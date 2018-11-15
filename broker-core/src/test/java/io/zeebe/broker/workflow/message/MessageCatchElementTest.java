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

import static io.zeebe.broker.test.EmbeddedBrokerConfigurator.setPartitionCount;
import static io.zeebe.broker.workflow.WorkflowAssert.assertMessageSubscription;
import static io.zeebe.broker.workflow.WorkflowAssert.assertWorkflowInstanceRecord;
import static io.zeebe.broker.workflow.WorkflowAssert.assertWorkflowSubscription;
import static io.zeebe.broker.workflow.gateway.ParallelGatewayStreamProcessorTest.PROCESS_ID;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Assertions;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.RecordMetadata;
import io.zeebe.exporter.record.value.MessageSubscriptionRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceSubscriptionRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.record.RecordingExporter;
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
public class MessageCatchElementTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule(setPartitionCount(3));

  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private static final BpmnModelInstance CATCH_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("receive-message")
          .message(m -> m.name("order canceled").zeebeCorrelationKey("$.orderId"))
          .sequenceFlowId("to-end")
          .endEvent()
          .done();

  private static final BpmnModelInstance RECEIVE_TASK_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
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

  private PartitionTestClient testClient;

  @Before
  public void init() {
    apiRule.waitForPartition(3);
    testClient = apiRule.partitionClient();
    final long deploymentKey = testClient.deploy(workflow);

    testClient.receiveFirstDeploymentEvent(DeploymentIntent.CREATED, deploymentKey);
    apiRule.partitionClient(1).receiveFirstDeploymentEvent(DeploymentIntent.CREATED, deploymentKey);
    apiRule.partitionClient(2).receiveFirstDeploymentEvent(DeploymentIntent.CREATED, deploymentKey);
  }

  @Test
  public void testWorkflowInstanceLifeCycle() {

    testClient.publishMessage("order canceled", "order-123");

    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("orderId", "order-123"));

    final List<Record<WorkflowInstanceRecordValue>> events =
        testClient.receiveWorkflowInstances().limit(10).collect(Collectors.toList());

    assertThat(events)
        .extracting(Record::getMetadata)
        .extracting(RecordMetadata::getIntent)
        .containsExactly(
            WorkflowInstanceIntent.CREATE,
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
        testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("orderId", "order-123"));

    final Record<WorkflowInstanceRecordValue> event =
        testClient.receiveElementInState(
            "receive-message", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    assertWorkflowInstanceRecord(workflowInstanceKey, "receive-message", event);
  }

  @Test
  public void shouldOpenMessageSubscription() {

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("orderId", "order-123"));

    final Record<WorkflowInstanceRecordValue> catchEventEntered =
        testClient.receiveElementInState(
            "receive-message", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final Record<MessageSubscriptionRecordValue> messageSubscription =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.OPENED).getFirst();
    assertThat(messageSubscription.getMetadata().getValueType())
        .isEqualTo(ValueType.MESSAGE_SUBSCRIPTION);
    assertThat(messageSubscription.getMetadata().getRecordType()).isEqualTo(RecordType.EVENT);

    assertMessageSubscription(
        workflowInstanceKey, "order-123", catchEventEntered, messageSubscription);
  }

  @Test
  public void shouldOpenWorkflowInstanceSubscription() {
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("orderId", "order-123"));

    final Record<WorkflowInstanceRecordValue> catchEventEntered =
        testClient.receiveElementInState(
            "receive-message", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    final Record<WorkflowInstanceSubscriptionRecordValue> workflowInstanceSubscription =
        testClient
            .receiveWorkflowInstanceSubscriptions()
            .withIntent(WorkflowInstanceSubscriptionIntent.OPENED)
            .getFirst();

    assertThat(workflowInstanceSubscription.getMetadata().getValueType())
        .isEqualTo(ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION);
    assertThat(workflowInstanceSubscription.getMetadata().getRecordType())
        .isEqualTo(RecordType.EVENT);

    assertWorkflowSubscription(
        workflowInstanceKey, catchEventEntered, workflowInstanceSubscription);
  }

  @Test
  public void shouldCorrelateWorkflowInstanceSubscription() {
    // given
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("orderId", "order-123"));

    final Record<WorkflowInstanceRecordValue> catchEventEntered =
        testClient.receiveElementInState(
            "receive-message", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    final DirectBuffer messagePayload = asMsgPack("foo", "bar");
    testClient.publishMessage("order canceled", "order-123", messagePayload);

    // then
    final Record<WorkflowInstanceSubscriptionRecordValue> subscription =
        testClient
            .receiveWorkflowInstanceSubscriptions()
            .withIntent(WorkflowInstanceSubscriptionIntent.CORRELATED)
            .getFirst();

    assertThat(subscription.getMetadata().getValueType())
        .isEqualTo(ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION);
    assertThat(subscription.getMetadata().getRecordType()).isEqualTo(RecordType.EVENT);

    assertWorkflowSubscription(
        workflowInstanceKey, "{\"foo\":\"bar\"}", catchEventEntered, subscription);
  }

  @Test
  public void shouldCorrelateMessageSubscription() {
    // given
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("orderId", "order-123"));

    final Record<WorkflowInstanceRecordValue> catchEventEntered =
        testClient.receiveElementInState(
            "receive-message", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when
    testClient.publishMessage("order canceled", "order-123", asMsgPack("foo", "bar"));

    // then
    final Record<MessageSubscriptionRecordValue> subscription =
        testClient
            .receiveMessageSubscriptions()
            .withIntent(MessageSubscriptionIntent.CORRELATED)
            .getFirst();

    assertThat(subscription.getMetadata().getValueType()).isEqualTo(ValueType.MESSAGE_SUBSCRIPTION);
    assertThat(subscription.getMetadata().getRecordType()).isEqualTo(RecordType.EVENT);

    assertMessageSubscription(workflowInstanceKey, catchEventEntered, subscription);
  }

  @Test
  public void shouldCloseMessageSubscription() {

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("orderId", "order-123"));

    final Record<WorkflowInstanceRecordValue> catchEventEntered =
        testClient.receiveElementInState(
            "receive-message", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    testClient.cancelWorkflowInstance(workflowInstanceKey);

    final Record<MessageSubscriptionRecordValue> messageSubscription =
        RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.CLOSED).getFirst();

    assertThat(messageSubscription.getMetadata().getRecordType()).isEqualTo(RecordType.EVENT);

    Assertions.assertThat(messageSubscription.getValue())
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName("order canceled")
        .hasCorrelationKey("");
  }

  @Test
  public void shouldCloseWorkflowInstanceSubscription() {

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("orderId", "order-123"));

    final Record<WorkflowInstanceRecordValue> catchEventEntered =
        testClient.receiveElementInState(
            "receive-message", WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    testClient.cancelWorkflowInstance(workflowInstanceKey);

    final Record<WorkflowInstanceSubscriptionRecordValue> subscription =
        RecordingExporter.workflowInstanceSubscriptionRecords(
                WorkflowInstanceSubscriptionIntent.CLOSED)
            .getFirst();

    assertThat(subscription.getMetadata().getRecordType()).isEqualTo(RecordType.EVENT);

    Assertions.assertThat(subscription.getValue())
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName("order canceled");
  }

  @Test
  public void shouldCorrelateMessageAndContinue() {
    // given
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("orderId", "order-123"));

    // when
    testClient.publishMessage("order canceled", "order-123");

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withElementId("receive-message")
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
                .withElementId("to-end")
                .exists())
        .isTrue();
  }
}
