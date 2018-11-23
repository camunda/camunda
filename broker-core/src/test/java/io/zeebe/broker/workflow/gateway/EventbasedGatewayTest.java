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
package io.zeebe.broker.workflow.gateway;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Assertions;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.TimerRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceSubscriptionRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class EventbasedGatewayTest {

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance WORKFLOW_WITH_TIMERS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .eventBasedGateway()
          .id("gateway")
          .intermediateCatchEvent("timer-1", c -> c.timerWithDuration("PT0.1S"))
          .sequenceFlowId("to-end1")
          .endEvent("end1")
          .moveToLastGateway()
          .intermediateCatchEvent("timer-2", c -> c.timerWithDuration("PT10S"))
          .sequenceFlowId("to-end2")
          .endEvent("end2")
          .done();

  private static final BpmnModelInstance WORKFLOW_WITH_MESSAGES =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .eventBasedGateway()
          .id("gateway")
          .intermediateCatchEvent(
              "message-1", c -> c.message(m -> m.name("msg-1").zeebeCorrelationKey("$.key")))
          .sequenceFlowId("to-end1")
          .endEvent("end1")
          .moveToLastGateway()
          .intermediateCatchEvent(
              "message-2", c -> c.message(m -> m.name("msg-2").zeebeCorrelationKey("$.key")))
          .sequenceFlowId("to-end2")
          .endEvent("end2")
          .done();

  private static final BpmnModelInstance WORKFLOW_WITH_TIMER_AND_MESSAGE =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .eventBasedGateway()
          .id("gateway")
          .intermediateCatchEvent("timer", c -> c.timerWithDuration("PT10S"))
          .sequenceFlowId("to-end1")
          .endEvent("end1")
          .moveToLastGateway()
          .intermediateCatchEvent(
              "message", c -> c.message(m -> m.name("msg").zeebeCorrelationKey("$.key")))
          .sequenceFlowId("to-end2")
          .endEvent("end2")
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
  public void testLifecycle() {
    // given
    testClient.deploy(WORKFLOW_WITH_TIMERS);
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "123"));

    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).limit(2).exists());

    // when
    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .skipUntil(
                    r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.GATEWAY_ACTIVATED)
                .limitToWorkflowInstanceCompleted())
        .extracting(r -> tuple(r.getValue().getElementId(), r.getMetadata().getIntent()))
        .containsExactly(
            tuple("gateway", WorkflowInstanceIntent.GATEWAY_ACTIVATED),
            tuple("timer-1", WorkflowInstanceIntent.EVENT_TRIGGERING),
            tuple("timer-1", WorkflowInstanceIntent.EVENT_TRIGGERED),
            tuple("to-end1", WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("end1", WorkflowInstanceIntent.EVENT_ACTIVATING),
            tuple("end1", WorkflowInstanceIntent.EVENT_ACTIVATED),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCreateTimer() {
    // given
    testClient.deploy(WORKFLOW_WITH_TIMERS);

    // when
    testClient.createWorkflowInstance(PROCESS_ID);

    // then
    final Record<WorkflowInstanceRecordValue> gatewayEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.GATEWAY_ACTIVATED)
            .getFirst();

    final List<Record<TimerRecordValue>> timerEvents =
        RecordingExporter.timerRecords(TimerIntent.CREATED).limit(2).asList();

    assertThat(timerEvents)
        .hasSize(2)
        .extracting(
            r -> tuple(r.getValue().getHandlerFlowNodeId(), r.getValue().getElementInstanceKey()))
        .contains(tuple("timer-1", gatewayEvent.getKey()), tuple("timer-2", gatewayEvent.getKey()));
  }

  @Test
  public void shouldOpenWorkflowInstanceSubscriptions() {
    // given
    testClient.deploy(WORKFLOW_WITH_MESSAGES);

    // when
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "123"));

    // then
    final Record<WorkflowInstanceRecordValue> gatewayEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.GATEWAY_ACTIVATED)
            .getFirst();

    final List<Record<WorkflowInstanceSubscriptionRecordValue>> subscriptionEvents =
        RecordingExporter.workflowInstanceSubscriptionRecords(
                WorkflowInstanceSubscriptionIntent.OPENED)
            .limit(2)
            .asList();

    assertThat(subscriptionEvents)
        .hasSize(2)
        .extracting(r -> tuple(r.getValue().getMessageName(), r.getValue().getElementInstanceKey()))
        .contains(tuple("msg-1", gatewayEvent.getKey()), tuple("msg-2", gatewayEvent.getKey()));
  }

  @Test
  public void shouldContinueWhenTimerIsTriggered() {
    // given
    testClient.deploy(WORKFLOW_WITH_TIMERS);
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "123"));

    final Record<WorkflowInstanceRecordValue> gatewayEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.GATEWAY_ACTIVATED)
            .getFirst();

    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).limit(2).exists());

    // when
    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    // then
    final Record<TimerRecordValue> triggeredEvent =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED).getFirst();
    Assertions.assertThat(triggeredEvent.getValue())
        .hasElementInstanceKey(gatewayEvent.getKey())
        .hasHandlerFlowNodeId("timer-1");

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
                .withElementId("to-end1")
                .exists())
        .isTrue();

    assertThat(
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId(PROCESS_ID)
            .exists());
  }

  @Test
  public void shouldContinueWhenMessageIsCorrelated() {
    // given
    testClient.deploy(WORKFLOW_WITH_MESSAGES);
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "123"));

    final Record<WorkflowInstanceRecordValue> gatewayEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.GATEWAY_ACTIVATED)
            .getFirst();

    // when
    testClient.publishMessage("msg-1", "123");

    // then
    final Record<WorkflowInstanceSubscriptionRecordValue> triggeredEvent =
        RecordingExporter.workflowInstanceSubscriptionRecords(
                WorkflowInstanceSubscriptionIntent.CORRELATED)
            .getFirst();
    Assertions.assertThat(triggeredEvent.getValue())
        .hasElementInstanceKey(gatewayEvent.getKey())
        .hasMessageName("msg-1");

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
                .withElementId("to-end1")
                .exists())
        .isTrue();

    assertThat(
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId(PROCESS_ID)
            .exists());
  }

  @Test
  public void shouldCancelTimer() {
    // given
    testClient.deploy(WORKFLOW_WITH_TIMERS);
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "123"));

    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).limit(2).exists());

    // when
    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    // then
    assertThat(RecordingExporter.timerRecords(TimerIntent.CANCELED).limit(1))
        .extracting(r -> r.getValue().getHandlerFlowNodeId())
        .hasSize(1)
        .contains("timer-2");
  }

  @Test
  public void shouldCloseWorkflowInstanceSubscription() {
    // given
    testClient.deploy(WORKFLOW_WITH_MESSAGES);
    testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "123"));

    assertThat(
        RecordingExporter.workflowInstanceSubscriptionRecords(
                WorkflowInstanceSubscriptionIntent.OPENED)
            .limit(2)
            .exists());

    // when
    testClient.publishMessage("msg-1", "123");

    // then
    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.CLOSED)
                .limit(1))
        .extracting(r -> r.getValue().getMessageName())
        .hasSize(1)
        .contains("msg-2");
  }

  @Test
  public void shouldCancelSubscriptionsWhenScopeIsTerminated() {
    // given
    testClient.deploy(WORKFLOW_WITH_TIMER_AND_MESSAGE);

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(PROCESS_ID, asMsgPack("key", "123"));

    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).limit(1).exists());
    assertThat(
        RecordingExporter.workflowInstanceSubscriptionRecords(
                WorkflowInstanceSubscriptionIntent.OPENED)
            .limit(1)
            .exists());

    // when
    testClient.cancelWorkflowInstance(workflowInstanceKey);

    // then
    assertThat(RecordingExporter.timerRecords(TimerIntent.CANCELED).limit(1))
        .extracting(r -> r.getValue().getHandlerFlowNodeId())
        .hasSize(1)
        .contains("timer");

    assertThat(
            RecordingExporter.workflowInstanceSubscriptionRecords(
                    WorkflowInstanceSubscriptionIntent.CLOSED)
                .limit(1))
        .extracting(r -> r.getValue().getMessageName())
        .hasSize(1)
        .contains("msg");
  }
}
