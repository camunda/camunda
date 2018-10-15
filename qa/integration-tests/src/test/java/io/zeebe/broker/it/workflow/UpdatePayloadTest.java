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
package io.zeebe.broker.it.workflow;

import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertWorkflowInstanceCompleted;
import static io.zeebe.broker.it.util.ZeebeAssertHelper.assertWorkflowInstancePayloadUpdated;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.it.GrpcClientRule;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.client.api.events.DeploymentEvent;
import io.zeebe.client.cmd.ClientException;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.Collections;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class UpdatePayloadTest {
  private static final String PAYLOAD = "{\"foo\":\"bar\"}";

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("process")
          .startEvent("start")
          .serviceTask("task-1", t -> t.zeebeTaskType("task-1").zeebeOutput("$.result", "$.result"))
          .endEvent("end")
          .done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public GrpcClientRule clientRule = new GrpcClientRule(brokerRule);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(clientRule);

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Before
  public void init() {
    final DeploymentEvent deploymentEvent =
        clientRule
            .getWorkflowClient()
            .newDeployCommand()
            .addWorkflowModel(WORKFLOW, "workflow.bpmn")
            .send()
            .join();

    clientRule.waitUntilDeploymentIsDone(deploymentEvent.getKey());

    clientRule
        .getWorkflowClient()
        .newCreateInstanceCommand()
        .bpmnProcessId("process")
        .latestVersion()
        .send()
        .join();
  }

  @Test
  public void shouldUpdatePayloadWhenActivityIsActivated() {
    // given
    final long workflowInstanceKey =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withActivityId("task-1")
            .findFirst()
            .get()
            .getValue()
            .getWorkflowInstanceKey();

    // when
    clientRule
        .getWorkflowClient()
        .newUpdatePayloadCommand(workflowInstanceKey)
        .payload(PAYLOAD)
        .send()
        .join();

    // then
    assertWorkflowInstancePayloadUpdated(
        (payloadUpdatedEvent) -> {
          assertThat(payloadUpdatedEvent.getPayload()).isEqualTo(PAYLOAD);
          assertThat(payloadUpdatedEvent.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
        });
  }

  @Test
  public void shouldUpdateWithNullPayload() {
    // given
    final long workflowInstanceKey =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withActivityId("task-1")
            .findFirst()
            .get()
            .getValue()
            .getWorkflowInstanceKey();

    // when
    clientRule
        .getWorkflowClient()
        .newUpdatePayloadCommand(workflowInstanceKey)
        .payload("null")
        .send()
        .join();

    // then
    assertWorkflowInstancePayloadUpdated(
        (payloadUpdatedEvent) -> {
          assertThat(payloadUpdatedEvent.getPayload()).isEqualTo("{}");
          assertThat(payloadUpdatedEvent.getPayloadAsMap()).isEmpty();
        });
  }

  @Test
  public void shouldThrowExceptionOnUpdateWithInvalidPayload() {
    // given
    final Record<WorkflowInstanceRecordValue> workflowInstanceRecordValueRecord =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withActivityId("task-1")
            .findFirst()
            .get();

    // when
    final Throwable throwable =
        catchThrowable(
            () ->
                clientRule
                    .getWorkflowClient()
                    .newUpdatePayloadCommand(
                        workflowInstanceRecordValueRecord.getValue().getWorkflowInstanceKey())
                    .payload("[]")
                    .send()
                    .join());

    // then
    assertThat(throwable).isInstanceOf(ClientException.class);
    assertThat(throwable.getMessage())
        .contains("Document has invalid format. On root level an object is only allowed.");
  }

  @Test
  public void shouldUpdatePayloadAndCompleteJobAfterwards() {
    // given
    final Record<WorkflowInstanceRecordValue> workflowInstanceRecordValueRecord =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withActivityId("task-1")
            .findFirst()
            .get();

    clientRule
        .getWorkflowClient()
        .newUpdatePayloadCommand(
            workflowInstanceRecordValueRecord.getValue().getWorkflowInstanceKey())
        .payload(PAYLOAD)
        .send()
        .join();
    assertWorkflowInstancePayloadUpdated();

    // when
    clientRule
        .getJobClient()
        .newWorker()
        .jobType("task-1")
        .handler(
            (client, job) ->
                client.newCompleteCommand(job.getKey()).payload("{\"result\": \"ok\"}").send())
        .open();

    // then
    assertWorkflowInstanceCompleted(
        "process",
        (wfEvent) -> {
          //          assertThat(wfEvent.getPayload()).isEqualTo("{\"foo\":
          // \"bar\",\"result\":\"ok\"}");
          assertThat(wfEvent.getPayloadAsMap())
              .hasSize(2)
              .contains(entry("foo", "bar"))
              .contains(entry("result", "ok"));
        });
  }

  @Test
  public void shouldUpdatePayloadWithMap() {
    // given
    final long workflowInstanceKey =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withActivityId("task-1")
            .findFirst()
            .get()
            .getValue()
            .getWorkflowInstanceKey();

    // when
    clientRule
        .getWorkflowClient()
        .newUpdatePayloadCommand(workflowInstanceKey)
        .payload(Collections.singletonMap("foo", "bar"))
        .send()
        .join();

    // then
    assertWorkflowInstancePayloadUpdated(
        (payloadUpdatedEvent) -> {
          assertThat(payloadUpdatedEvent.getPayload()).isEqualTo(PAYLOAD);
          assertThat(payloadUpdatedEvent.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
        });
  }

  @Test
  public void shouldUpdatePayloadWithObject() {
    // given
    final PayloadObject newPayload = new PayloadObject();
    newPayload.foo = "bar";

    final long workflowInstanceKey =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withActivityId("task-1")
            .findFirst()
            .get()
            .getValue()
            .getWorkflowInstanceKey();

    // when
    clientRule
        .getWorkflowClient()
        .newUpdatePayloadCommand(workflowInstanceKey)
        .payload(newPayload)
        .send()
        .join();

    // then
    assertWorkflowInstancePayloadUpdated(
        (payloadUpdatedEvent) -> {
          assertThat(payloadUpdatedEvent.getPayload()).isEqualTo(PAYLOAD);
          assertThat(payloadUpdatedEvent.getPayloadAsMap()).containsOnly(entry("foo", "bar"));
        });
  }

  @Test
  public void shouldFailUpdatePayloadIfWorkflowInstanceIsCompleted() {
    // given
    clientRule
        .getJobClient()
        .newWorker()
        .jobType("task-1")
        .handler(
            (client, job) ->
                client.newCompleteCommand(job.getKey()).payload("{\"result\": \"done\"}").send())
        .open();
    assertWorkflowInstanceCompleted("process");

    final long workflowInstanceKey =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withActivityId("task-1")
            .findFirst()
            .get()
            .getValue()
            .getWorkflowInstanceKey();

    // then
    thrown.expect(ClientException.class);
    thrown.expectMessage(
        "Command (UPDATE_PAYLOAD) for event with key " + workflowInstanceKey + " was rejected");

    // when
    clientRule
        .getWorkflowClient()
        .newUpdatePayloadCommand(workflowInstanceKey)
        .payload(PAYLOAD)
        .send()
        .join();
  }

  public static class PayloadObject {
    public String foo;
  }
}
