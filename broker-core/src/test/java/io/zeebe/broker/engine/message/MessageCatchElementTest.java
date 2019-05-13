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
package io.zeebe.broker.engine.message;

import static io.zeebe.broker.engine.WorkflowAssert.assertMessageSubscription;
import static io.zeebe.broker.engine.WorkflowAssert.assertWorkflowSubscription;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerConfigurator;
import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.MessageSubscriptionRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceSubscriptionRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MessageCatchElementTest {

  private static final int PARTITION_COUNT = 3;

  private static final String ELEMENT_ID = "receive-message";
  private static final String CORRELATION_VARIABLE = "orderId";
  private static final String MESSAGE_NAME = "order canceled";
  private static final String SEQUENCE_FLOW_ID = "to-end";

  private static final String CATCH_EVENT_WORKFLOW_PROCESS_ID = "catchEventWorkflow";
  private static final BpmnModelInstance CATCH_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(CATCH_EVENT_WORKFLOW_PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent(ELEMENT_ID)
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKey(CORRELATION_VARIABLE))
          .sequenceFlowId(SEQUENCE_FLOW_ID)
          .endEvent()
          .done();

  private static final String RECEIVE_TASK_WORKFLOW_PROCESS_ID = "receiveTaskWorkflow";
  private static final BpmnModelInstance RECEIVE_TASK_WORKFLOW =
      Bpmn.createExecutableProcess(RECEIVE_TASK_WORKFLOW_PROCESS_ID)
          .startEvent()
          .receiveTask(ELEMENT_ID)
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKey(CORRELATION_VARIABLE))
          .sequenceFlowId(SEQUENCE_FLOW_ID)
          .endEvent()
          .done();

  private static final String BOUNDARY_EVENT_WORKFLOW_PROCESS_ID = "boundaryEventWorkflow";
  private static final BpmnModelInstance BOUNDARY_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(BOUNDARY_EVENT_WORKFLOW_PROCESS_ID)
          .startEvent()
          .serviceTask(ELEMENT_ID, b -> b.zeebeTaskType("type"))
          .boundaryEvent()
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKey(CORRELATION_VARIABLE))
          .sequenceFlowId(SEQUENCE_FLOW_ID)
          .endEvent()
          .done();

  private static final String NON_INT_BOUNDARY_EVENT_WORKFLOW_PROCESS_ID =
      "nonIntBoundaryEventWorkflow";
  private static final BpmnModelInstance NON_INT_BOUNDARY_EVENT_WORKFLOW =
      Bpmn.createExecutableProcess(NON_INT_BOUNDARY_EVENT_WORKFLOW_PROCESS_ID)
          .startEvent()
          .serviceTask(ELEMENT_ID, b -> b.zeebeTaskType("type"))
          .boundaryEvent("event")
          .cancelActivity(false)
          .message(m -> m.name(MESSAGE_NAME).zeebeCorrelationKey(CORRELATION_VARIABLE))
          .sequenceFlowId(SEQUENCE_FLOW_ID)
          .endEvent()
          .done();

  public static EmbeddedBrokerRule brokerRule =
      new EmbeddedBrokerRule(EmbeddedBrokerConfigurator.setPartitionCount(PARTITION_COUNT));
  public static ClientApiRule apiRule =
      new ClientApiRule(0, PARTITION_COUNT, brokerRule::getAtomix);

  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter(0)
  public String elementType;

  @Parameter(1)
  public String bpmnProcessId;

  @Parameter(2)
  public WorkflowInstanceIntent enteredState;

  @Parameter(3)
  public WorkflowInstanceIntent continueState;

  @Parameter(4)
  public String continuedElementId;

  @Parameters(name = "{0}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        "intermediate message catch event",
        CATCH_EVENT_WORKFLOW_PROCESS_ID,
        WorkflowInstanceIntent.ELEMENT_ACTIVATED,
        WorkflowInstanceIntent.ELEMENT_COMPLETED,
        ELEMENT_ID
      },
      {
        "receive task",
        RECEIVE_TASK_WORKFLOW_PROCESS_ID,
        WorkflowInstanceIntent.ELEMENT_ACTIVATED,
        WorkflowInstanceIntent.ELEMENT_COMPLETED,
        ELEMENT_ID
      },
      {
        "int boundary event",
        BOUNDARY_EVENT_WORKFLOW_PROCESS_ID,
        WorkflowInstanceIntent.ELEMENT_ACTIVATED,
        WorkflowInstanceIntent.ELEMENT_TERMINATED,
        ELEMENT_ID
      },
      {
        "non int boundary event",
        NON_INT_BOUNDARY_EVENT_WORKFLOW_PROCESS_ID,
        WorkflowInstanceIntent.ELEMENT_ACTIVATED,
        WorkflowInstanceIntent.ELEMENT_COMPLETED,
        "event"
      }
    };
  }

  private String correlationKey;
  private long workflowInstanceKey;

  @BeforeClass
  public static void awaitCluster() {
    apiRule.waitForPartition(PARTITION_COUNT);

    deploy(CATCH_EVENT_WORKFLOW);
    deploy(RECEIVE_TASK_WORKFLOW);
    deploy(BOUNDARY_EVENT_WORKFLOW);
    deploy(NON_INT_BOUNDARY_EVENT_WORKFLOW);
  }

  private static void deploy(BpmnModelInstance modelInstance) {
    apiRule.deployWorkflow(modelInstance);
  }

  @Before
  public void init() {
    correlationKey = UUID.randomUUID().toString();
    workflowInstanceKey =
        apiRule
            .partitionClient()
            .createWorkflowInstance(
                r ->
                    r.setBpmnProcessId(bpmnProcessId)
                        .setVariables(asMsgPack("orderId", correlationKey)))
            .getInstanceKey();
  }

  @Test
  public void shouldOpenMessageSubscription() {
    final Record<WorkflowInstanceRecordValue> catchEventEntered =
        getFirstElementRecord(enteredState);

    final Record<MessageSubscriptionRecordValue> messageSubscription =
        getFirstMessageSubscriptionRecord(MessageSubscriptionIntent.OPENED);

    assertThat(messageSubscription.getMetadata().getValueType())
        .isEqualTo(ValueType.MESSAGE_SUBSCRIPTION);
    assertThat(messageSubscription.getMetadata().getRecordType()).isEqualTo(RecordType.EVENT);

    assertMessageSubscription(
        workflowInstanceKey, correlationKey, catchEventEntered, messageSubscription);
  }

  @Test
  public void shouldOpenWorkflowInstanceSubscription() {
    final Record<WorkflowInstanceRecordValue> catchEventEntered =
        getFirstElementRecord(enteredState);

    final Record<WorkflowInstanceSubscriptionRecordValue> workflowInstanceSubscription =
        getFirstWorkflowInstanceSubscriptionRecord(WorkflowInstanceSubscriptionIntent.OPENED);

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
    final Record<WorkflowInstanceRecordValue> catchEventEntered =
        getFirstElementRecord(enteredState);

    // when
    apiRule.publishMessage(MESSAGE_NAME, correlationKey, asMsgPack("foo", "bar"));

    // then
    final Record<WorkflowInstanceSubscriptionRecordValue> subscription =
        getFirstWorkflowInstanceSubscriptionRecord(WorkflowInstanceSubscriptionIntent.CORRELATED);

    assertThat(subscription.getMetadata().getValueType())
        .isEqualTo(ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION);
    assertThat(subscription.getMetadata().getRecordType()).isEqualTo(RecordType.EVENT);

    assertWorkflowSubscription(
        workflowInstanceKey, "{\"foo\":\"bar\"}", catchEventEntered, subscription);
  }

  @Test
  public void shouldCorrelateMessageSubscription() {
    // given
    final Record<WorkflowInstanceRecordValue> catchEventEntered =
        getFirstElementRecord(enteredState);

    // when
    apiRule.publishMessage(MESSAGE_NAME, correlationKey, asMsgPack("foo", "bar"));

    // then
    final Record<MessageSubscriptionRecordValue> subscription =
        getFirstMessageSubscriptionRecord(MessageSubscriptionIntent.CORRELATED);

    assertThat(subscription.getMetadata().getValueType()).isEqualTo(ValueType.MESSAGE_SUBSCRIPTION);
    assertThat(subscription.getMetadata().getRecordType()).isEqualTo(RecordType.EVENT);

    assertMessageSubscription(workflowInstanceKey, catchEventEntered, subscription);
  }

  @Test
  public void shouldCloseMessageSubscription() {
    // given
    final Record<WorkflowInstanceRecordValue> catchEventEntered =
        getFirstElementRecord(enteredState);

    RecordingExporter.messageSubscriptionRecords(MessageSubscriptionIntent.OPENED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .await();

    // when
    apiRule.cancelWorkflowInstance(workflowInstanceKey);

    // then
    final Record<MessageSubscriptionRecordValue> messageSubscription =
        getFirstMessageSubscriptionRecord(MessageSubscriptionIntent.CLOSED);

    assertThat(messageSubscription.getMetadata().getRecordType()).isEqualTo(RecordType.EVENT);

    Assertions.assertThat(messageSubscription.getValue())
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName(MESSAGE_NAME)
        .hasCorrelationKey("");
  }

  @Test
  public void shouldCloseWorkflowInstanceSubscription() {
    // given
    final Record<WorkflowInstanceRecordValue> catchEventEntered =
        getFirstElementRecord(enteredState);

    // when
    apiRule.cancelWorkflowInstance(workflowInstanceKey);

    // then
    final Record<WorkflowInstanceSubscriptionRecordValue> subscription =
        getFirstWorkflowInstanceSubscriptionRecord(WorkflowInstanceSubscriptionIntent.CLOSED);

    assertThat(subscription.getMetadata().getRecordType()).isEqualTo(RecordType.EVENT);

    Assertions.assertThat(subscription.getValue())
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementInstanceKey(catchEventEntered.getKey())
        .hasMessageName(MESSAGE_NAME);
  }

  @Test
  public void shouldCorrelateMessageAndContinue() {
    // given
    RecordingExporter.workflowInstanceSubscriptionRecords(WorkflowInstanceSubscriptionIntent.OPENED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withMessageName(MESSAGE_NAME)
        .await();

    // when
    apiRule.publishMessage(MESSAGE_NAME, correlationKey);

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(continueState)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId(continuedElementId)
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withElementId(SEQUENCE_FLOW_ID)
                .exists())
        .isTrue();
  }

  private Record<WorkflowInstanceRecordValue> getFirstElementRecord(WorkflowInstanceIntent intent) {
    return RecordingExporter.workflowInstanceRecords(intent)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementId(ELEMENT_ID)
        .getFirst();
  }

  private Record<MessageSubscriptionRecordValue> getFirstMessageSubscriptionRecord(
      MessageSubscriptionIntent intent) {
    return RecordingExporter.messageSubscriptionRecords(intent)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withMessageName(MESSAGE_NAME)
        .getFirst();
  }

  private Record<WorkflowInstanceSubscriptionRecordValue>
      getFirstWorkflowInstanceSubscriptionRecord(WorkflowInstanceSubscriptionIntent intent) {
    return RecordingExporter.workflowInstanceSubscriptionRecords(intent)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withMessageName(MESSAGE_NAME)
        .getFirst();
  }
}
