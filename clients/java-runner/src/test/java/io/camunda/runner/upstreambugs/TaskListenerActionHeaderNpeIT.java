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
package io.camunda.runner.upstreambugs;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Reproducer for <b>Bug 1</b> in {@code clients/java-runner/UPSTREAM_BUGS.md} — {@code
 * ResponseMapper.toUserTaskProperties} NPE on missing action header.
 *
 * <p><b>Scenario</b>: a BPMN user task is statically pre-assigned via {@code
 * <zeebe:assignmentDefinition assignee="..." />}. An {@code assigning} task listener is attached.
 * The broker creates the task, applies the static assignee, triggers the {@code ASSIGNING}
 * transition, and emits a {@code TASK_LISTENER} job. <em>The broker does not set the {@code action}
 * header on this job</em>, because the broker-internal {@code BpmnUserTaskBehavior
 * .userTaskAssigning} path doesn't call {@code setAction(...)} (only the explicit-command path via
 * {@code UserTaskAssignProcessor.onCommand} does).
 *
 * <p>The gateway response mapper at {@code
 * gateways/gateway-mapping-http/.../ResponseMapper.java:251} requires the action header non-null:
 *
 * <pre>
 *   props.setAction(requireNonNull(headers.get(USER_TASK_ACTION_HEADER_NAME), "action"));
 * </pre>
 *
 * Result: NPE in {@code RoundRobinActivateJobsHandler}, the response batch never reaches the
 * worker, the listener job sits in {@code CREATED} forever, the user task hangs in {@code
 * ASSIGNING}.
 *
 * <p><b>Status</b>: {@link Disabled} until the upstream fix lands. Flip the annotation off and run
 * against any Camunda 8.10-SNAPSHOT broker with the proposed engine fix applied — the test should
 * pass.
 *
 * <p><b>Pre-requisites to run:</b> a Camunda 8.x broker on {@code localhost:26500} (gRPC) and
 * {@code localhost:8080} (REST), with secondary storage available (Operate / Tasklist optional).
 */
@Tag("upstream-bug-reproducer")
@Disabled("Reproduces upstream Bug 1 — NPE in gateway ResponseMapper. Enable to verify the fix.")
final class TaskListenerActionHeaderNpeIT {

  @Test
  void shouldActivateAssigningTaskListenerJobWhenUserTaskHasStaticAssignee() {
    // unique IDs per run so reruns don't collide
    final String runId = "bug1-" + UUID.randomUUID().toString().substring(0, 8);
    final String processId = runId + "-process";
    final String listenerJobType = runId + "-assigning-listener";

    try (CamundaClient client = newClient()) {

      // given — a user task with static pre-assignment AND an assigning task listener
      final var model =
          Bpmn.createExecutableProcess(processId)
              .startEvent()
              .userTask(
                  "review",
                  t ->
                      t.zeebeUserTask()
                          .zeebeAssignee("demo")
                          .zeebeTaskListener(
                              b ->
                                  b.eventType(ZeebeTaskListenerEventType.assigning)
                                      .type(listenerJobType)))
              .endEvent()
              .done();

      client.newDeployResourceCommand().addProcessModel(model, processId + ".bpmn").send().join();

      final AtomicReference<String> seenJobType = new AtomicReference<>();

      // when — register a worker for the listener job type and create one instance
      try (var worker =
          client
              .newWorker()
              .jobType(listenerJobType)
              .handler(
                  (jobClient, job) -> {
                    seenJobType.set(job.getType());
                    jobClient.newCompleteCommand(job).send().join();
                  })
              .open()) {

        client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();

        // then — the assigning listener should fire within a reasonable window
        Awaitility.await("assigning task listener fires")
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofMillis(500))
            .untilAsserted(() -> assertThat(seenJobType).hasValue(listenerJobType));
      }
    }
  }

  private static CamundaClient newClient() {
    return CamundaClient.newClientBuilder()
        .grpcAddress(URI.create("http://localhost:26500"))
        .restAddress(URI.create("http://localhost:8080"))
        .usePlaintext()
        .build();
  }
}
