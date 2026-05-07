/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.runner.examples;

import static io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType.end;
import static io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListenerEventType.start;
import static io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType.assigning;
import static io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType.completing;
import static io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType.creating;

import io.camunda.runner.Job;
import io.camunda.runner.LiveBpmn;
import io.camunda.runner.Run;
import io.camunda.runner.RunOptions;
import java.time.Duration;
import java.util.Map;

/**
 * Listener demo covering both kinds with one unified {@code .on(...)} surface:
 *
 * <ul>
 *   <li><b>Execution listeners</b> on the {@code greet} service task — fire on element activation /
 *       completion.
 *   <li><b>Task listeners</b> on the {@code review} user task — fire on user-task lifecycle events
 *       (creating, assigning, completing).
 * </ul>
 *
 * <p>The user task hangs waiting for human completion. Open Tasklist (<a
 * href="http://localhost:8080/tasklist">localhost:8080/tasklist</a>), claim the task, complete it,
 * and watch the {@code [tl-*]} listener output finish.
 */
public final class ListenerDemo {

  private ListenerDemo() {}

  public static void main(final String[] args) throws Exception {
    try (final var cluster = LiveBpmn.cluster().localhost()) {
      final Run run =
          LiveBpmn.createExecutableProcess("hello")
              .startEvent()

              // service task with execution listeners
              .serviceTask(
                  "greet",
                  (Job job) -> Map.of("greeting", "hi " + job.variable("name", String.class)))
              .listeners(
                  l ->
                      l.on(start, (Job job) -> System.out.println("[el-start] greet activating"))
                          .on(end, (Job job) -> System.out.println("[el-end]   greet completed")))

              // user task with task listeners — completes via Tasklist
              .userTask("review")
              .listeners(
                  l ->
                      l.on(
                              creating,
                              (Job job) -> System.out.println("[tl-creating]   review created"))
                          .on(
                              assigning,
                              (Job job) -> System.out.println("[tl-assigning]  review claimed"))
                          .on(
                              completing,
                              (Job job) -> System.out.println("[tl-completing] review completed")))
              .endEvent()
              .run(RunOptions.of(1).variables(i -> Map.of("name", "World")), cluster);

      System.out.println();
      System.out.println("──────────────────────────────────────────────────────────────────");
      System.out.println(" The user task 'review' is now waiting. To finish the run:");
      System.out.println("   1) open  http://localhost:8080/tasklist");
      System.out.println("   2) claim and complete the task");
      System.out.println("   3) watch [tl-assigning] / [tl-completing] fire below");
      System.out.println("──────────────────────────────────────────────────────────────────");
      System.out.println();

      run.await(Duration.ofMinutes(5));
      System.out.println("done; handled = " + run.workersHandled());
    }
  }
}
