/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor.workflow.message;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.engine.util.PublishMessageClient;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.RecordMetadata;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceSubscriptionRecordValue;
import io.zeebe.exporter.api.record.value.deployment.DeployedWorkflow;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.RecordType;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.WorkflowInstances;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public class MessageCorrelationTest {

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance RECEIVE_TASK_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask("receive-message")
          .message(m -> m.name("message").zeebeCorrelationKey("key"))
          .endEvent()
          .done();

  private static final BpmnModelInstance SINGLE_MESSAGE_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("receive-message")
          .message(m -> m.name("message").zeebeCorrelationKey("key"))
          .endEvent()
          .done();

  private static final BpmnModelInstance TWO_MESSAGES_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("message1")
          .message(m -> m.name("ping").zeebeCorrelationKey("key"))
          .intermediateCatchEvent("message2")
          .message(m -> m.name("ping").zeebeCorrelationKey("key"))
          .done();

  private static final BpmnModelInstance BOUNDARY_EVENTS_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask("task")
          .message(m -> m.name("taskMsg").zeebeCorrelationKey("key"))
          .boundaryEvent("msg1")
          .message(m -> m.name("msg1").zeebeCorrelationKey("key"))
          .endEvent("msg1End")
          .moveToActivity("task")
          .boundaryEvent("msg2")
          .message(m -> m.name("msg2").zeebeCorrelationKey("key"))
          .endEvent("msg2End")
          .moveToActivity("task")
          .endEvent("taskEnd")
          .done();

  @Rule public EngineRule engine = new EngineRule();

  @Test
  public void shouldCorrelateMessageIfEnteredBefore() {
    // given
    final String messageId = UUID.randomUUID().toString();
    engine.deploy(SINGLE_MESSAGE_WORKFLOW);
    final long workflowInstanceKey =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.OPENED).exists())
        .isTrue();

    // when
    engine
        .message()
        .withName("message")
        .withCorrelationKey("order-123")
        .withTimeToLive(1000L)
        .withVariables(asMsgPack("foo", "bar"))
        .withId(messageId)
        .publish();

    // then
    final Record<WorkflowInstanceRecordValue> event =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("receive-message")
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();
    final Map<String, String> variables =
        WorkflowInstances.getCurrentVariables(workflowInstanceKey, event.getPosition());
    assertThat(variables).containsOnly(entry("key", "\"order-123\""), entry("foo", "\"bar\""));
  }

  @Test
  public void shouldCorrelateMessageIfPublishedBefore() {
    // given
    engine.deploy(SINGLE_MESSAGE_WORKFLOW);

    engine
        .message()
        .withName("message")
        .withCorrelationKey("order-123")
        .withVariables(asMsgPack("foo", "bar"))
        .publish();

    // when
    final long workflowInstanceKey =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();

    // then
    final Record<WorkflowInstanceRecordValue> event =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("receive-message")
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();
    final Map<String, String> variables =
        WorkflowInstances.getCurrentVariables(workflowInstanceKey, event.getPosition());
    assertThat(variables).containsOnly(entry("key", "\"order-123\""), entry("foo", "\"bar\""));
  }

  @Test
  public void shouldCorrelateMessageIfCorrelationKeyIsANumber() {
    // given
    engine.deploy(SINGLE_MESSAGE_WORKFLOW);

    engine
        .message()
        .withName("message")
        .withCorrelationKey("123")
        .withVariables(asMsgPack("foo", "bar"))
        .publish();

    // when
    final long workflowInstanceKey =
        engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", 123).create();

    // then
    final Record<WorkflowInstanceRecordValue> event =
        RecordingExporter.workflowInstanceRecords()
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();
    final Map<String, String> variables =
        WorkflowInstances.getCurrentVariables(workflowInstanceKey, event.getPosition());
    assertThat(variables).containsOnly(entry("key", "123"), entry("foo", "\"bar\""));
  }

  @Test
  public void shouldCorrelateFirstPublishedMessage() {
    // given
    engine.deploy(SINGLE_MESSAGE_WORKFLOW);

    final PublishMessageClient messageClient =
        engine.message().withName("message").withCorrelationKey("order-123");

    messageClient.withVariables(asMsgPack("nr", 1)).publish();
    messageClient.withVariables(asMsgPack("nr", 2)).publish();

    // when
    final long workflowInstanceKey =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();

    // then
    final Record<WorkflowInstanceRecordValue> event =
        RecordingExporter.workflowInstanceRecords()
            .withElementType(BpmnElementType.PROCESS)
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();
    final Map<String, String> variables =
        WorkflowInstances.getCurrentVariables(workflowInstanceKey, event.getPosition());
    assertThat(variables).containsOnly(entry("key", "\"order-123\""), entry("nr", "1"));
  }

  @Test
  public void shouldCorrelateMessageWithZeroTTL() {
    // given
    engine.deploy(SINGLE_MESSAGE_WORKFLOW);

    final long workflowInstanceKey =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.OPENED).exists())
        .isTrue();

    // when
    engine
        .message()
        .withName("message")
        .withCorrelationKey("order-123")
        .withVariables(asMsgPack("foo", "bar"))
        .withTimeToLive(0L)
        .publish();

    // then
    final Record<WorkflowInstanceRecordValue> event =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("receive-message")
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();

    assertThat(event.getValue().getWorkflowInstanceKey()).isEqualTo(workflowInstanceKey);
  }

  @Test
  public void shouldNotCorrelateMessageAfterTTL() {
    // given
    engine.deploy(SINGLE_MESSAGE_WORKFLOW);

    final PublishMessageClient messageClient =
        engine.message().withName("message").withCorrelationKey("order-123");

    messageClient.withVariables(asMsgPack("nr", 1)).withTimeToLive(0L).publish();
    messageClient.withVariables(asMsgPack("nr", 2)).withTimeToLive(10_000L).publish();

    // when
    final long workflowInstanceKey =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();

    // then
    final Record<WorkflowInstanceRecordValue> event =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("receive-message")
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();
    final Map<String, String> variables =
        WorkflowInstances.getCurrentVariables(workflowInstanceKey, event.getPosition());
    assertThat(variables).containsOnly(entry("key", "\"order-123\""), entry("nr", "2"));
  }

  @Test
  public void shouldCorrelateMessageByCorrelationKey() {
    // given
    engine.deploy(SINGLE_MESSAGE_WORKFLOW);

    final long workflowInstanceKey1 =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();
    final long workflowInstanceKey2 =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-456")
            .create();

    // when
    engine
        .message()
        .withName("message")
        .withCorrelationKey("order-123")
        .withVariables(asMsgPack("nr", 1))
        .publish();

    engine
        .message()
        .withName("message")
        .withCorrelationKey("order-456")
        .withVariables(asMsgPack("nr", 2))
        .publish();

    // then
    final Record<WorkflowInstanceRecordValue> catchEventOccurred1 =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey1)
            .withElementType(BpmnElementType.INTERMEDIATE_CATCH_EVENT)
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();
    final Map<String, String> variables1 =
        WorkflowInstances.getCurrentVariables(
            workflowInstanceKey1, catchEventOccurred1.getPosition());
    assertThat(variables1).containsOnly(entry("key", "\"order-123\""), entry("nr", "1"));

    final Record<WorkflowInstanceRecordValue> catchEventOccurred2 =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey2)
            .withElementType(BpmnElementType.INTERMEDIATE_CATCH_EVENT)
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst();
    final Map<String, String> variables2 =
        WorkflowInstances.getCurrentVariables(
            workflowInstanceKey2, catchEventOccurred2.getPosition());
    assertThat(variables2).containsOnly(entry("key", "\"order-456\""), entry("nr", "2"));
  }

  @Test
  public void shouldCorrelateMessageToAllSubscriptions() {
    // given
    engine.deploy(SINGLE_MESSAGE_WORKFLOW);

    final long workflowInstanceKey1 =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();
    final long workflowInstanceKey2 =
        engine
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("key", "order-123")
            .create();

    // when
    engine.message().withName("message").withCorrelationKey("order-123").publish();

    // then
    final List<Record<WorkflowInstanceRecordValue>> events =
        RecordingExporter.workflowInstanceRecords()
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId("receive-message")
            .limit(2)
            .collect(Collectors.toList());

    assertThat(events)
        .extracting(r -> r.getValue().getWorkflowInstanceKey())
        .contains(workflowInstanceKey1, workflowInstanceKey2);
  }

  @Test
  public void shouldCorrelateMessageOnlyOnceIfPublishedBefore() {
    // given
    engine.deploy(TWO_MESSAGES_WORKFLOW);

    final PublishMessageClient messageClient =
        engine.message().withName("ping").withCorrelationKey("123");

    messageClient.withVariables(asMsgPack("nr", 1)).publish();
    messageClient.withVariables(asMsgPack("nr", 2)).publish();

    // when
    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    // then
    final List<Object> correlatedValues =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .filter(r -> r.getValue().getElementId().startsWith("message"))
            .limit(2)
            .map(
                event ->
                    tuple(
                        event.getValue().getElementId(),
                        WorkflowInstances.getCurrentVariables(
                                event.getValue().getWorkflowInstanceKey(), event.getPosition())
                            .get("nr")))
            .collect(Collectors.toList());
    assertThat(correlatedValues).contains(tuple("message1", "1"), tuple("message2", "2"));
  }

  @Test
  public void shouldCorrelateMessageOnlyOnceIfEnteredBefore() {
    // given
    engine.deploy(TWO_MESSAGES_WORKFLOW);

    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    // when
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    final PublishMessageClient messageClient =
        engine.message().withName("ping").withCorrelationKey("123");

    messageClient.withVariables(asMsgPack("nr", 1)).publish();

    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .limit(2)
                .count())
        .isEqualTo(2);

    messageClient.withVariables(asMsgPack("nr", 2)).publish();

    // then
    final List<Object> correlatedValues =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .filter(r -> r.getValue().getElementId().startsWith("message"))
            .limit(2)
            .map(
                event ->
                    tuple(
                        event.getValue().getElementId(),
                        WorkflowInstances.getCurrentVariables(
                                event.getValue().getWorkflowInstanceKey(), event.getPosition())
                            .get("nr")))
            .collect(Collectors.toList());
    assertThat(correlatedValues).contains(tuple("message1", "1"), tuple("message2", "2"));
  }

  @Test
  public void shouldCorrelateMessageOnlyOnceToInstance() {
    // given
    engine.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway()
            .intermediateCatchEvent("message1")
            .message(m -> m.name("ping").zeebeCorrelationKey("key"))
            .moveToLastGateway()
            .intermediateCatchEvent("message2")
            .message(m -> m.name("ping").zeebeCorrelationKey("key"))
            .done());

    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    // when
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .limit(2)
                .count())
        .isEqualTo(2);

    final PublishMessageClient client = engine.message().withName("ping").withCorrelationKey("123");

    client.withVariables(asMsgPack("nr", 1)).publish();
    client.withVariables(asMsgPack("nr", 2)).publish();

    // then
    final List<Object> correlatedValues =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .filter(r -> r.getValue().getElementId().startsWith("message"))
            .limit(2)
            .map(
                event ->
                    WorkflowInstances.getCurrentVariables(
                            event.getValue().getWorkflowInstanceKey(), event.getPosition())
                        .get("nr"))
            .collect(Collectors.toList());
    assertThat(correlatedValues).contains("1", "2");
  }

  @Test
  public void shouldCorrelateOnlyOneMessagePerCatchElement() {
    // given
    engine.deploy(TWO_MESSAGES_WORKFLOW);

    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    final PublishMessageClient messageClient =
        engine.message().withName("ping").withCorrelationKey("123");

    messageClient.withVariables(asMsgPack("nr", 1)).publish();
    messageClient.withVariables(asMsgPack("nr", 2)).publish();

    // then
    final List<Object> correlatedValues =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .filter(r -> r.getValue().getElementId().startsWith("message"))
            .limit(2)
            .map(
                event ->
                    tuple(
                        event.getValue().getElementId(),
                        WorkflowInstances.getCurrentVariables(
                                event.getValue().getWorkflowInstanceKey(), event.getPosition())
                            .get("nr")))
            .collect(Collectors.toList());
    assertThat(correlatedValues).contains(tuple("message1", "1"), tuple("message2", "2"));
  }

  @Test
  public void shouldCorrelateCorrectBoundaryEvent() {
    // given
    engine.deploy(BOUNDARY_EVENTS_WORKFLOW);
    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    // when
    awaitSubscriptionsOpened(3);

    engine
        .message()
        .withName("msg1")
        .withCorrelationKey("123")
        .withVariables(asMsgPack("foo", 1))
        .publish();

    // then
    assertThat(RecordingExporter.workflowInstanceRecords().limitToWorkflowInstanceCompleted())
        .filteredOn(r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_ACTIVATED)
        .extracting(Record::getValue)
        .extracting(WorkflowInstanceRecordValue::getElementId)
        .contains("msg1End")
        .doesNotContain("taskEnd", "msg2End");
  }

  @Test
  public void shouldNotTriggerBoundaryEventIfReceiveTaskTriggeredFirst() {
    // given
    engine.deploy(BOUNDARY_EVENTS_WORKFLOW);
    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    // when
    awaitSubscriptionsOpened(3);

    engine
        .message()
        .withName("taskMsg")
        .withCorrelationKey("123")
        .withVariables(asMsgPack("foo", 1))
        .publish();

    // then
    assertThat(RecordingExporter.workflowInstanceRecords().limitToWorkflowInstanceCompleted())
        .filteredOn(r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_ACTIVATED)
        .extracting(Record::getValue)
        .extracting(WorkflowInstanceRecordValue::getElementId)
        .contains("taskEnd")
        .doesNotContain("msg1End", "msg2End");
  }

  @Test
  public void shouldNotTriggerReceiveTaskIfBoundaryEventTriggeredFirst() {
    // given
    engine.deploy(BOUNDARY_EVENTS_WORKFLOW);
    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    // when
    awaitSubscriptionsOpened(3); // await both subscriptions opened

    engine
        .message()
        .withName("msg2")
        .withCorrelationKey("123")
        .withVariables(asMsgPack("foo", 1))
        .publish();

    // then
    assertThat(RecordingExporter.workflowInstanceRecords().limitToWorkflowInstanceCompleted())
        .filteredOn(r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_ACTIVATED)
        .extracting(Record::getValue)
        .extracting(WorkflowInstanceRecordValue::getElementId)
        .contains("msg2End")
        .doesNotContain("taskEnd", "msg1End");
  }

  @Test
  public void testIntermediateMessageEventLifeCycle() {
    // given
    engine.deploy(SINGLE_MESSAGE_WORKFLOW);

    engine.message().withName("message").withCorrelationKey("order-123").publish();

    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "order-123").create();

    final List<Record<WorkflowInstanceRecordValue>> events =
        RecordingExporter.workflowInstanceRecords()
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(events)
        .filteredOn(r -> r.getValue().getElementId().equals("receive-message"))
        .extracting(Record::getMetadata)
        .extracting(RecordMetadata::getIntent)
        .containsExactly(
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.EVENT_OCCURRED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void testReceiveTaskLifeCycle() {
    // given
    engine.deploy(RECEIVE_TASK_WORKFLOW);
    engine.message().withName("message").withCorrelationKey("order-123").publish();
    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "order-123").create();

    final List<Record<WorkflowInstanceRecordValue>> events =
        RecordingExporter.workflowInstanceRecords()
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(events)
        .filteredOn(r -> r.getValue().getElementId().equals("receive-message"))
        .extracting(Record::getMetadata)
        .extracting(RecordMetadata::getIntent)
        .containsExactly(
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.EVENT_OCCURRED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void testBoundaryMessageEventLifecycle() {
    // given
    engine.deploy(BOUNDARY_EVENTS_WORKFLOW);
    engine.message().withName("msg1").withCorrelationKey("order-123").publish();

    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "order-123").create();

    final List<Record<WorkflowInstanceRecordValue>> events =
        RecordingExporter.workflowInstanceRecords()
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(events)
        .extracting(r -> tuple(r.getValue().getElementId(), r.getMetadata().getIntent()))
        .containsSequence(
            tuple("task", WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple("task", WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple("task", WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple("task", WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("task", WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple("msg1", WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple("msg1", WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple("msg1", WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple("msg1", WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCorrelateToNonInterruptingBoundaryEvent() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", b -> b.zeebeTaskType("type"))
            .boundaryEvent("msg1")
            .cancelActivity(false)
            .message(m -> m.name("msg1").zeebeCorrelationKey("key"))
            .endEvent("msg1End")
            .moveToActivity("task")
            .endEvent("taskEnd")
            .done();
    engine.deploy(workflow);
    engine.workflowInstance().ofBpmnProcessId(PROCESS_ID).withVariable("key", "123").create();

    // when
    final PublishMessageClient messageClient =
        engine.message().withName("msg1").withCorrelationKey("123");

    messageClient.withVariables(asMsgPack("foo", 0)).publish();
    messageClient.withVariables(asMsgPack("foo", 1)).publish();
    messageClient.withVariables(asMsgPack("foo", 2)).publish();

    assertThat(awaitMessagesCorrelated(3)).hasSize(3);

    // then
    final List<Object> correlatedValues =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId("msg1")
            .limit(3)
            .map(
                event ->
                    WorkflowInstances.getCurrentVariables(
                            event.getValue().getWorkflowInstanceKey(), event.getPosition())
                        .get("foo"))
            .collect(Collectors.toList());
    assertThat(correlatedValues).containsOnly("0", "1", "2");
  }

  @Test
  public void shouldCorrelateMessageAgainAfterRejection() {
    // given
    engine.message().withName("a").withCorrelationKey("123").publish();
    engine.message().withName("b").withCorrelationKey("123").publish();

    final BpmnModelInstance twoMessages =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .eventBasedGateway("split")
            .intermediateCatchEvent(
                "element-a", c -> c.message(m -> m.name("a").zeebeCorrelationKey("key")))
            .intermediateCatchEvent(
                "element-ab", c -> c.message(m -> m.name("b").zeebeCorrelationKey("key")))
            .exclusiveGateway("merge")
            .endEvent()
            .moveToNode("split")
            .intermediateCatchEvent(
                "element-b", c -> c.message(m -> m.name("b").zeebeCorrelationKey("key")))
            .intermediateCatchEvent(
                "element-ba", c -> c.message(m -> m.name("a").zeebeCorrelationKey("key")))
            .connectTo("merge")
            .done();

    final DeployedWorkflow workflow =
        engine.deploy(twoMessages).getValue().getDeployedWorkflows().get(0);

    // when
    engine.workflowInstance().ofBpmnProcessId("process").withVariable("key", "123").create();

    // then
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.CORRELATE)
                .withRecordType(RecordType.COMMAND_REJECTION)
                .limit(1))
        .isNotEmpty();
    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.REJECT).limit(1))
        .isNotEmpty();
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.CORRELATED)
                .limit(2))
        .extracting(r -> r.getValue().getMessageName())
        .containsExactlyInAnyOrder("a", "b");
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withElementId("process")
                .limit(1))
        .isNotEmpty();
  }

  private List<Record<WorkflowInstanceSubscriptionRecordValue>> awaitMessagesCorrelated(
      int messagesCount) {
    return RecordingExporter.workflowInstanceSubscriptionRecords(
            WorkflowInstanceSubscriptionIntent.CORRELATED)
        .limit(messagesCount)
        .asList();
  }

  private List<Record<WorkflowInstanceSubscriptionRecordValue>> awaitSubscriptionsOpened(
      int subscriptionsCount) {
    return RecordingExporter.workflowInstanceSubscriptionRecords()
        .withIntent(WorkflowInstanceSubscriptionIntent.OPENED)
        .limit(subscriptionsCount)
        .asList();
  }
}
