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
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.RecordMetadata;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceSubscriptionRecordValue;
import io.zeebe.exporter.api.record.value.deployment.DeployedWorkflow;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.test.util.MsgPackUtil;
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

  @Rule public EngineRule engineRule = new EngineRule();

  @Test
  public void shouldCorrelateMessageIfEnteredBefore() {
    // given
    final String messageId = UUID.randomUUID().toString();
    engineRule.deploy(SINGLE_MESSAGE_WORKFLOW);
    final long workflowInstanceKey =
        engineRule
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "order-123")))
            .getValue()
            .getInstanceKey();

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.OPENED).exists())
        .isTrue();

    // when
    engineRule.publishMessage(
        "order-123",
        r ->
            r.setName("message")
                .setTimeToLive(1000)
                .setCorrelationKey("order-123")
                .setVariables(asMsgPack("foo", "bar"))
                .setMessageId(messageId));

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
    engineRule.deploy(SINGLE_MESSAGE_WORKFLOW);

    engineRule.publishMessage("message", "order-123", asMsgPack("foo", "bar"));

    // when
    final long workflowInstanceKey =
        engineRule
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "order-123")))
            .getValue()
            .getInstanceKey();

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
    engineRule.deploy(SINGLE_MESSAGE_WORKFLOW);

    engineRule.publishMessage("message", "123", asMsgPack("foo", "bar"));

    // when
    final long workflowInstanceKey =
        engineRule
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", 123)))
            .getValue()
            .getInstanceKey();

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
    engineRule.deploy(SINGLE_MESSAGE_WORKFLOW);

    engineRule.publishMessage("message", "order-123", asMsgPack("nr", 1));
    engineRule.publishMessage("message", "order-123", asMsgPack("nr", 2));

    // when
    final long workflowInstanceKey =
        engineRule
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "order-123")))
            .getValue()
            .getInstanceKey();

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
    engineRule.deploy(SINGLE_MESSAGE_WORKFLOW);

    final long workflowInstanceKey =
        engineRule
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "order-123")))
            .getValue()
            .getInstanceKey();

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.OPENED).exists())
        .isTrue();

    // when
    engineRule.publishMessage(
        "order-123",
        r -> r.setName("message").setVariables(asMsgPack("foo", "bar")).setTimeToLive(0L));

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
    engineRule.deploy(SINGLE_MESSAGE_WORKFLOW);

    engineRule.publishMessage(
        "order-123", r -> r.setName("message").setVariables(asMsgPack("nr", 1)).setTimeToLive(0L));
    engineRule.publishMessage(
        "order-123",
        r -> r.setName("message").setVariables(asMsgPack("nr", 2)).setTimeToLive(10_000L));

    // when
    final long workflowInstanceKey =
        engineRule
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "order-123")))
            .getValue()
            .getInstanceKey();

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
    engineRule.deploy(SINGLE_MESSAGE_WORKFLOW);

    final long workflowInstanceKey1 =
        engineRule
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "order-123")))
            .getValue()
            .getInstanceKey();
    final long workflowInstanceKey2 =
        engineRule
            .createWorkflowInstance(
                r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "order-456")))
            .getValue()
            .getInstanceKey();

    // when
    engineRule.publishMessage("message", "order-123", asMsgPack("nr", 1));
    engineRule.publishMessage("message", "order-456", asMsgPack("nr", 2));

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
    engineRule.deploy(SINGLE_MESSAGE_WORKFLOW);

    final long workflowInstanceKey1 =
        engineRule
            .createWorkflowInstance(
                r1 -> r1.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "order-123")))
            .getValue()
            .getInstanceKey();
    final long workflowInstanceKey2 =
        engineRule
            .createWorkflowInstance(
                r1 -> r1.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "order-123")))
            .getValue()
            .getInstanceKey();

    // when
    engineRule.publishMessage("message", "order-123");

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
    engineRule.deploy(TWO_MESSAGES_WORKFLOW);

    engineRule.publishMessage("ping", "123", asMsgPack("nr", 1));
    engineRule.publishMessage("ping", "123", asMsgPack("nr", 2));

    // when
    engineRule
        .createWorkflowInstance(
            r1 -> r1.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "123")))
        .getValue()
        .getInstanceKey();

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
    engineRule.deploy(TWO_MESSAGES_WORKFLOW);

    engineRule
        .createWorkflowInstance(
            r1 -> r1.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "123")))
        .getValue()
        .getInstanceKey();

    // when
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    engineRule.publishMessage("ping", "123", asMsgPack("nr", 1));

    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .limit(2)
                .count())
        .isEqualTo(2);

    engineRule.publishMessage("ping", "123", asMsgPack("nr", 2));

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
    engineRule.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway()
            .intermediateCatchEvent("message1")
            .message(m -> m.name("ping").zeebeCorrelationKey("key"))
            .moveToLastGateway()
            .intermediateCatchEvent("message2")
            .message(m -> m.name("ping").zeebeCorrelationKey("key"))
            .done());

    engineRule
        .createWorkflowInstance(
            r1 -> r1.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "123")))
        .getValue()
        .getInstanceKey();

    // when
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .limit(2)
                .count())
        .isEqualTo(2);

    engineRule.publishMessage("ping", "123", asMsgPack("nr", 1));
    engineRule.publishMessage("ping", "123", asMsgPack("nr", 2));

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
    engineRule.deploy(TWO_MESSAGES_WORKFLOW);

    engineRule
        .createWorkflowInstance(
            r1 -> r1.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "123")))
        .getValue()
        .getInstanceKey();

    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    engineRule.publishMessage("ping", "123", asMsgPack("nr", 1));
    engineRule.publishMessage("ping", "123", asMsgPack("nr", 2));

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
    engineRule.deploy(BOUNDARY_EVENTS_WORKFLOW);
    engineRule
        .createWorkflowInstance(
            r1 -> r1.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "123")))
        .getValue()
        .getInstanceKey();

    // when
    awaitSubscriptionsOpened(3);
    engineRule.publishMessage("msg1", "123", asMsgPack("foo", 1));

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
    engineRule.deploy(BOUNDARY_EVENTS_WORKFLOW);
    engineRule
        .createWorkflowInstance(
            r1 -> r1.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "123")))
        .getValue()
        .getInstanceKey();

    // when
    awaitSubscriptionsOpened(3);
    engineRule.publishMessage("taskMsg", "123", asMsgPack("foo", 1));

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
    engineRule.deploy(BOUNDARY_EVENTS_WORKFLOW);
    engineRule
        .createWorkflowInstance(
            r1 -> r1.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "123")))
        .getValue()
        .getInstanceKey();

    // when
    awaitSubscriptionsOpened(3); // await both subscriptions opened
    engineRule.publishMessage("msg2", "123", asMsgPack("foo", 1));

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
    engineRule.deploy(SINGLE_MESSAGE_WORKFLOW);
    engineRule.publishMessage("message", "order-123");
    engineRule
        .createWorkflowInstance(
            r1 -> r1.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "order-123")))
        .getValue()
        .getInstanceKey();

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
    engineRule.deploy(RECEIVE_TASK_WORKFLOW);
    engineRule.publishMessage("message", "order-123");
    engineRule
        .createWorkflowInstance(
            r1 -> r1.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "order-123")))
        .getValue()
        .getInstanceKey();

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
    engineRule.deploy(BOUNDARY_EVENTS_WORKFLOW);
    engineRule.publishMessage("msg1", "order-123");
    engineRule
        .createWorkflowInstance(
            r1 -> r1.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "order-123")))
        .getValue()
        .getInstanceKey();

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
    engineRule.deploy(workflow);
    engineRule
        .createWorkflowInstance(
            r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "123")))
        .getValue()
        .getInstanceKey();

    // when
    engineRule.publishMessage("msg1", "123", asMsgPack("foo", 0));
    engineRule.publishMessage("msg1", "123", asMsgPack("foo", 1));
    engineRule.publishMessage("msg1", "123", asMsgPack("foo", 2));
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
    engineRule.publishMessage("a", "123");
    engineRule.publishMessage("b", "123");

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
        engineRule.deploy(twoMessages).getValue().getDeployedWorkflows().get(0);

    // when
    engineRule.createWorkflowInstance(
        c -> c.setKey(workflow.getWorkflowKey()).setVariables(MsgPackUtil.asMsgPack("key", "123")));

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
