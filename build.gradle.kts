plugins {
    id("org.springframework.boot") version "3.5.14" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
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
        resolutionStrategy.eachDependency {
            if (requested.group == "org.apache.tomcat.embed") {
                useVersion("10.1.55")
            }
            if (requested.group == "org.springframework.security") {
                useVersion("6.5.9")
            }
            if (requested.group == "org.springframework") {
                useVersion("6.2.11")
            }
            if (requested.group == "org.postgresql" && requested.name == "postgresql") {
                useVersion("42.7.11")
            }
            if (requested.group == "org.apache.kafka" && requested.name == "kafka-clients") {
                useVersion("3.9.2")
            }
            if (requested.group == "org.lz4" && requested.name == "lz4-java") {
                useVersion("1.8.1")
            }
            if (requested.group == "io.netty") {
                useVersion("4.1.133.Final")
            }
            if (requested.group == "commons-io" && requested.name == "commons-io") {
                useVersion("2.14.0")
            }
        }
    }

    dependencies {
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:3.5.14"))
        "testImplementation"(platform("org.springframework.boot:spring-boot-dependencies:3.5.14"))
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
