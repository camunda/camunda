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
package io.zeebe.broker.workflow;

import static io.zeebe.broker.test.MsgPackUtil.JSON_MAPPER;
import static io.zeebe.broker.test.MsgPackUtil.MSGPACK_MAPPER;
import static io.zeebe.broker.workflow.data.WorkflowInstanceRecord.PROP_WORKFLOW_ACTIVITY_ID;
import static io.zeebe.broker.workflow.data.WorkflowInstanceRecord.PROP_WORKFLOW_BPMN_PROCESS_ID;
import static io.zeebe.broker.workflow.data.WorkflowInstanceRecord.PROP_WORKFLOW_INSTANCE_KEY;
import static io.zeebe.broker.workflow.data.WorkflowInstanceRecord.PROP_WORKFLOW_VERSION;
import static io.zeebe.test.broker.protocol.clientapi.TestTopicClient.intent;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.SubscriptionUtil;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class IntermediateMessageCatchEventTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule();

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("wf")
          .startEvent()
          .intermediateCatchEvent("catch-event")
          .message(m -> m.name("order canceled").zeebeCorrelationKey("$.orderId"))
          .sequenceFlowId("to-end")
          .endEvent()
          .done();

  private TestTopicClient testClient;

  @Before
  public void init() {
    testClient = apiRule.topic();
    testClient.deploy(WORKFLOW);
  }

  @Test
  public void shouldEnterIntermediateCatchEvent() {

    final long workflowInstanceKey =
        testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));

    final SubscribedRecord event =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.CATCH_EVENT_ENTERED);

    assertThat(event.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "wf")
        .containsEntry(PROP_WORKFLOW_VERSION, 1L)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "catch-event");
  }

  @Test
  public void testWorkflowInstanceLifeCycle() {

    testClient.publishMessage("order canceled", "order-123");

    testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));

    final List<SubscribedRecord> events =
        testClient.receiveRecords().ofTypeWorkflowInstance().limit(9).collect(Collectors.toList());

    assertThat(events)
        .extracting(SubscribedRecord::intent)
        .containsExactly(
            WorkflowInstanceIntent.CREATE,
            WorkflowInstanceIntent.CREATED,
            WorkflowInstanceIntent.START_EVENT_OCCURRED,
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
            WorkflowInstanceIntent.CATCH_EVENT_ENTERING,
            WorkflowInstanceIntent.CATCH_EVENT_ENTERED,
            WorkflowInstanceIntent.CATCH_EVENT_OCCURRING,
            WorkflowInstanceIntent.CATCH_EVENT_OCCURRED,
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN);
  }

  @Test
  public void shouldOpenMessageSubscription() {

    final long workflowInstanceKey =
        testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));

    final SubscribedRecord catchEventEntered =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.CATCH_EVENT_ENTERED);

    final SubscribedRecord messageSubscription =
        findMessageSubscription(testClient, MessageSubscriptionIntent.OPENED);
    assertThat(messageSubscription.valueType()).isEqualTo(ValueType.MESSAGE_SUBSCRIPTION);
    assertThat(messageSubscription.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(messageSubscription.value())
        .containsExactly(
            entry("workflowInstancePartitionId", (long) catchEventEntered.partitionId()),
            entry("workflowInstanceKey", workflowInstanceKey),
            entry("activityInstanceKey", catchEventEntered.key()),
            entry("messageName", "order canceled"),
            entry("correlationKey", "order-123"));
  }

  @Test
  public void shouldOpenMessageSubscriptionsOnDifferentPartitions() {
    // given
    apiRule.createTopic("test", 5);
    final List<Integer> partitionIds = apiRule.getPartitionIds("test");

    final String correlationKey1 = "order-123";
    final String correlationKey2 = "order-456";
    assertThat(getPartitionId(partitionIds, correlationKey1))
        .isNotEqualTo(getPartitionId(partitionIds, correlationKey2));

    final TestTopicClient workflowPartition = apiRule.topic(partitionIds.get(0));
    final TestTopicClient subscriptionPartition1 =
        apiRule.topic(getPartitionId(partitionIds, correlationKey1));
    final TestTopicClient subscriptionPartition2 =
        apiRule.topic(getPartitionId(partitionIds, correlationKey2));

    testClient.deploy("test", WORKFLOW);

    // when
    final long workflowInstanceKey1 =
        workflowPartition.createWorkflowInstance("wf", asMsgPack("orderId", correlationKey1));

    final long workflowInstanceKey2 =
        workflowPartition.createWorkflowInstance("wf", asMsgPack("orderId", correlationKey2));

    // then
    assertThat(
            findMessageSubscription(subscriptionPartition1, MessageSubscriptionIntent.OPENED)
                .value())
        .contains(entry("workflowInstanceKey", workflowInstanceKey1));

    assertThat(
            findMessageSubscription(subscriptionPartition2, MessageSubscriptionIntent.OPENED)
                .value())
        .contains(entry("workflowInstanceKey", workflowInstanceKey2));
  }

  @Test
  public void shouldOpenMessageSubscriptionsOnSamePartition() {
    // given
    apiRule.createTopic("test", 5);
    final List<Integer> partitionIds = apiRule.getPartitionIds("test");

    final String correlationKey = "order-123";

    final TestTopicClient workflowPartition = apiRule.topic(partitionIds.get(0));
    final TestTopicClient subscriptionPartition =
        apiRule.topic(getPartitionId(partitionIds, correlationKey));

    testClient.deploy("test", WORKFLOW);

    // when
    final long workflowInstanceKey1 =
        workflowPartition.createWorkflowInstance("wf", asMsgPack("orderId", correlationKey));

    final long workflowInstanceKey2 =
        workflowPartition.createWorkflowInstance("wf", asMsgPack("orderId", correlationKey));

    // then
    final List<SubscribedRecord> subscriptions =
        subscriptionPartition
            .receiveEvents()
            .filter(intent(MessageSubscriptionIntent.OPENED))
            .limit(2)
            .collect(Collectors.toList());

    assertThat(subscriptions)
        .extracting(s -> s.value().get("workflowInstanceKey"))
        .contains(workflowInstanceKey1, workflowInstanceKey2);
  }

  @Test
  public void shouldCorrelateMessageIfEnteredBefore() throws Exception {
    // given
    final long workflowInstanceKey =
        testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));

    testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.CATCH_EVENT_ENTERED);

    // when
    testClient.publishMessage("order canceled", "order-123", asMsgPack("foo", "bar"));

    // then
    final SubscribedRecord event =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.CATCH_EVENT_OCCURRED);

    assertThat(event.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "wf")
        .containsEntry(PROP_WORKFLOW_VERSION, 1L)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "catch-event");

    final byte[] payload = (byte[]) event.value().get("payload");
    assertThat(MSGPACK_MAPPER.readTree(payload))
        .isEqualTo(JSON_MAPPER.readTree("{'orderId':'order-123', 'foo':'bar'}"));
  }

  @Test
  public void shouldCorrelateMessageIfPublishedBefore() throws Exception {
    // given
    testClient.publishMessage("order canceled", "order-123", asMsgPack("foo", "bar"));

    // when
    final long workflowInstanceKey =
        testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));

    // then
    final SubscribedRecord event =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.CATCH_EVENT_OCCURRED);

    assertThat(event.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "wf")
        .containsEntry(PROP_WORKFLOW_VERSION, 1L)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "catch-event");

    final byte[] payload = (byte[]) event.value().get("payload");
    assertThat(MSGPACK_MAPPER.readTree(payload))
        .isEqualTo(JSON_MAPPER.readTree("{'orderId':'order-123', 'foo':'bar'}"));
  }

  @Test
  public void shouldContinueInstanceAfteMessageIsCorrelated() {
    // given
    final long workflowInstanceKey =
        testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));

    // when
    testClient.publishMessage("order canceled", "order-123");

    // then
    final SubscribedRecord event =
        testClient.receiveFirstWorkflowInstanceEvent(
            workflowInstanceKey, "to-end", WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN);

    assertThat(event.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "wf")
        .containsEntry(PROP_WORKFLOW_VERSION, 1L)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "to-end");
  }

  @Test
  public void shouldCorrelateMessageByCorrelationKey() throws Exception {
    // given
    final long workflowInstanceKey1 =
        testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));
    final long workflowInstanceKey2 =
        testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-456"));

    // when
    testClient.publishMessage("order canceled", "order-123", asMsgPack("foo", "bar"));
    testClient.publishMessage("order canceled", "order-456", asMsgPack("foo", "baz"));

    // then
    final SubscribedRecord catchEventOccurred1 =
        testClient.receiveFirstWorkflowInstanceEvent(
            workflowInstanceKey1, WorkflowInstanceIntent.CATCH_EVENT_OCCURRED);
    final byte[] payload1 = (byte[]) catchEventOccurred1.value().get("payload");
    assertThat(MSGPACK_MAPPER.readTree(payload1))
        .isEqualTo(JSON_MAPPER.readTree("{'orderId':'order-123', 'foo':'bar'}"));

    final SubscribedRecord catchEventOccurred2 =
        testClient.receiveFirstWorkflowInstanceEvent(
            workflowInstanceKey2, WorkflowInstanceIntent.CATCH_EVENT_OCCURRED);
    final byte[] payload2 = (byte[]) catchEventOccurred2.value().get("payload");
    assertThat(MSGPACK_MAPPER.readTree(payload2))
        .isEqualTo(JSON_MAPPER.readTree("{'orderId':'order-456', 'foo':'baz'}"));
  }

  @Test
  public void shouldCorrelateMessageToAllSubscriptions() {
    // given
    final long workflowInstanceKey1 =
        testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));
    final long workflowInstanceKey2 =
        testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));

    // when
    testClient.publishMessage("order canceled", "order-123");

    // then
    final List<SubscribedRecord> events =
        testClient
            .receiveEvents()
            .filter(intent(WorkflowInstanceIntent.CATCH_EVENT_OCCURRED))
            .limit(2)
            .collect(Collectors.toList());

    assertThat(events)
        .extracting(r -> r.value().get("workflowInstanceKey"))
        .contains(workflowInstanceKey1, workflowInstanceKey2);
  }

  @Test
  public void shouldCorrelateWorkflowInstanceSubscription() {
    // given
    final long workflowInstanceKey =
        testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));

    final SubscribedRecord catchEventEntered =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.CATCH_EVENT_ENTERED);

    // when
    final DirectBuffer messagePayload = asMsgPack("foo", "bar");
    testClient.publishMessage("order canceled", "order-123", messagePayload);

    // then
    final SubscribedRecord subscription =
        testClient
            .receiveEvents()
            .filter(intent(WorkflowInstanceSubscriptionIntent.CORRELATED))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no subscription event found"));

    assertThat(subscription.valueType()).isEqualTo(ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION);
    assertThat(subscription.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(subscription.value())
        .containsExactly(
            entry("workflowInstanceKey", workflowInstanceKey),
            entry("activityInstanceKey", catchEventEntered.key()),
            entry("messageName", "order canceled"),
            entry("payload", messagePayload.byteArray()));
  }

  @Test
  public void shouldRejectCorrelateCommand() throws Exception {
    // given
    final long workflowInstanceKey =
        testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));

    final SubscribedRecord catchEventEntered =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.CATCH_EVENT_ENTERED);

    // when
    apiRule
        .createCmdRequest()
        .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CANCEL)
        .key(workflowInstanceKey)
        .command()
        .done()
        .send();

    testClient.publishMessage("order canceled", "order-123");

    // then
    final SubscribedRecord rejection =
        testClient
            .receiveRejections()
            .filter(intent(WorkflowInstanceSubscriptionIntent.CORRELATE))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no rejection found"));

    assertThat(rejection.value())
        .containsEntry("workflowInstanceKey", workflowInstanceKey)
        .containsEntry("activityInstanceKey", catchEventEntered.key());
  }

  private SubscribedRecord findMessageSubscription(
      TestTopicClient client, MessageSubscriptionIntent intent) throws AssertionError {
    return client
        .receiveEvents()
        .filter(intent(intent))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no message subscription event found"));
  }

  private int getPartitionId(List<Integer> partitionIds, String correlationKey) {
    final int index =
        Math.abs(SubscriptionUtil.getSubscriptionHashCode(correlationKey) % partitionIds.size());
    return partitionIds.get(index);
  }
}
