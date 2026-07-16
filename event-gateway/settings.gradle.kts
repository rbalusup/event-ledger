rootProject.name = "event-gateway"

// Included only so the end-to-end test can boot a real Account Service instance
// in-process; account-service remains fully independent (its own settings.gradle.kts,
// its own gradlew, buildable/runnable entirely on its own). Guarded on the sibling
// directory existing so a Docker build (whose context is just this directory, with
// no account-service alongside it) can still run ./gradlew bootJar without it.
if (File(rootDir, "../account-service").exists()) {
    includeBuild("../account-service")
}
