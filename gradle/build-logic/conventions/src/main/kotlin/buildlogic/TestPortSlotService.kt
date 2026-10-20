package buildlogic

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.OperationCompletionListener
import org.gradle.tooling.events.task.TaskFinishEvent

/**
 * Leases a `testForkNumber` to each running test task, mirroring the fork numbers Maven Surefire
 * hands to its forks.
 *
 * `SocketUtil` derives a bounded port range from the fork number. Gradle worker IDs cannot be used
 * for that: they are global and grow across all test tasks of a build, so they quickly exceed the
 * range. The service is shared by the whole build, so concurrently running test tasks always hold
 * distinct slots, and a slot is only reused after its task has finished.
 *
 * All workers of one task share its slot, so this assumes a single fork per test task.
 */
abstract class TestPortSlotService :
  BuildService<BuildServiceParameters.None>, OperationCompletionListener {
  private val freeSlots = LinkedBlockingQueue((0 until MAX_SLOTS).toList())
  private val leases = ConcurrentHashMap<String, Int>()

  /** Returns the slot of the given task, blocking until one is free. */
  fun acquire(taskPath: String): Int {
    leases[taskPath]?.let {
      return it
    }
    val slot = freeSlots.take()
    leases[taskPath] = slot
    return slot
  }

  override fun onFinish(event: FinishEvent) {
    if (event is TaskFinishEvent) {
      leases.remove(event.descriptor.taskPath)?.let(freeSlots::put)
    }
  }

  companion object {
    /** Must stay in sync with `MAX_TEST_FORKS` in `SocketUtil`. */
    const val MAX_SLOTS = 30
  }
}

/** Registers the build-wide [TestPortSlotService] and the listener that frees finished slots. */
fun Project.registerTestPortSlotService(): Provider<TestPortSlotService> {
  val service =
    gradle.sharedServices.registerIfAbsent("testPortSlots", TestPortSlotService::class.java) {
      maxParallelUsages.set(TestPortSlotService.MAX_SLOTS)
    }
  serviceOf<org.gradle.build.event.BuildEventsListenerRegistry>().onTaskCompletion(service)
  return service
}
