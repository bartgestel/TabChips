import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

// 2026.x platform runs on Java 21; foojay downloads it if missing.
kotlin { jvmToolchain(21) }

dependencies {
    testImplementation("junit:junit:4.13.2")
    intellijPlatform {
        phpstorm("2026.2.3")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion { sinceBuild = "262" } // untilBuild left unset on purpose
    }
    pluginVerification {
        ides { recommended() }
    }
    // Secrets come from environment variables only, never from files in this repo. See README "Publishing".
    // Without them, signPlugin is skipped and publishPlugin fails, so local builds keep working.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}
