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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Assertions;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.TimerRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
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

    testClient.createWorkflowInstance(PROCESS_ID);

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
            tuple("timer-1", WorkflowInstanceIntent.CATCH_EVENT_TRIGGERING),
            tuple("timer-1", WorkflowInstanceIntent.CATCH_EVENT_TRIGGERED),
            tuple("to-end1", WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("end1", WorkflowInstanceIntent.END_EVENT_OCCURRED),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCreateTimers() {
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
  public void shouldTriggerFirstEvent() {
    // given
    testClient.deploy(WORKFLOW_WITH_TIMERS);

    testClient.createWorkflowInstance(PROCESS_ID);

    final Record<WorkflowInstanceRecordValue> gatewayEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.GATEWAY_ACTIVATED)
            .getFirst();

    // when
    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    // then
    final Record<TimerRecordValue> triggeredEvent =
        RecordingExporter.timerRecords(TimerIntent.TRIGGERED).getFirst();
    Assertions.assertThat(triggeredEvent.getValue())
        .hasElementInstanceKey(gatewayEvent.getKey())
        .hasHandlerFlowNodeId("timer-1");

    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.END_EVENT_OCCURRED)
                .withElementId("end1")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldCancelSubscriptionsAfterFirstEventIsTriggered() {
    // given
    testClient.deploy(WORKFLOW_WITH_TIMERS);

    testClient.createWorkflowInstance(PROCESS_ID);

    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).limit(2).exists());

    // when
    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withHandlerNodeId("timer-1")
                .exists())
        .isTrue();

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CANCELED)
                .withHandlerNodeId("timer-2")
                .exists())
        .isTrue();
  }

  @Test
  public void shouldCancelSubscriptionsWhenScopeIsTerminated() {
    // given
    testClient.deploy(WORKFLOW_WITH_TIMERS);

    final long workflowInstanceKey = testClient.createWorkflowInstance(PROCESS_ID);

    assertThat(RecordingExporter.timerRecords(TimerIntent.CREATED).limit(2).exists());

    // when
    testClient.cancelWorkflowInstance(workflowInstanceKey);

    // then
    assertThat(RecordingExporter.timerRecords(TimerIntent.CANCELED).limit(2))
        .extracting(r -> r.getValue().getHandlerFlowNodeId())
        .hasSize(2)
        .contains("timer-1", "timer-2");
  }
}
