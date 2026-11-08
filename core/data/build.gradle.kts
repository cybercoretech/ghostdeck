plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}
kotlin { jvmToolchain(17) }
dependencies {
    api(project(":core:model"))
    testImplementation("junit:junit:4.13.2")
}
