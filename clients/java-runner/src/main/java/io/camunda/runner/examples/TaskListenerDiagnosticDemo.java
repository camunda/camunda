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
 * <p>Builds the listener flow, fires one instance, then in parallel polls the SDK's <em>search</em>
 * APIs every second to print:
 *
 * <ul>
 *   <li>Every job in the system for our run id — its kind, type, state, retries, worker name.
 *   <li>Every user task — its state, assignee, candidate users/groups.
 * </ul>
 *
 * <p>Run it, watch the output. The interesting question: when the user task hits {@code assigning},
 * is there a {@code TASK_LISTENER} job with state {@code CREATED} matching our prefixed jobType? If
 * yes — the broker did emit it and our worker should activate it. If our worker never receives it,
 * the issue is on the activation/streaming path.
 */
public final class TaskListenerDiagnosticDemo {

  private TaskListenerDiagnosticDemo() {}

  public static void main(final String[] args) throws Exception {
    final AtomicBoolean stopPolling = new AtomicBoolean();

    try (final var cluster = LiveBpmn.cluster().localhost()) {
      final CamundaClient client = cluster.client();

      // Spin up a background poller BEFORE running, so it sees every state transition.
      final Thread poller = new Thread(() -> pollLoop(client, stopPolling), "diag-poller");
      poller.setDaemon(true);
      poller.start();

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

      System.out.println();
      System.out.println("══════════════════════════════════════════════════════════════════");
      System.out.println(" Diagnostic poller running — printing all jobs and user tasks");
      System.out.println(" Open Tasklist (http://localhost:8080/tasklist) to complete the");
      System.out.println(" user task. Watch the [diag] lines below to see what the broker");
      System.out.println(" actually has.");
      System.out.println("══════════════════════════════════════════════════════════════════");
      System.out.println();

      try {
        run.await(Duration.ofMinutes(3));
      } finally {
        stopPolling.set(true);
        poller.join(2000);
      }
      System.out.println("done; handled = " + run.workersHandled());
    }
  }

  /** Polls the SDK's job + user-task search APIs every second until {@code stop} flips true. */
  private static void pollLoop(final CamundaClient client, final AtomicBoolean stop) {
    while (!stop.get()) {
      try {
        snapshot(client);
      } catch (final Exception e) {
        System.out.println("[diag] poll error: " + e.getMessage());
      }
      try {
        Thread.sleep(1500);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private static void snapshot(final CamundaClient client) {
    final SearchResponse<Job> jobs = client.newJobSearchRequest().send().join();
    final SearchResponse<UserTask> tasks = client.newUserTaskSearchRequest().send().join();

    System.out.println("[diag] -------- snapshot --------");
    System.out.printf("[diag] jobs: %d total%n", jobs.items().size());
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
    System.out.printf("[diag] user tasks: %d total%n", tasks.items().size());
    for (final UserTask ut : tasks.items()) {
      System.out.printf(
          "[diag]   userTask key=%d state=%s assignee=%s candidateUsers=%s candidateGroups=%s%n",
          ut.getUserTaskKey(),
          ut.getState(),
          ut.getAssignee(),
          ut.getCandidateUsers(),
          ut.getCandidateGroups());
    }
  }
}
