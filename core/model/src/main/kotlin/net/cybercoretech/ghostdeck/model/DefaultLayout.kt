package net.cybercoretech.ghostdeck.model

private const val DEFAULT_DOCK_SLOTS = 5

/** First-run layout: the first five of [dockApps] in the dock, an empty first page. */
fun defaultLayout(dockApps: List<AppKey>): Layout = Layout(
    dock = Dock(List(DEFAULT_DOCK_SLOTS) { i -> dockApps.getOrNull(i)?.let { AppItem(ItemIds.new(), it) } }),
)
