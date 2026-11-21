plugins {
    id("java")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

group = "edu.hse.concurrency.links"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--enable-preview")
}

tasks.register<JavaExec>("threadProbe") {
    dependsOn(tasks.testClasses)
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "edu.hse.concurrency.links.LinkCheckProbe"
    jvmArgs("--enable-preview")
}
