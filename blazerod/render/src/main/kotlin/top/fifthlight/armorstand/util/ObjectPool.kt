package top.fifthlight.armorstand.util

import net.minecraft.util.Identifier
import top.fifthlight.armorstand.debug.ObjectPoolTracker
import java.util.ArrayDeque
import java.util.Collections

private val pools = Collections.synchronizedSet(mutableSetOf<Pool<*>>())
private var cleaned = false
fun cleanupPools() {
    require(!cleaned) { "All pools has been cleaned" }
    cleaned = true
    pools.forEach { it.close() }
    pools.clear()
}

interface Pool<T> : AutoCloseable {
    fun acquire(): T
    fun release(obj: T)
}

fun <T : AutoCloseable> ObjectPool(
    identifier: Identifier,
    create: () -> T,
    onAcquired: (T.() -> Unit)? = null,
    onReleased: (T.() -> Unit)? = null,
) = ObjectPool<T>(
    identifier = identifier,
    create = create,
    onAcquired = onAcquired,
    onReleased = onReleased,
    onClosed = { close() },
)

open class ObjectPool<T : Any>(
    protected val identifier: Identifier,
    protected val create: () -> T,
    protected val onAcquired: (T.() -> Unit)? = null,
    protected val onReleased: (T.() -> Unit)? = null,
    protected val onClosed: (T.() -> Unit)?,
) : Pool<T> {
    protected val closed: Boolean = false
    protected val pool = ArrayDeque<T>()

    override fun acquire(): T {
        require(!closed) { "Pool is closed" }
        return if (pool.isEmpty()) {
            create().also {
                ObjectPoolTracker.instance?.compute(identifier) {
                    copy(allocatedItem = allocatedItem + 1)
                }
            }
        } else {
            ObjectPoolTracker.instance?.compute(identifier) {
                copy(
                    allocatedItem = allocatedItem + 1,
                    pooledItem = pooledItem - 1,
                )
            }
            pool.removeFirst()
        }.also { obj ->
            try {
                onAcquired?.invoke(obj)
            } catch (ex: Throwable) {
                ObjectPoolTracker.instance?.compute(identifier) {
                    copy(
                        allocatedItem = allocatedItem - 1,
                        failedItem = failedItem + 1,
                    )
                }
                throw ex
            }
        }
    }

    override fun release(obj: T) {
        require(!closed) { "Pool is closed" }
        try {
            onReleased?.invoke(obj)
        } catch (ex: Throwable) {
            ObjectPoolTracker.instance?.compute(identifier) {
                copy(
                    allocatedItem = allocatedItem - 1,
                    failedItem = failedItem + 1,
                )
            }
            throw ex
        }
        ObjectPoolTracker.instance?.compute(identifier) {
            copy(
                allocatedItem = allocatedItem - 1,
                pooledItem = pooledItem + 1,
            )
        }
        pool.addLast(obj)
    }

    override fun close() {
        if (closed) {
            return
        }
        ObjectPoolTracker.instance?.compute(identifier) { ObjectPoolTracker.Item() }
        pool.forEach { onClosed?.invoke(it) }
        pool.clear()
    }
}

class ThreadSafeObjectPool<T>(
    val inner: Pool<T>,
) : Pool<T> {
    override fun acquire(): T = synchronized(this) {
        inner.acquire()
    }

    override fun release(obj: T) = synchronized(this) {
        inner.release(obj)
    }

    override fun close() = inner.close()
}

fun <T : AutoCloseable> FramedObjectPool(
    identifier: Identifier,
    create: () -> T,
    onAcquired: (T.() -> Unit)? = null,
    onReleased: (T.() -> Unit)? = null,
) = FramedObjectPool(
    identifier = identifier,
    create = create,
    onAcquired = onAcquired,
    onReleased = onReleased,
    onClosed = { close() },
)

class FramedObjectPool<T : Any>(
    identifier: Identifier,
    create: () -> T,
    onAcquired: (T.() -> Unit)? = null,
    onReleased: (T.() -> Unit)? = null,
    onClosed: (T.() -> Unit)? = null,
) : ObjectPool<T>(identifier, create, onAcquired, onReleased, onClosed) {
    private val pendingRelease = ArrayDeque<T>()

    override fun release(obj: T) {
        require(!closed) { "Pool is closed" }
        ObjectPoolTracker.instance?.compute(identifier) {
            copy(
                allocatedItem = allocatedItem - 1,
                pooledItem = pooledItem + 1,
            )
        }
        onReleased?.invoke(obj)
        pendingRelease.addLast(obj)
    }

    fun frame() {
        require(!closed) { "Pool is closed" }
        if (pendingRelease.isNotEmpty()) {
            pool.addAll(pendingRelease)
            pendingRelease.clear()
        }
    }

    override fun close() {
        if (closed) {
            return
        }
        pendingRelease.forEach { onClosed?.invoke(it) }
        pendingRelease.clear()
        super.close()
    }

    init {
        POOLS.add(this)
    }

    companion object {
        private val POOLS = mutableListOf<FramedObjectPool<*>>()

        fun frame() {
            POOLS.forEach { it.frame() }
        }
    }
}
