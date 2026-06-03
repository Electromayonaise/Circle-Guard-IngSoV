plugins {
    id("org.springframework.boot") version "3.5.14" apply false
    kotlin("jvm") version "1.9.24" apply false
    kotlin("plugin.spring") version "1.9.24" apply false
    kotlin("plugin.jpa") version "1.9.24" apply false
    id("org.sonarqube") version "4.4.1.3373"
}

allprojects {
    group = "com.circleguard"
    version = "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

sonar {
    properties {
        property("sonar.projectKey", "circleguard")
        property("sonar.projectName", "Circle Guard")
        property("sonar.gradle.skipCompile", "true")
        property("sonar.coverage.jacoco.xmlReportPaths", "**/build/reports/jacoco/test/jacocoTestReport.xml")
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    configurations.all {
        resolutionStrategy {
            // force() is evaluated before any eachDependency action, including those registered
            // by the io.spring.dependency-management plugin (which re-applies BOM versions via
            // its own eachDependency and would silently override useVersion() calls in ours).
            force(
                // Tomcat — CVE-2026-41293, CVE-2026-43512, CVE-2026-43515 (CRITICAL)
                "org.apache.tomcat.embed:tomcat-embed-core:10.1.55",
                "org.apache.tomcat.embed:tomcat-embed-websocket:10.1.55",
                "org.apache.tomcat.embed:tomcat-embed-el:10.1.55",
                // PostgreSQL — CVE-2026-42198 (HIGH)
                "org.postgresql:postgresql:42.7.11",
                // Kafka — HIGH
                "org.apache.kafka:kafka-clients:3.9.2",
                // lz4-java — HIGH
                "org.lz4:lz4-java:1.8.1",
                // commons-io
                "commons-io:commons-io:2.14.0",
                // Netty — CVE-2026-42583, CVE-2026-42579, CVE-2026-42584, CVE-2026-42587 (HIGH)
                "io.netty:netty-codec:4.1.133.Final",
                "io.netty:netty-codec-dns:4.1.133.Final",
                "io.netty:netty-codec-http:4.1.133.Final",
                "io.netty:netty-codec-http2:4.1.133.Final",
                "io.netty:netty-buffer:4.1.133.Final",
                "io.netty:netty-common:4.1.133.Final",
                "io.netty:netty-handler:4.1.133.Final",
                "io.netty:netty-resolver:4.1.133.Final",
                "io.netty:netty-resolver-dns:4.1.133.Final",
                "io.netty:netty-transport:4.1.133.Final",
                "io.netty:netty-transport-native-epoll:4.1.133.Final",
                "io.netty:netty-transport-native-unix-common:4.1.133.Final"
            )
            // eachDependency for group-wide overrides where listing every artifact is impractical.
            // These are supplementary — force() above already covers the CVE-affected artifacts.
            eachDependency {
                if (requested.group == "org.springframework.security") {
                    useVersion("6.5.9")
                }
                if (requested.group == "org.springframework") {
                    useVersion("6.2.11")
                }
            }
        }
    }

    dependencies {
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:3.5.14"))
        "testImplementation"(platform("org.springframework.boot:spring-boot-dependencies:3.5.14"))
        "annotationProcessor"(platform("org.springframework.boot:spring-boot-dependencies:3.5.14"))
        "testAnnotationProcessor"(platform("org.springframework.boot:spring-boot-dependencies:3.5.14"))
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")
        "testCompileOnly"("org.projectlombok:lombok")
        "testAnnotationProcessor"("org.projectlombok:lombok")
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("com.h2database:h2")
    }

    apply(plugin = "jacoco")

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "21"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform {
            if (project.hasProperty("excludeIntegration")) {
                excludeTags("integration")
            }
        }
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.withType<JacocoReport>().configureEach {
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }

    tasks.withType<JacocoCoverageVerification>().configureEach {
        dependsOn(tasks.withType<JacocoReport>())
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    minimum = "0.60".toBigDecimal()
                }
            }
        }
    }

    tasks.named("check") {
        dependsOn(tasks.withType<JacocoCoverageVerification>())
    }
}
