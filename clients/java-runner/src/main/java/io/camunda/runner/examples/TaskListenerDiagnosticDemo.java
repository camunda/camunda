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

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.Job;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.runner.LiveBpmn;
import io.camunda.runner.Run;
import io.camunda.runner.RunOptions;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Diagnostic demo for understanding why task-listener jobs don't reach the worker.
 *
 * <p>After deploying and creating one instance, polls the SDK's <em>search</em> APIs every 1.5 s —
 * but filtered to <em>this run's</em> {@code processDefinitionKey} and {@code processInstanceKey}
 * so we don't trip over polluted entries left in the index by other runs or other projects.
 *
 * <p>What to look for: when the user task hits {@code ASSIGNING}, is there a {@code TASK_LISTENER}
 * job in {@code CREATED} state with the prefixed jobType matching what we registered for? If yes —
 * the broker emitted it and the activation path is the gap. If no — the listener-job creation step
 * itself isn't happening for our run.
 */
public final class TaskListenerDiagnosticDemo {

  private TaskListenerDiagnosticDemo() {}

  public static void main(final String[] args) throws Exception {
    final AtomicBoolean stopPolling = new AtomicBoolean();

    try (final var cluster = LiveBpmn.cluster().localhost()) {
      final CamundaClient client = cluster.client();

      final Run run =
          LiveBpmn.createExecutableProcess("hello")
              .startEvent()
              .serviceTask(
                  "greet",
                  (io.camunda.runner.Job job) ->
                      Map.of("greeting", "hi " + job.variable("name", String.class)))
              .listeners(
                  l ->
                      l.on(
                              start,
                              (io.camunda.runner.Job job) ->
                                  System.out.println("[el-start] greet activating"))
                          .on(
                              end,
                              (io.camunda.runner.Job job) ->
                                  System.out.println("[el-end]   greet completed")))
              .userTask(
                  "review",
                  (io.camunda.zeebe.model.bpmn.builder.UserTaskBuilder t) ->
                      t.zeebeAssignee("demo"))
              .listeners(
                  l ->
                      l.on(
                              assigning,
                              (io.camunda.runner.Job job) ->
                                  System.out.println("[tl-assigning]  review claimed"))
                          .on(
                              completing,
                              (io.camunda.runner.Job job) ->
                                  System.out.println("[tl-completing] review completed")))
              .endEvent()
              .run(RunOptions.of(1).variables(i -> Map.of("name", "World")), cluster);

      // Capture the keys to filter on AFTER instance creation.
      final long pdk = run.processDefinitionKey();
      final long pik = run.instances().get(0);

      System.out.println();
      System.out.println("══════════════════════════════════════════════════════════════════");
      System.out.printf("  diagnostic poller — filtered to processInstanceKey=%d%n", pik);
      System.out.printf("                                processDefinitionKey=%d%n", pdk);
      System.out.println("  Open  http://localhost:8080/tasklist  and complete 'review'.");
      System.out.println("══════════════════════════════════════════════════════════════════");
      System.out.println();

      final Thread poller =
          new Thread(() -> pollLoop(client, pdk, pik, stopPolling), "diag-poller");
      poller.setDaemon(true);
      poller.start();

      try {
        run.await(Duration.ofMinutes(3));
      } finally {
        stopPolling.set(true);
        poller.join(2000);
      }
      System.out.println("done; handled = " + run.workersHandled());
    }
  }

  private static void pollLoop(
      final CamundaClient client, final long pdk, final long pik, final AtomicBoolean stop) {
    while (!stop.get()) {
      try {
        snapshot(client, pdk, pik);
      } catch (final Exception e) {
        // Print only the first frame so the corrupt-row NPE doesn't drown the output.
        final var first = e.getStackTrace().length > 0 ? e.getStackTrace()[0].toString() : "";
        System.out.printf(
            "[diag] poll error (%s): %s @ %s%n",
            e.getClass().getSimpleName(), e.getMessage(), first);
      }
      try {
        Thread.sleep(1500);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private static void snapshot(final CamundaClient client, final long pdk, final long pik) {
    System.out.println("[diag] -------- snapshot --------");

    // Filter jobs to ONLY this run — avoids tripping over polluted entries from other projects.
    try {
      final SearchResponse<Job> jobs =
          client.newJobSearchRequest().filter(f -> f.processDefinitionKey(pdk)).send().join();
      System.out.printf("[diag] jobs (processDefinitionKey=%d): %d%n", pdk, jobs.items().size());
      for (final Job j : jobs.items()) {
        System.out.printf(
            "[diag]   job key=%d kind=%s state=%s type=%s retries=%d worker=%s eventType=%s%n",
            j.getJobKey(),
            j.getKind(),
            j.getState(),
            j.getType(),
            j.getRetries() == null ? -1 : j.getRetries(),
            j.getWorker(),
            j.getListenerEventType());
      }
    } catch (final Exception e) {
      System.out.printf(
          "[diag] job search failed: %s — %s%n", e.getClass().getSimpleName(), e.getMessage());
    }

    try {
      final SearchResponse<UserTask> tasks =
          client.newUserTaskSearchRequest().filter(f -> f.processInstanceKey(pik)).send().join();
      System.out.printf(
          "[diag] user tasks (processInstanceKey=%d): %d%n", pik, tasks.items().size());
      for (final UserTask ut : tasks.items()) {
        System.out.printf(
            "[diag]   userTask key=%d state=%s assignee=%s candidateUsers=%s candidateGroups=%s%n",
            ut.getUserTaskKey(),
            ut.getState(),
            ut.getAssignee(),
            ut.getCandidateUsers(),
            ut.getCandidateGroups());
      }
    } catch (final Exception e) {
      System.out.printf(
          "[diag] user-task search failed: %s — %s%n",
          e.getClass().getSimpleName(), e.getMessage());
    }
  }
}
