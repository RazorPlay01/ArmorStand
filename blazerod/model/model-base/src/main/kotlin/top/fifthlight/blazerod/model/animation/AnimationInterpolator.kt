package top.fifthlight.blazerod.model.animation

import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.pow
import kotlin.math.sin

enum class AnimationInterpolation(val elements: Int) {
    LINEAR(1),           // Linear interpolation
    STEP(1),             // Constant (no interpolation, holds start value)
    CUBIC_SPLINE(3),     // Hermite spline (approximates Blender's Bézier)
    BEZIER(3),           // Explicit Bézier interpolation
    BACK(2),             // Overshoots the endpoint
    BOUNCE(2),           // Simulates bouncing effect
    ELASTIC(2),          // Simulates elastic oscillation
    QUADRATIC(2),        // Quadratic interpolation
    CATMULL_ROM(4),      // Catmull-Rom spline
}

interface AnimationInterpolator<T> {
    fun set(value: List<T>, result: T)

    fun interpolate(
        delta: Float,
        type: AnimationInterpolation,
        startValue: List<T>,
        endValue: List<T>,
        result: T,
    )
}

object Vector3AnimationInterpolator : AnimationInterpolator<Vector3f> {
    override fun set(value: List<Vector3f>, result: Vector3f) {
        require(value.isNotEmpty()) { "startValue must contain at least one element" }
        result.set(value[0])
    }

    override fun interpolate(
        delta: Float,
        type: AnimationInterpolation,
        startValue: List<Vector3f>,
        endValue: List<Vector3f>,
        result: Vector3f,
    ) {
        require(startValue.size >= type.elements && endValue.size >= type.elements) {
            "Insufficient control points for ${type.name}: requires ${type.elements} elements"
        }

        when (type) {
            AnimationInterpolation.LINEAR -> result.set(startValue[0]).lerp(endValue[0], delta)
            AnimationInterpolation.STEP -> result.set(startValue[0])
            AnimationInterpolation.CUBIC_SPLINE -> {
                val t = delta
                val t2 = t * t
                val t3 = t2 * t

                // Hermite spline formula
                val h1 = 2f * t3 - 3f * t2 + 1f
                val h2 = t3 - 2f * t2 + t
                val h3 = -2f * t3 + 3f * t2
                val h4 = t3 - t2

                result.set(
                    startValue[1].mul(h1)
                        .add(startValue[2].mul(h2))
                        .add(endValue[1].mul(h3))
                        .add(endValue[0].mul(h4))
                )
            }
            AnimationInterpolation.BEZIER -> {
                val t = delta
                val t2 = t * t
                val t3 = t2 * t
                val oneMinusT = 1f - t
                val oneMinusT2 = oneMinusT * oneMinusT
                val oneMinusT3 = oneMinusT2 * oneMinusT

                // Cubic Bézier formula: B(t) = (1-t)^3*P0 + 3(1-t)^2*t*P1 + 3(1-t)*t^2*P2 + t^3*P3
                val w0 = oneMinusT3
                val w1 = 3f * oneMinusT2 * t
                val w2 = 3f * oneMinusT * t2
                val w3 = t3

                result.set(
                    startValue[0].mul(w0)
                        .add(startValue[1].mul(w1))
                        .add(endValue[0].mul(w2))
                        .add(endValue[1].mul(w3))
                )
            }
            AnimationInterpolation.BACK -> {
                val t = delta
                val s = 1.70158f // Overshoot factor, standard for Blender's Back effect
                val overshoot = t * t * ((s + 1f) * t - s)

                result.set(startValue[0]).lerp(endValue[0], overshoot)
            }
            AnimationInterpolation.BOUNCE -> {
                val t = delta
                // Bounce effect: piecewise function to simulate bounces
                val bounce = when {
                    t < 1f / 2.75f -> 7.5625f * t * t
                    t < 2f / 2.75f -> {
                        val t2 = t - 1.5f / 2.75f
                        7.5625f * t2 * t2 + 0.75f
                    }
                    t < 2.5f / 2.75f -> {
                        val t2 = t - 2.25f / 2.75f
                        7.5625f * t2 * t2 + 0.9375f
                    }
                    else -> {
                        val t2 = t - 2.625f / 2.75f
                        7.5625f * t2 * t2 + 0.984375f
                    }
                }

                result.set(startValue[0]).lerp(endValue[0], bounce)
            }
            AnimationInterpolation.ELASTIC -> {
                val t = delta
                val amplitude = 1f
                val period = 0.3f // Period for oscillation
                val s = period / 4f
                val tAdjusted = t - 1f
                val elastic = amplitude * 2f.pow(-10f * t) * sin((tAdjusted - s) * (2f * Math.PI.toFloat()) / period) + 1f

                result.set(startValue[0]).lerp(endValue[0], elastic)
            }
            AnimationInterpolation.QUADRATIC -> {
                val t = delta
                val t2 = t * t

                // Quadratic interpolation: (1-t)^2*P0 + 2(1-t)*t*P1 + t^2*P2
                val w0 = (1f - t) * (1f - t)
                val w1 = 2f * (1f - t) * t
                val w2 = t2

                result.set(
                    startValue[0].mul(w0)
                        .add(startValue[1].mul(w1))
                        .add(endValue[0].mul(w2))
                )
            }
            AnimationInterpolation.CATMULL_ROM -> {
                val t = delta
                val t2 = t * t
                val t3 = t2 * t

                // Catmull-Rom spline weights
                val w0 = -0.5f * t3 + t2 - 0.5f * t
                val w1 = 1.5f * t3 - 2.5f * t2 + 1f
                val w2 = -1.5f * t3 + 2f * t2 + 0.5f * t
                val w3 = 0.5f * t3 - 0.5f * t2

                result.set(
                    startValue[0].mul(w0)
                        .add(startValue[1].mul(w1))
                        .add(endValue[0].mul(w2))
                        .add(endValue[1].mul(w3))
                )
            }
        }
    }
}

