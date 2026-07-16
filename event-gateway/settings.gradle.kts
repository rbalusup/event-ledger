rootProject.name = "event-gateway"

// Included only so the end-to-end test can boot a real Account Service instance
// in-process; account-service remains fully independent (its own settings.gradle.kts,
// its own gradlew, buildable/runnable entirely on its own).
includeBuild("../account-service")
