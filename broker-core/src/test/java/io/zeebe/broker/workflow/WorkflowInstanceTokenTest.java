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

import static io.zeebe.broker.workflow.gateway.ParallelGatewayStreamProcessorTest.PROCESS_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.IncidentRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class WorkflowInstanceTokenTest {

  public EmbeddedBrokerRule brokerRule = new EmbeddedBrokerRule();
  public ClientApiRule apiRule = new ClientApiRule(brokerRule::getClientAddress);

  @Rule public RuleChain ruleChain = RuleChain.outerRule(brokerRule).around(apiRule);

  private PartitionTestClient testClient;

  @Before
  public void init() {
    testClient = apiRule.partitionClient();
  }

  @Test
  public void shouldCompleteInstanceAfterEndEvent() {
    // given
    testClient.deploy(Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent("end").done());

    // when
    testClient.createWorkflowInstance(PROCESS_ID);

    // then
    assertThatWorkflowInstanceCompletedAfter("end", WorkflowInstanceIntent.EVENT_ACTIVATED);
  }

  @Test
  public void shouldCompleteInstanceAfterEventWithoutOutgoingSequenceFlows() {
    // given
    testClient.deploy(Bpmn.createExecutableProcess(PROCESS_ID).startEvent("start").done());

    // when
    testClient.createWorkflowInstance(PROCESS_ID);

    // then
    assertThatWorkflowInstanceCompletedAfter("start", WorkflowInstanceIntent.EVENT_TRIGGERED);
  }

  @Test
  public void shouldCompleteInstanceAfterActivityWithoutOutgoingSequenceFlows() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeTaskType("task"))
            .done());

    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    testClient.completeJobOfType("task");

    // then
    assertThatWorkflowInstanceCompletedAfter("task", WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldCompleteInstanceAfterParallelSplit() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway()
            .serviceTask("task-1", t -> t.zeebeTaskType("task-1"))
            .endEvent("end-1")
            .moveToLastGateway()
            .serviceTask("task-2", t -> t.zeebeTaskType("task-2"))
            .endEvent("end-2")
            .done());

    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    testClient.completeJobOfType("task-1");
    testClient.completeJobOfType("task-2");

    // then
    assertThatWorkflowInstanceCompletedAfter("end-2", WorkflowInstanceIntent.EVENT_ACTIVATED);
  }

  @Test
  public void shouldCompleteInstanceAfterParallelJoin() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .serviceTask("task-1", t -> t.zeebeTaskType("task-1"))
            .parallelGateway("join")
            .endEvent("end")
            .moveToNode("fork")
            .serviceTask("task-2", t -> t.zeebeTaskType("task-2"))
            .connectTo("join")
            .done());

    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    testClient.completeJobOfType("task-1");
    testClient.completeJobOfType("task-2");

    // then
    assertThatWorkflowInstanceCompletedAfter("end", WorkflowInstanceIntent.EVENT_ACTIVATED);
  }

  @Test
  public void shouldCompleteInstanceAfterMessageIntermediateCatchEvent() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway()
            .serviceTask("task", t -> t.zeebeTaskType("task"))
            .endEvent("end-1")
            .moveToLastGateway()
            .intermediateCatchEvent(
                "catch", e -> e.message(m -> m.name("msg").zeebeCorrelationKey("$.key")))
            .endEvent("end-2")
            .done());

    testClient.createWorkflowInstance(PROCESS_ID, "{'key':'123'}");

    // when
    testClient.completeJobOfType("task");
    testClient.publishMessage("msg", "123");

    // then
    assertThatWorkflowInstanceCompletedAfter("end-2", WorkflowInstanceIntent.EVENT_ACTIVATED);
  }

  @Test
  public void shouldCompleteInstanceAfterTimerIntermediateCatchEvent() {
    // given
    brokerRule.getClock().pinCurrentTime();

    testClient.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway()
            .serviceTask("task", t -> t.zeebeTaskType("task"))
            .endEvent("end-1")
            .moveToLastGateway()
            .intermediateCatchEvent("catch", e -> e.timerWithDuration("PT0.1S"))
            .endEvent("end-2")
            .done());

    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    testClient.completeJobOfType("task");

    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    // then
    assertThatWorkflowInstanceCompletedAfter("end-2", WorkflowInstanceIntent.EVENT_ACTIVATED);
  }

  @Test
  public void shouldCompleteInstanceAfterSubProcessEnded() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway()
            .serviceTask("task-1", t -> t.zeebeTaskType("task-1"))
            .endEvent("end-1")
            .moveToLastGateway()
            .subProcess(
                "sub",
                s ->
                    s.embeddedSubProcess()
                        .startEvent()
                        .serviceTask("task-2", t -> t.zeebeTaskType("task-2"))
                        .endEvent("end-sub"))
            .endEvent("end-2")
            .done());

    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    testClient.completeJobOfType("task-1");
    testClient.completeJobOfType("task-2");

    // then
    assertThatWorkflowInstanceCompletedAfter("end-2", WorkflowInstanceIntent.EVENT_ACTIVATED);
  }

  @Test
  public void shouldCompleteInstanceAfterEventBasedGateway() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway()
            .serviceTask("task", t -> t.zeebeTaskType("task"))
            .endEvent("end-1")
            .moveToLastGateway()
            .eventBasedGateway("gateway")
            .intermediateCatchEvent(
                "catch-1", e -> e.message(m -> m.name("msg-1").zeebeCorrelationKey("$.key")))
            .endEvent("end-2")
            .moveToNode("gateway")
            .intermediateCatchEvent(
                "catch-2", e -> e.message(m -> m.name("msg-2").zeebeCorrelationKey("$.key")))
            .endEvent("end-3")
            .done());

    testClient.createWorkflowInstance(PROCESS_ID, "{'key':'123'}");

    // when
    testClient.completeJobOfType("task");
    testClient.publishMessage("msg-1", "123");

    // then
    assertThatWorkflowInstanceCompletedAfter("end-2", WorkflowInstanceIntent.EVENT_ACTIVATED);
  }

  @Test
  public void shouldCompleteInstanceAfterInterruptingBoundaryEventTriggered() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeTaskType("task"))
            .endEvent("end-1")
            .moveToActivity("task")
            .boundaryEvent("timeout", b -> b.cancelActivity(true).timerWithDuration("PT0.1S"))
            .endEvent("end-2")
            .done());

    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    brokerRule.getClock().addTime(Duration.ofSeconds(1));

    // then
    assertThatWorkflowInstanceCompletedAfter("end-2", WorkflowInstanceIntent.EVENT_ACTIVATED);
  }

  @Test
  public void shouldCompleteInstanceAfterNonInterruptingBoundaryEventTriggered() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task-1", t -> t.zeebeTaskType("task-1"))
            .endEvent("end-1")
            .moveToActivity("task-1")
            .boundaryEvent("timeout", b -> b.cancelActivity(false).timerWithCycle("R1/PT0.1S"))
            .serviceTask("task-2", t -> t.zeebeTaskType("task-2"))
            .endEvent("end-2")
            .done());

    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    testClient.completeJobOfType("task-2");
    testClient.completeJobOfType("task-1");

    // then
    assertThatWorkflowInstanceCompletedAfter("end-1", WorkflowInstanceIntent.EVENT_ACTIVATED);
    assertThatWorkflowInstanceCompletedAfter("end-2", WorkflowInstanceIntent.EVENT_ACTIVATED);
  }

  @Test
  public void shouldNotCompleteInstanceAfterIncidentIsRaisedOnEvent() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway()
            .serviceTask("task", t -> t.zeebeTaskType("task"))
            .endEvent("end-1")
            .moveToLastGateway()
            .intermediateCatchEvent(
                "catch", e -> e.message(m -> m.name("msg").zeebeCorrelationKey("$.key")))
            .endEvent("end-2")
            .done());

    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).getFirst();

    testClient.completeJobOfType("task");

    testClient.updatePayload(incident.getValue().getElementInstanceKey(), "{'key':'123'}");
    testClient.resolveIncident(incident.getKey());

    testClient.publishMessage("msg", "123");

    // then
    assertThatWorkflowInstanceCompletedAfter("end-2", WorkflowInstanceIntent.EVENT_ACTIVATED);
  }

  @Test
  public void shouldNotCompleteInstanceAfterIncidentIsRaisedOnActivity() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway()
            .serviceTask("task-1", t -> t.zeebeTaskType("task-1"))
            .endEvent("end-1")
            .moveToLastGateway()
            .serviceTask("task-2", t -> t.zeebeTaskType("task-2").zeebeOutput("$.result", "$.r"))
            .endEvent("end-2")
            .done());

    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    testClient.completeJobOfType("task-2");

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).getFirst();

    testClient.completeJobOfType("task-1");

    testClient.updatePayload(incident.getValue().getElementInstanceKey(), "{'result':'123'}");
    testClient.resolveIncident(incident.getKey());

    // then
    assertThatWorkflowInstanceCompletedAfter("end-2", WorkflowInstanceIntent.EVENT_ACTIVATED);
  }

  @Test
  public void shouldNotCompleteInstanceAfterIncidentIsRaisedOnExclusiveGateway() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway()
            .serviceTask("task", t -> t.zeebeTaskType("task"))
            .endEvent("end-1")
            .moveToLastGateway()
            .exclusiveGateway("gateway")
            .defaultFlow()
            .endEvent("end-2")
            .moveToNode("gateway")
            .sequenceFlowId("to-end-3")
            .condition("$.x < 21")
            .endEvent("end-3")
            .done());

    testClient.createWorkflowInstance(PROCESS_ID);

    // when
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED).getFirst();

    testClient.completeJobOfType("task");

    testClient.updatePayload(incident.getValue().getElementInstanceKey(), "{'x':123}");
    testClient.resolveIncident(incident.getKey());

    // then
    assertThatWorkflowInstanceCompletedAfter("end-2", WorkflowInstanceIntent.EVENT_ACTIVATED);
  }

  private void assertThatWorkflowInstanceCompletedAfter(
      String elementId, WorkflowInstanceIntent intent) {
    final Record<WorkflowInstanceRecordValue> lastEvent =
        RecordingExporter.workflowInstanceRecords(intent).withElementId(elementId).getFirst();

    final Record<WorkflowInstanceRecordValue> completedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId(PROCESS_ID)
            .getFirst();

    assertThat(completedEvent.getPosition()).isGreaterThan(lastEvent.getPosition());
  }
}
