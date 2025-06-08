package top.fifthlight.blazerod.model

data class Scene(
    val nodes: List<Node>,
    val initialTransform: NodeTransform? = null,
)