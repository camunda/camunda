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
import static io.zeebe.broker.workflow.WorkflowAssert.assertWorkflowInstanceRecord;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
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

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "order-123"));

    assertThat(
            RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.OPENED).exists())
        .isTrue();

    // when
    testClient.publishMessage("message", "order-123", asMsgPack("foo", "bar"));

    // then
    final Record<WorkflowInstanceRecordValue> event =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_COMPLETED);

    assertWorkflowInstanceRecord(workflowInstanceKey, "receive-message", event);
    assertWorkflowInstancePayload(event, "{'key':'order-123', 'foo':'bar'}");
  }

  @Test
  public void shouldCorrelateMessageIfPublishedBefore() {
    // given
    testClient.deploy(SINGLE_MESSAGE_WORKFLOW);

    testClient.publishMessage("message", "order-123", asMsgPack("foo", "bar"));

    // when
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "order-123"));

    // then
    final Record<WorkflowInstanceRecordValue> event =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_COMPLETED);
    assertWorkflowInstanceRecord(workflowInstanceKey, "receive-message", event);
    assertWorkflowInstancePayload(event, "{'key':'order-123', 'foo':'bar'}");
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
        testClient.receiveElementInState(
            "receive-message", WorkflowInstanceIntent.ELEMENT_COMPLETED);

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
        testClient.receiveElementInState(
            "receive-message", WorkflowInstanceIntent.ELEMENT_COMPLETED);

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
    testClient.deploy(TWO_MESSAGES_WORKFLOW);

    testClient.publishMessage("ping", "123", asMsgPack("nr", 1));
    testClient.publishMessage("ping", "123", asMsgPack("nr", 2));

    // when
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "123"));

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
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
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
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
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
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
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .limit(2)
                .asList())
        .extracting(
            r -> tuple(r.getValue().getElementId(), r.getValue().getPayloadAsMap().get("nr")))
        .contains(tuple("message1", 1), tuple("message2", 2));
  }
}
