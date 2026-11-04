plugins { `java-library` }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
val coreCheck by tasks.registering(JavaExec::class) {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.shubhraghav.lensqivo.core.CoreTests")
}
tasks.check { dependsOn(coreCheck) }
tasks.test {
    // Core behavior uses the standalone assertion runner rather than JUnit discovery.
    dependsOn(coreCheck)
    enabled = false
}
