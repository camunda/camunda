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
import io.zeebe.msgpack.spec.MsgPackHelper;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.SubscriptionUtil;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.ExecuteCommandResponse;
import io.zeebe.test.broker.protocol.clientapi.SubscribedRecord;
import io.zeebe.test.broker.protocol.clientapi.TestTopicClient;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MessageCorrelationTest {

  public EmbeddedBrokerRule brokerRule =
      new EmbeddedBrokerRule("zeebe.unit-test.increased.partitions.cfg.toml");

  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private static final BpmnModelInstance CATCH_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess("wf")
          .startEvent()
          .intermediateCatchEvent("receive-message")
          .message(m -> m.name("order canceled").zeebeCorrelationKey("$.orderId"))
          .sequenceFlowId("to-end")
          .endEvent()
          .done();

  private static final BpmnModelInstance RECEIVE_TASK_WORKFLOW =
      Bpmn.createExecutableProcess("wf")
          .startEvent()
          .receiveTask("receive-message")
          .message(m -> m.name("order canceled").zeebeCorrelationKey("$.orderId"))
          .sequenceFlowId("to-end")
          .endEvent()
          .done();

  @Parameter(0)
  public String elementType;

  @Parameter(1)
  public BpmnModelInstance workflow;

  @Parameters(name = "{0}")
  public static final Object[][] parameters() {
    return new Object[][] {
      {"intermediate message catch event", CATCH_EVENT_WORKFLOW},
      {"receive task", RECEIVE_TASK_WORKFLOW}
    };
  }

  private TestTopicClient testClient;

  @Before
  public void init() {
    apiRule.waitForTopic(3);
    testClient = apiRule.topic();
    final long deploymentKey = testClient.deploy(workflow);

    testClient.receiveFirstDeploymentEvent(DeploymentIntent.CREATED, deploymentKey);
    apiRule.topic(1).receiveFirstDeploymentEvent(DeploymentIntent.CREATED, deploymentKey);
    apiRule.topic(2).receiveFirstDeploymentEvent(DeploymentIntent.CREATED, deploymentKey);
  }

  @Test
  public void testWorkflowInstanceLifeCycle() {

    testClient.publishMessage("order canceled", "order-123");

    testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));

    final List<SubscribedRecord> events =
        testClient.receiveRecords().ofTypeWorkflowInstance().limit(11).collect(Collectors.toList());

    assertThat(events)
        .extracting(SubscribedRecord::intent)
        .containsExactly(
            WorkflowInstanceIntent.CREATE,
            WorkflowInstanceIntent.CREATED,
            WorkflowInstanceIntent.ELEMENT_READY,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.START_EVENT_OCCURRED,
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
            WorkflowInstanceIntent.ELEMENT_READY,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED,
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN);
  }

  @Test
  public void shouldActivateElement() {

    final long workflowInstanceKey =
        testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));

    final SubscribedRecord event =
        testClient.receiveElementInState(
            "receive-message", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    assertThat(event.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "wf")
        .containsEntry(PROP_WORKFLOW_VERSION, 1L)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "receive-message");
  }

  @Test
  public void shouldOpenMessageSubscription() {

    final long workflowInstanceKey =
        testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));

    final SubscribedRecord catchEventEntered =
        testClient.receiveElementInState(
            "receive-message", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

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
  public void shouldOpenMessageSubscriptionsOnSamePartition() {
    // given
    final List<Integer> partitionIds = apiRule.getPartitionIds();

    final String correlationKey = "order-123";

    final TestTopicClient workflowPartition = apiRule.topic(partitionIds.get(0));
    final TestTopicClient subscriptionPartition = apiRule.topic(getPartitionId(correlationKey));

    testClient.deploy(CATCH_EVENT_WORKFLOW);

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
  public void shouldOpenWorkflowInstanceSubscription() {
    final long workflowInstanceKey =
        testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));

    final SubscribedRecord catchEventEntered =
        testClient.receiveElementInState(
            "receive-message", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final SubscribedRecord workflowInstanceSubscription =
        testClient
            .receiveEvents()
            .filter(intent(WorkflowInstanceSubscriptionIntent.OPENED))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no workflow instance subscription event found"));

    assertThat(workflowInstanceSubscription.valueType())
        .isEqualTo(ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION);
    assertThat(workflowInstanceSubscription.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(workflowInstanceSubscription.value())
        .containsExactly(
            entry("subscriptionPartitionId", (long) getPartitionId("order-123")),
            entry("workflowInstanceKey", workflowInstanceKey),
            entry("activityInstanceKey", catchEventEntered.key()),
            entry("messageName", "order canceled"),
            entry("payload", MsgPackHelper.EMTPY_OBJECT));
  }

  @Test
  public void shouldCorrelateMessageIfEnteredBefore() throws Exception {
    // given
    final long workflowInstanceKey =
        testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));

    testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    testClient.publishMessage("order canceled", "order-123", asMsgPack("foo", "bar"));

    // then
    final SubscribedRecord event =
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_COMPLETED);

    assertThat(event.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "wf")
        .containsEntry(PROP_WORKFLOW_VERSION, 1L)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "receive-message");

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
        testClient.receiveFirstWorkflowInstanceEvent(WorkflowInstanceIntent.ELEMENT_COMPLETED);

    assertThat(event.value())
        .containsEntry(PROP_WORKFLOW_BPMN_PROCESS_ID, "wf")
        .containsEntry(PROP_WORKFLOW_VERSION, 1L)
        .containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey)
        .containsEntry(PROP_WORKFLOW_ACTIVITY_ID, "receive-message");

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
  public void shouldCorrelateMessageWithZeroTTL() throws Exception {
    // given
    final long workflowInstanceKey =
        testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));

    testClient.receiveElementInState("receive-message", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    testClient.publishMessage("order canceled", "order-123", asMsgPack("foo", "bar"), 0);

    // then
    final SubscribedRecord event =
        testClient.receiveElementInState(
            "receive-message", WorkflowInstanceIntent.ELEMENT_COMPLETED);

    assertThat(event.value()).containsEntry(PROP_WORKFLOW_INSTANCE_KEY, workflowInstanceKey);
  }

  @Test
  public void shouldNotCorrelateMessageAfterTTL() throws Exception {
    // given
    testClient.publishMessage("order canceled", "order-123", asMsgPack("nr", "first"), 0);
    testClient.publishMessage("order canceled", "order-123", asMsgPack("nr", "second"), 10_000);

    // when
    testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));

    // then
    final SubscribedRecord event =
        testClient.receiveElementInState(
            "receive-message", WorkflowInstanceIntent.ELEMENT_COMPLETED);

    final byte[] payload = (byte[]) event.value().get("payload");
    assertThat(MSGPACK_MAPPER.readTree(payload))
        .isEqualTo(JSON_MAPPER.readTree("{'orderId':'order-123', 'nr':'second'}"));
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
            workflowInstanceKey1, WorkflowInstanceIntent.ELEMENT_COMPLETED);
    final byte[] payload1 = (byte[]) catchEventOccurred1.value().get("payload");
    assertThat(MSGPACK_MAPPER.readTree(payload1))
        .isEqualTo(JSON_MAPPER.readTree("{'orderId':'order-123', 'foo':'bar'}"));

    final SubscribedRecord catchEventOccurred2 =
        testClient.receiveFirstWorkflowInstanceEvent(
            workflowInstanceKey2, WorkflowInstanceIntent.ELEMENT_COMPLETED);
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
            .filter(intent(WorkflowInstanceIntent.ELEMENT_COMPLETED))
            .filter(r -> "receive-message".equals(r.value().get("activityId")))
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
        testClient.receiveElementInState(
            "receive-message", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final DirectBuffer messagePayload = asMsgPack("foo", "bar");
    final ExecuteCommandResponse resp =
        testClient.publishMessage("order canceled", "order-123", messagePayload);
    final int messagePartitionId = resp.partitionId();

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
            entry("subscriptionPartitionId", (long) messagePartitionId),
            entry("workflowInstanceKey", workflowInstanceKey),
            entry("activityInstanceKey", catchEventEntered.key()),
            entry("messageName", "order canceled"),
            entry("payload", messagePayload.byteArray()));
  }

  @Test
  public void shouldCorrelateMessageSubscription() {
    // given
    final long workflowInstanceKey =
        testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));

    final SubscribedRecord catchEventEntered =
        testClient.receiveElementInState(
            "receive-message", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    testClient.publishMessage("order canceled", "order-123", asMsgPack("foo", "bar"));

    // then
    final SubscribedRecord subscription =
        testClient
            .receiveEvents()
            .filter(intent(MessageSubscriptionIntent.CORRELATED))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no subscription event found"));

    assertThat(subscription.valueType()).isEqualTo(ValueType.MESSAGE_SUBSCRIPTION);
    assertThat(subscription.recordType()).isEqualTo(RecordType.EVENT);
    assertThat(subscription.value())
        .containsExactly(
            entry("workflowInstancePartitionId", (long) catchEventEntered.partitionId()),
            entry("workflowInstanceKey", workflowInstanceKey),
            entry("activityInstanceKey", catchEventEntered.key()),
            entry("messageName", "order canceled"),
            entry("correlationKey", "order-123"));
  }

  @Test
  public void shouldRejectCorrelateCommand() throws Exception {
    // given
    final long workflowInstanceKey =
        testClient.createWorkflowInstance("wf", asMsgPack("orderId", "order-123"));

    final SubscribedRecord catchEventEntered =
        testClient.receiveElementInState(
            "receive-message", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    apiRule
        .createCmdRequest()
        .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CANCEL)
        .key(workflowInstanceKey)
        .command()
        .done()
        .send();

    testClient.receiveElementInState("wf", WorkflowInstanceIntent.ELEMENT_TERMINATED);

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
      final TestTopicClient client, final MessageSubscriptionIntent intent) throws AssertionError {
    return client
        .receiveEvents()
        .filter(intent(intent))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no message subscription event found"));
  }

  private int getPartitionId(final String correlationKey) {
    final List<Integer> partitionIds = apiRule.getPartitionIds();
    final int index =
        Math.abs(SubscriptionUtil.getSubscriptionHashCode(correlationKey) % partitionIds.size());
    return partitionIds.get(index);
  }
}
