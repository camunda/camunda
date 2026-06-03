abstract class SerialTestService : BuildService<BuildServiceParameters.None>

val serialTestService =
    gradle.sharedServices.registerIfAbsent("serialTests", SerialTestService::class) {
        maxParallelUsages = 1
    }

tasks.withType<Test>().configureEach { usesService(serialTestService) }
