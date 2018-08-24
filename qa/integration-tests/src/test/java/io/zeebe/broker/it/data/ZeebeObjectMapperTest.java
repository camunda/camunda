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
package io.zeebe.broker.it.data;

import static io.zeebe.broker.it.util.TopicEventRecorder.state;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.it.ClientRule;
import io.zeebe.broker.it.EmbeddedBrokerRule;
import io.zeebe.broker.it.util.RecordingJobHandler;
import io.zeebe.broker.it.util.TopicEventRecorder;
import io.zeebe.gateway.api.events.DeploymentState;
import io.zeebe.gateway.api.events.IncidentState;
import io.zeebe.gateway.api.events.JobEvent;
import io.zeebe.gateway.api.events.JobState;
import io.zeebe.gateway.api.events.WorkflowInstanceEvent;
import io.zeebe.gateway.api.events.WorkflowInstanceState;
import io.zeebe.gateway.api.record.Record;
import io.zeebe.gateway.api.record.ValueType;
import io.zeebe.gateway.impl.record.RecordClassMapping;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class ZeebeObjectMapperTest {
  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public ClientRule clientRule = new ClientRule(brokerRule);

  public TopicEventRecorder eventRecorder = new TopicEventRecorder(clientRule, false);

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(brokerRule).around(clientRule).around(eventRecorder);

  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void shouldCompleteJobWithDeserializedEvent() throws InterruptedException {
    // given
    eventRecorder.startRecordingEvents();

    clientRule
        .getJobClient()
        .newCreateCommand()
        .jobType("foo")
        .payload("{\"foo\":\"bar\"}")
        .send()
        .join();

    final RecordingJobHandler jobHandler = new RecordingJobHandler();
    clientRule.getJobClient().newWorker().jobType("foo").handler(jobHandler).open();

    waitUntil(() -> jobHandler.getHandledJobs().size() > 0);

    final JobEvent jobEvent = jobHandler.getHandledJobs().get(0);
    final String serializedJobEvent = jobEvent.toJson();

    // when
    final JobEvent deserializedJobEvent =
        clientRule.getClient().objectMapper().fromJson(serializedJobEvent, JobEvent.class);

    assertThat(deserializedJobEvent.getPayload()).isEqualTo("{\"foo\":\"bar\"}");

    clientRule
        .getJobClient()
        .newCompleteCommand(deserializedJobEvent)
        .payload("{\"foo\":\"baz\"}")
        .send()
        .join();

    // then
    waitUntil(() -> eventRecorder.hasJobEvent(state(JobState.COMPLETED)));

    final JobEvent completedJobEvent = eventRecorder.getJobEvents(JobState.COMPLETED).get(0);
    assertThat(completedJobEvent.getPayload()).isEqualTo("{\"foo\":\"baz\"}");
  }

  @Test
  public void shouldSerializeAndDeserializeRecords() throws InterruptedException {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess("workflow")
            .startEvent("start")
            .serviceTask("task1", t -> t.zeebeTaskType("task").zeebeTaskHeader("cust", "val"))
            .serviceTask("task2", t -> t.zeebeTaskType("task").zeebeInput("$.baz", "$.baz"))
            .endEvent("end")
            .done();

    clientRule
        .getWorkflowClient()
        .newDeployCommand()
        .addWorkflowModel(workflow, "workflow.bpmn")
        .send()
        .join();

    final WorkflowInstanceEvent workflowInstanceEvent =
        clientRule
            .getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("workflow")
            .latestVersion()
            .payload("{\"foo\":\"bar\"}")
            .send()
            .join();

    clientRule
        .getJobClient()
        .newWorker()
        .jobType("task")
        .handler((c, j) -> c.newCompleteCommand(j).send())
        .open();

    // when
    final List<Record> records = new CopyOnWriteArrayList<>();

    clientRule
        .getTopicClient()
        .newSubscription()
        .name("test")
        .recordHandler(records::add)
        .startAtHeadOfTopic()
        .open();

    clientRule
        .getClient()
        .newManagementSubscription()
        .name("test-management")
        .recordHandler(records::add)
        .startAtHeadOfTopic()
        .open();

    waitUntil(
        () ->
            records
                .stream()
                .anyMatch(recordFilter(ValueType.INCIDENT, IncidentState.CREATED.name())));

    final List<WorkflowInstanceEvent> workflowInstanceEvents =
        records
            .stream()
            .filter(
                recordFilter(
                    ValueType.WORKFLOW_INSTANCE, WorkflowInstanceState.ELEMENT_READY.name()))
            .map(r -> (WorkflowInstanceEvent) r)
            .collect(Collectors.toList());

    final WorkflowInstanceEvent latestWorkflowInstanceEvent =
        workflowInstanceEvents.get(workflowInstanceEvents.size() - 1);

    clientRule
        .getWorkflowClient()
        .newUpdatePayloadCommand(latestWorkflowInstanceEvent)
        .payload("{\"baz\":123}")
        .send()
        .join();

    waitUntil(
        () ->
            records
                .stream()
                .anyMatch(
                    recordFilter(
                            ValueType.WORKFLOW_INSTANCE,
                            WorkflowInstanceState.ELEMENT_COMPLETED.name())
                        .and(r -> r.getKey() == workflowInstanceEvent.getWorkflowInstanceKey())));
    waitUntil(
        () ->
            records
                .stream()
                .anyMatch(recordFilter(ValueType.DEPLOYMENT, DeploymentState.CREATED.name())));

    // then
    records.forEach(
        r -> {
          final String serializedRecord = r.toJson();

          final Record deserizaliedRecord =
              clientRule
                  .getClient()
                  .objectMapper()
                  .fromJson(
                      serializedRecord, RecordClassMapping.getRecordOfImplClass(r.getClass()));

          assertThat(deserizaliedRecord.getMetadata().getValueType())
              .isEqualTo(r.getMetadata().getValueType());
          assertThat(deserizaliedRecord.getMetadata().getRecordType())
              .isEqualTo(r.getMetadata().getRecordType());
          assertThat(deserizaliedRecord.getMetadata().getIntent())
              .isEqualTo(r.getMetadata().getIntent());
        });
  }

  @Test
  public void shouldSerializeJobRecordWithPayload() throws InterruptedException {
    // given
    final JobEvent jobEvent =
        clientRule
            .getJobClient()
            .newCreateCommand()
            .jobType("foo")
            .payload("{\"foo\":\"bar\"}")
            .send()
            .join();

    // when / then
    final String json = jobEvent.toJson();
    assertThat(json).contains("\"payload\":{\"foo\":\"bar\"}");

    final JobEvent deserializedRecord =
        clientRule.getClient().objectMapper().fromJson(json, JobEvent.class);
    assertThat(deserializedRecord.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
  }

  @Test
  public void shouldSerializeJobRecordWithoutPayload() throws InterruptedException {
    // given
    final JobEvent jobEvent =
        clientRule.getJobClient().newCreateCommand().jobType("foo").send().join();

    // when / then
    final String json = jobEvent.toJson();
    assertThat(json).contains("\"payload\":{}");

    final JobEvent deserializedRecord =
        clientRule.getClient().objectMapper().fromJson(json, JobEvent.class);
    assertThat(deserializedRecord.getPayload()).isEqualTo("{}");
    assertThat(deserializedRecord.getPayloadAsMap()).isEmpty();
  }

  @Test
  public void shouldSerializeWorkflowInstanceRecordWithPayload() throws InterruptedException {
    // given
    clientRule
        .getWorkflowClient()
        .newDeployCommand()
        .addWorkflowModel(
            Bpmn.createExecutableProcess("workflow").startEvent().done(), "workflow.bpmn")
        .send()
        .join();

    final WorkflowInstanceEvent workflowInstanceEvent =
        clientRule
            .getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("workflow")
            .latestVersion()
            .payload("{\"foo\":\"bar\"}")
            .send()
            .join();

    // when / then
    final String json = workflowInstanceEvent.toJson();
    assertThat(json).contains("\"payload\":{\"foo\":\"bar\"}");

    final WorkflowInstanceEvent deserializedRecord =
        clientRule.getClient().objectMapper().fromJson(json, WorkflowInstanceEvent.class);
    assertThat(deserializedRecord.getPayload()).isEqualTo("{\"foo\":\"bar\"}");
  }

  @Test
  public void shouldSerializeWorkflowInstanceRecordWithoutPayload() throws InterruptedException {
    // given
    clientRule
        .getWorkflowClient()
        .newDeployCommand()
        .addWorkflowModel(
            Bpmn.createExecutableProcess("workflow").startEvent().done(), "workflow.bpmn")
        .send()
        .join();

    final WorkflowInstanceEvent workflowInstanceEvent =
        clientRule
            .getWorkflowClient()
            .newCreateInstanceCommand()
            .bpmnProcessId("workflow")
            .latestVersion()
            .send()
            .join();

    // when / then
    final String json = workflowInstanceEvent.toJson();
    assertThat(json).contains("\"payload\":{}");

    final WorkflowInstanceEvent deserializedRecord =
        clientRule.getClient().objectMapper().fromJson(json, WorkflowInstanceEvent.class);
    assertThat(deserializedRecord.getPayload()).isEqualTo("{}");
    assertThat(deserializedRecord.getPayloadAsMap()).isEmpty();
  }

  private static Predicate<Record> recordFilter(ValueType valueType, String intent) {
    return r ->
        r.getMetadata().getValueType() == valueType && r.getMetadata().getIntent().equals(intent);
  }
}
