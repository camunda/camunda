/*
 * Zeebe Broker Core
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
package io.zeebe.broker.workflow.message;

import static io.zeebe.broker.workflow.WorkflowAssert.assertWorkflowInstancePayload;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.RecordMetadata;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceSubscriptionRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class MessageCorrelationTest {

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance RECEIVE_TASK_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask("receive-message")
          .message(m -> m.name("message").zeebeCorrelationKey("$.key"))
          .endEvent()
          .done();

  private static final BpmnModelInstance SINGLE_MESSAGE_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("receive-message")
          .message(m -> m.name("message").zeebeCorrelationKey("$.key"))
          .endEvent()
          .done();

  private static final BpmnModelInstance TWO_MESSAGES_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("message1")
          .message(m -> m.name("ping").zeebeCorrelationKey("$.key"))
          .intermediateCatchEvent("message2")
          .message(m -> m.name("ping").zeebeCorrelationKey("$.key"))
          .done();

  private static final BpmnModelInstance BOUNDARY_EVENTS_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .receiveTask("task")
          .message(m -> m.name("taskMsg").zeebeCorrelationKey("$.key"))
          .boundaryEvent("msg1")
          .message(m -> m.name("msg1").zeebeCorrelationKey("$.key"))
          .endEvent("msg1End")
          .moveToActivity("task")
          .boundaryEvent("msg2")
          .message(m -> m.name("msg2").zeebeCorrelationKey("$.key"))
          .endEvent("msg2End")
          .moveToActivity("task")
          .endEvent("taskEnd")
          .done();

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();

  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldCorrelateMessageIfEnteredBefore() {
    // given
    testClient.deploy(SINGLE_MESSAGE_WORKFLOW);

    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "order-123"));

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.OPENED).exists())
        .isTrue();

    // when
    testClient.publishMessage("message", "order-123", asMsgPack("foo", "bar"));

    // then
    final Record<WorkflowInstanceRecordValue> event =
        testClient.receiveElementInState("receive-message", WorkflowInstanceIntent.EVENT_TRIGGERED);
    assertWorkflowInstancePayload(event, "{'key':'order-123', 'foo':'bar'}");
  }

  @Test
  public void shouldCorrelateMessageIfPublishedBefore() {
    // given
    testClient.deploy(SINGLE_MESSAGE_WORKFLOW);

    testClient.publishMessage("message", "order-123", asMsgPack("foo", "bar"));

    // when
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "order-123"));

    // then
    final Record<WorkflowInstanceRecordValue> event =
        testClient.receiveElementInState("receive-message", WorkflowInstanceIntent.EVENT_TRIGGERED);
    assertWorkflowInstancePayload(event, "{'key':'order-123', 'foo':'bar'}");
  }

  @Test
  public void shouldCorrelateMessageIfCorrelationKeyIsANumber() {
    // given
    testClient.deploy(SINGLE_MESSAGE_WORKFLOW);

    testClient.publishMessage("message", "123", asMsgPack("foo", "bar"));

    // when
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", 123));

    // then
    final Record<WorkflowInstanceRecordValue> event =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_COMPLETED);
    assertWorkflowInstancePayload(event, "{'key':123, 'foo':'bar'}");
  }

  @Test
  public void shouldCorrelateFirstPublishedMessage() {
    // given
    testClient.deploy(SINGLE_MESSAGE_WORKFLOW);

    testClient.publishMessage("message", "order-123", asMsgPack("nr", 1));
    testClient.publishMessage("message", "order-123", asMsgPack("nr", 2));

    // when
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "order-123"));

    // then
    final Record<WorkflowInstanceRecordValue> event =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_COMPLETED);

    assertWorkflowInstancePayload(event, "{'key':'order-123', 'nr':1}");
  }

  @Test
  public void shouldCorrelateMessageWithZeroTTL() {
    // given
    testClient.deploy(SINGLE_MESSAGE_WORKFLOW);

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "order-123"));

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.OPENED).exists())
        .isTrue();

    // when
    testClient.publishMessage("message", "order-123", asMsgPack("foo", "bar"), 0);

    // then
    final Record<WorkflowInstanceRecordValue> event =
        testClient.receiveElementInState("receive-message", WorkflowInstanceIntent.EVENT_TRIGGERED);

    assertThat(event.getValue().getWorkflowInstanceKey()).isEqualTo(workflowInstanceKey);
  }

  @Test
  public void shouldNotCorrelateMessageAfterTTL() {
    // given
    testClient.deploy(SINGLE_MESSAGE_WORKFLOW);

    testClient.publishMessage("message", "order-123", asMsgPack("nr", 1), 0);
    testClient.publishMessage("message", "order-123", asMsgPack("nr", 2), 10_000);

    // when
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "order-123"));

    // then
    final Record<WorkflowInstanceRecordValue> event =
        testClient.receiveElementInState("receive-message", WorkflowInstanceIntent.EVENT_TRIGGERED);

    assertWorkflowInstancePayload(event, "{'key':'order-123', 'nr':2}");
  }

  @Test
  public void shouldCorrelateMessageByCorrelationKey() {
    // given
    testClient.deploy(SINGLE_MESSAGE_WORKFLOW);

    final long workflowInstanceKey1 =
        testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "order-123"));
    final long workflowInstanceKey2 =
        testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "order-456"));

    // when
    testClient.publishMessage("message", "order-123", asMsgPack("nr", 1));
    testClient.publishMessage("message", "order-456", asMsgPack("nr", 2));

    // then
    final Record<WorkflowInstanceRecordValue> catchEventOccurred1 =
        testClient.receiveFirstWorkflowInstanceEvent(
            workflowInstanceKey1, WorkflowInstanceIntent.ELEMENT_COMPLETED);
    assertWorkflowInstancePayload(catchEventOccurred1, "{'key':'order-123', 'nr':1}");

    final Record<WorkflowInstanceRecordValue> catchEventOccurred2 =
        testClient.receiveFirstWorkflowInstanceEvent(
            workflowInstanceKey2, WorkflowInstanceIntent.ELEMENT_COMPLETED);
    assertWorkflowInstancePayload(catchEventOccurred2, "{'key':'order-456', 'nr':2}");
  }

  @Test
  public void shouldCorrelateMessageToAllSubscriptions() {
    // given
    testClient.deploy(SINGLE_MESSAGE_WORKFLOW);

    final long workflowInstanceKey1 =
        testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "order-123"));
    final long workflowInstanceKey2 =
        testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "order-123"));

    // when
    testClient.publishMessage("message", "order-123");

    // then
    final List<Record<WorkflowInstanceRecordValue>> events =
        testClient
            .receiveWorkflowInstances()
            .withIntent(WorkflowInstanceIntent.EVENT_TRIGGERED)
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
    testClient.deploy(TWO_MESSAGES_WORKFLOW);

    testClient.publishMessage("ping", "123", asMsgPack("nr", 1));
    testClient.publishMessage("ping", "123", asMsgPack("nr", 2));

    // when
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "123"));

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_TRIGGERED)
                .filter(r -> r.getValue().getElementId().startsWith("message"))
                .limit(2)
                .asList())
        .extracting(
            r -> tuple(r.getValue().getElementId(), r.getValue().getPayloadAsMap().get("nr")))
        .contains(tuple("message1", 1), tuple("message2", 2));
  }

  @Test
  public void shouldCorrelateMessageOnlyOnceIfEnteredBefore() {
    // given
    testClient.deploy(TWO_MESSAGES_WORKFLOW);

    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "123"));

    // when
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    testClient.publishMessage("ping", "123", asMsgPack("nr", 1));

    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .limit(2)
                .count())
        .isEqualTo(2);

    testClient.publishMessage("ping", "123", asMsgPack("nr", 2));

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_TRIGGERED)
                .filter(r -> r.getValue().getElementId().startsWith("message"))
                .limit(2)
                .asList())
        .extracting(
            r -> tuple(r.getValue().getElementId(), r.getValue().getPayloadAsMap().get("nr")))
        .contains(tuple("message1", 1), tuple("message2", 2));
  }

  @Test
  public void shouldCorrelateMessageOnlyOnceToInstance() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway()
            .intermediateCatchEvent("message1")
            .message(m -> m.name("ping").zeebeCorrelationKey("$.key"))
            .moveToLastGateway()
            .intermediateCatchEvent("message2")
            .message(m -> m.name("ping").zeebeCorrelationKey("$.key"))
            .done());

    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "123"));

    // when
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .limit(2)
                .count())
        .isEqualTo(2);

    testClient.publishMessage("ping", "123", asMsgPack("nr", 1));
    testClient.publishMessage("ping", "123", asMsgPack("nr", 2));

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_TRIGGERED)
                .filter(r -> r.getValue().getElementId().startsWith("message"))
                .limit(2)
                .asList())
        .extracting(r -> r.getValue().getPayloadAsMap().get("nr"))
        .contains(1, 2);
  }

  @Test
  public void shouldCorrelateOnlyOneMessagePerCatchElement() {
    // given
    testClient.deploy(TWO_MESSAGES_WORKFLOW);

    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "123"));

    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.OPENED)
                .exists())
        .isTrue();

    // when
    testClient.publishMessage("ping", "123", asMsgPack("nr", 1));
    testClient.publishMessage("ping", "123", asMsgPack("nr", 2));

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_TRIGGERED)
                .filter(r -> r.getValue().getElementId().startsWith("message"))
                .limit(2)
                .asList())
        .extracting(
            r -> tuple(r.getValue().getElementId(), r.getValue().getPayloadAsMap().get("nr")))
        .contains(tuple("message1", 1), tuple("message2", 2));
  }

  @Test
  public void shouldCorrelateCorrectBoundaryEvent() {
    // given
    testClient.deploy(BOUNDARY_EVENTS_WORKFLOW);
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "123"));

    // when
    awaitSubscriptionsOpened(3);
    testClient.publishMessage("msg1", "123", asMsgPack("foo", 1));

    // then
    assertThat(RecordingExporter.workflowInstanceRecords().limitToWorkflowInstanceCompleted())
        .filteredOn(r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.EVENT_ACTIVATED)
        .extracting(Record::getValue)
        .extracting(WorkflowInstanceRecordValue::getElementId)
        .contains("msg1End")
        .doesNotContain("taskEnd", "msg2End");
  }

  @Test
  public void shouldNotTriggerBoundaryEventIfReceiveTaskTriggeredFirst() {
    // given
    testClient.deploy(BOUNDARY_EVENTS_WORKFLOW);
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "123"));

    // when
    awaitSubscriptionsOpened(3);
    testClient.publishMessage("taskMsg", "123", asMsgPack("foo", 1));

    // then
    assertThat(RecordingExporter.workflowInstanceRecords().limitToWorkflowInstanceCompleted())
        .filteredOn(r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.EVENT_ACTIVATED)
        .extracting(Record::getValue)
        .extracting(WorkflowInstanceRecordValue::getElementId)
        .contains("taskEnd")
        .doesNotContain("msg1End", "msg2End");
  }

  @Test
  public void shouldNotTriggerReceiveTaskIfBoundaryEventTriggeredFirst() {
    // given
    testClient.deploy(BOUNDARY_EVENTS_WORKFLOW);
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "123"));

    // when
    awaitSubscriptionsOpened(3); // await both subscriptions opened
    testClient.publishMessage("msg2", "123", asMsgPack("foo", 1));

    // then
    assertThat(RecordingExporter.workflowInstanceRecords().limitToWorkflowInstanceCompleted())
        .filteredOn(r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.EVENT_ACTIVATED)
        .extracting(Record::getValue)
        .extracting(WorkflowInstanceRecordValue::getElementId)
        .contains("msg2End")
        .doesNotContain("taskEnd", "msg1End");
  }

  @Test
  public void testIntermediateMessageEventLifeCycle() {
    // given
    testClient.deploy(SINGLE_MESSAGE_WORKFLOW);
    testClient.publishMessage("message", "order-123");
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "order-123"));

    final List<Record<WorkflowInstanceRecordValue>> events =
        testClient
            .receiveWorkflowInstances()
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(events)
        .filteredOn(r -> r.getValue().getElementId().equals("receive-message"))
        .extracting(Record::getMetadata)
        .extracting(RecordMetadata::getIntent)
        .containsExactly(
            WorkflowInstanceIntent.EVENT_ACTIVATING,
            WorkflowInstanceIntent.EVENT_ACTIVATED,
            WorkflowInstanceIntent.EVENT_OCCURRED,
            WorkflowInstanceIntent.EVENT_TRIGGERING,
            WorkflowInstanceIntent.EVENT_TRIGGERED);
  }

  @Test
  public void testReceiveTaskLifeCycle() {
    // given
    testClient.deploy(RECEIVE_TASK_WORKFLOW);
    testClient.publishMessage("message", "order-123");
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "order-123"));

    final List<Record<WorkflowInstanceRecordValue>> events =
        testClient
            .receiveWorkflowInstances()
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(events)
        .filteredOn(r -> r.getValue().getElementId().equals("receive-message"))
        .extracting(Record::getMetadata)
        .extracting(RecordMetadata::getIntent)
        .containsExactly(
            WorkflowInstanceIntent.ELEMENT_READY,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.EVENT_OCCURRED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void testBoundaryMessageEventLifecycle() {
    // given
    testClient.deploy(BOUNDARY_EVENTS_WORKFLOW);
    testClient.publishMessage("msg1", "order-123");
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "order-123"));

    final List<Record<WorkflowInstanceRecordValue>> events =
        testClient
            .receiveWorkflowInstances()
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(events)
        .extracting(r -> tuple(r.getValue().getElementId(), r.getMetadata().getIntent()))
        .containsSequence(
            tuple("task", WorkflowInstanceIntent.ELEMENT_READY),
            tuple("task", WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple("msg1", WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple("task", WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("task", WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple("msg1", WorkflowInstanceIntent.EVENT_TRIGGERING),
            tuple("msg1", WorkflowInstanceIntent.EVENT_TRIGGERED));
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
            .message(m -> m.name("msg1").zeebeCorrelationKey("$.key"))
            .endEvent("msg1End")
            .moveToActivity("task")
            .endEvent("taskEnd")
            .done();
    testClient.deploy(workflow);
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "123"));

    // when
    testClient.publishMessage("msg1", "123", asMsgPack("foo", "0"));
    testClient.publishMessage("msg1", "123", asMsgPack("foo", "1"));
    testClient.publishMessage("msg1", "123", asMsgPack("foo", "2"));
    assertThat(awaitMessagesCorrelated(3)).hasSize(3);

    // then
    final List<Record<WorkflowInstanceRecordValue>> msgEndEvents =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.EVENT_ACTIVATED)
            .withElementId("msg1End")
            .limit(3)
            .asList();

    assertThat(msgEndEvents)
        .extracting(e -> e.getValue().getPayloadAsMap().get("foo"))
        .contains("0", "1", "2");
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
    return testClient
        .receiveWorkflowInstanceSubscriptions()
        .withIntent(WorkflowInstanceSubscriptionIntent.OPENED)
        .limit(subscriptionsCount)
        .asList();
  }
}