object QuaternionAnimationInterpolator : AnimationInterpolator<Quaternionf> {
    override fun set(value: List<Quaternionf>, result: Quaternionf) {
        require(value.isNotEmpty()) { "startValue must contain at least one element" }
        result.set(value[0])
    }

    override fun interpolate(
        delta: Float,
        type: AnimationInterpolation,
        startValue: List<Quaternionf>,
        endValue: List<Quaternionf>,
        result: Quaternionf,
    ) {
        require(startValue.size >= type.elements && endValue.size >= type.elements) {
            "Insufficient control points for ${type.name}: requires ${type.elements} elements"
        }

        when (type) {
            AnimationInterpolation.LINEAR -> result.set(startValue[0]).slerp(endValue[0], delta)
            AnimationInterpolation.STEP -> result.set(startValue[0])
            AnimationInterpolation.CUBIC_SPLINE -> {
                val t = delta
                val t2 = t * t
                val t3 = t2 * t

                // Hermite spline formula
                val h1 = 2f * t3 - 3f * t2 + 1f
                val h2 = t3 - 2f * t2 + t
                val h3 = -2f * t3 + 3f * t2
                val h4 = t3 - t2

                result.set(
                    startValue[1].mul(h1)
                        .add(startValue[2].mul(h2))
                        .add(endValue[1].mul(h3))
                        .add(endValue[0].mul(h4))
                )
            }
            AnimationInterpolation.BEZIER -> {
                val t = delta
                // Bézier interpolation for quaternions using slerp
                val temp1 = Quaternionf().set(startValue[0]).slerp(startValue[1], t)
                val temp2 = Quaternionf().set(startValue[1]).slerp(endValue[0], t)
                val temp3 = Quaternionf().set(endValue[0]).slerp(endValue[1], t)

                // Interpolate between intermediate points
                val intermediate1 = Quaternionf().set(temp1).slerp(temp2, t)
                val intermediate2 = Quaternionf().set(temp2).slerp(temp3, t)

                result.set(intermediate1).slerp(intermediate2, t)
            }
            AnimationInterpolation.BACK -> {
                val t = delta
                val s = 1.70158f // Overshoot factor
                val overshoot = t * t * ((s + 1f) * t - s)

                result.set(startValue[0]).slerp(endValue[0], overshoot)
            }
            AnimationInterpolation.BOUNCE -> {
                val t = delta
                // Bounce effect: piecewise function
                val bounce = when {
                    t < 1f / 2.75f -> 7.5625f * t * t
                    t < 2f / 2.75f -> {
                        val t2 = t - 1.5f / 2.75f
                        7.5625f * t2 * t2 + 0.75f
                    }
                    t < 2.5f / 2.75f -> {
                        val t2 = t - 2.25f / 2.75f
                        7.5625f * t2 * t2 + 0.9375f
                    }
                    else -> {
                        val t2 = t - 2.625f / 2.75f
                        7.5625f * t2 * t2 + 0.984375f
                    }
                }

                result.set(startValue[0]).slerp(endValue[0], bounce)
            }
            AnimationInterpolation.ELASTIC -> {
                val t = delta
                val amplitude = 1f
                val period = 0.3f
                val s = period / 4f
                val tAdjusted = t - 1f
                val elastic = amplitude * 2f.pow(-10f * t) * sin((tAdjusted - s) * (2f * Math.PI.toFloat()) / period) + 1f

                result.set(startValue[0]).slerp(endValue[0], elastic)
            }
            AnimationInterpolation.QUADRATIC -> {
                val t = delta
                // Quadratic interpolation for quaternions using slerp
                val temp1 = Quaternionf().set(startValue[0]).slerp(startValue[1], 2f * t / (1f + t))
                val temp2 = Quaternionf().set(startValue[1]).slerp(endValue[0], t)

                result.set(temp1).slerp(temp2, t)
            }
            AnimationInterpolation.CATMULL_ROM -> {
                val t = delta
                // Catmull-Rom for quaternions using slerp
                val temp1 = Quaternionf().set(startValue[0]).slerp(startValue[1], t)
                val temp2 = Quaternionf().set(startValue[1]).slerp(endValue[0], t)
                val temp3 = Quaternionf().set(endValue[0]).slerp(endValue[1], t)

                // Interpolate between intermediate points
                val intermediate1 = Quaternionf().set(temp1).slerp(temp2, t)
                val intermediate2 = Quaternionf().set(temp2).slerp(temp3, t)

                result.set(intermediate1).slerp(intermediate2, t)
            }
        }
    }
}
}