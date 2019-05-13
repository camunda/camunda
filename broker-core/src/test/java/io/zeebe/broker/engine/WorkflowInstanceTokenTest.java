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
package io.zeebe.broker.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.broker.test.EmbeddedBrokerRule;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.IncidentRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.broker.protocol.clientapi.ClientApiRule;
import io.zeebe.test.broker.protocol.clientapi.PartitionTestClient;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.Strings;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class WorkflowInstanceTokenTest {

  private static final EmbeddedBrokerRule BROKER_RULE = new EmbeddedBrokerRule();
  private static final ClientApiRule API_RULE = new ClientApiRule(BROKER_RULE::getAtomix);

  @ClassRule public static RuleChain ruleChain = RuleChain.outerRule(BROKER_RULE).around(API_RULE);

  @Rule
  public RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private PartitionTestClient testClient;
  private String processId;

  @Before
  public void setUp() {
    BROKER_RULE.getClock().reset();
    testClient = API_RULE.partitionClient();
    processId = Strings.newRandomValidBpmnId();
  }

  @Test
  public void shouldCompleteInstanceAfterEndEvent() {
    // given
    testClient.deploy(Bpmn.createExecutableProcess(processId).startEvent().endEvent("end").done());

    // when
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end");
  }

  @Test
  public void shouldCompleteInstanceAfterEventWithoutOutgoingSequenceFlows() {
    // given
    testClient.deploy(Bpmn.createExecutableProcess(processId).startEvent("start").done());

    // when
    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "start");
  }

  @Test
  public void shouldCompleteInstanceAfterActivityWithoutOutgoingSequenceFlows() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeTaskType("task"))
            .done());

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // when
    testClient.completeJobOfType(workflowInstanceKey, "task");

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "task");
  }

  @Test
  public void shouldCompleteInstanceAfterParallelSplit() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .parallelGateway()
            .serviceTask("task-1", t -> t.zeebeTaskType("task-1"))
            .endEvent("end-1")
            .moveToLastGateway()
            .serviceTask("task-2", t -> t.zeebeTaskType("task-2"))
            .endEvent("end-2")
            .done());

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // when
    testClient.completeJobOfType(workflowInstanceKey, "task-1");
    testClient.completeJobOfType(workflowInstanceKey, "task-2");

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  @Test
  public void shouldCompleteInstanceAfterParallelJoin() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .parallelGateway("fork")
            .serviceTask("task-1", t -> t.zeebeTaskType("task-1"))
            .parallelGateway("join")
            .endEvent("end")
            .moveToNode("fork")
            .serviceTask("task-2", t -> t.zeebeTaskType("task-2"))
            .connectTo("join")
            .done());

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // when
    testClient.completeJobOfType(workflowInstanceKey, "task-1");
    testClient.completeJobOfType(workflowInstanceKey, "task-2");

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end");
  }

  @Test
  public void shouldCompleteInstanceAfterMessageIntermediateCatchEvent() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .parallelGateway()
            .serviceTask("task", t -> t.zeebeTaskType("task"))
            .endEvent("end-1")
            .moveToLastGateway()
            .intermediateCatchEvent(
                "catch", e -> e.message(m -> m.name("msg").zeebeCorrelationKey("key")))
            .endEvent("end-2")
            .done());

    final long workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r ->
                    r.setBpmnProcessId(processId)
                        .setVariables(MsgPackUtil.asMsgPack("{'key':'123'}")))
            .getInstanceKey();

    // when
    testClient.completeJobOfType(workflowInstanceKey, "task");
    testClient.publishMessage("msg", "123");

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  @Test
  public void shouldCompleteInstanceAfterTimerIntermediateCatchEvent() {
    // given
    BROKER_RULE.getClock().pinCurrentTime();

    testClient.deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .parallelGateway()
            .serviceTask("task", t -> t.zeebeTaskType("task"))
            .endEvent("end-1")
            .moveToLastGateway()
            .intermediateCatchEvent("catch", e -> e.timerWithDuration("PT0.1S"))
            .endEvent("end-2")
            .done());

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // when
    testClient.completeJobOfType(workflowInstanceKey, "task");

    BROKER_RULE.getClock().addTime(Duration.ofSeconds(1));

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  @Test
  public void shouldCompleteInstanceAfterSubProcessEnded() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(processId)
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

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // when
    testClient.completeJobOfType(workflowInstanceKey, "task-1");
    testClient.completeJobOfType(workflowInstanceKey, "task-2");

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  @Test
  public void shouldCompleteInstanceAfterEventBasedGateway() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .parallelGateway()
            .serviceTask("task", t -> t.zeebeTaskType("task"))
            .endEvent("end-1")
            .moveToLastGateway()
            .eventBasedGateway("gateway")
            .intermediateCatchEvent(
                "catch-1", e -> e.message(m -> m.name("msg-1").zeebeCorrelationKey("key")))
            .endEvent("end-2")
            .moveToNode("gateway")
            .intermediateCatchEvent(
                "catch-2", e -> e.message(m -> m.name("msg-2").zeebeCorrelationKey("key")))
            .endEvent("end-3")
            .done());

    final long workflowInstanceKey =
        testClient
            .createWorkflowInstance(
                r ->
                    r.setBpmnProcessId(processId)
                        .setVariables(MsgPackUtil.asMsgPack("{'key':'123'}")))
            .getInstanceKey();

    // when
    testClient.completeJobOfType(workflowInstanceKey, "task");
    testClient.publishMessage("msg-1", "123");

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  @Test
  public void shouldCompleteInstanceAfterInterruptingBoundaryEventTriggered() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeTaskType("task"))
            .endEvent("end-1")
            .moveToActivity("task")
            .boundaryEvent("timeout", b -> b.cancelActivity(true).timerWithDuration("PT0.1S"))
            .endEvent("end-2")
            .done());

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // when
    testClient
        .receiveJobs()
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withIntent(JobIntent.CREATED)
        .getFirst();
    BROKER_RULE.getClock().addTime(Duration.ofSeconds(1));

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  @Test
  public void shouldCompleteInstanceAfterNonInterruptingBoundaryEventTriggered() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task-1", t -> t.zeebeTaskType("task-1"))
            .endEvent("end-1")
            .moveToActivity("task-1")
            .boundaryEvent("timeout", b -> b.cancelActivity(false).timerWithCycle("R1/PT0.1S"))
            .serviceTask("task-2", t -> t.zeebeTaskType("task-2"))
            .endEvent("end-2")
            .done());

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // when
    testClient
        .receiveJobs()
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withIntent(JobIntent.CREATED)
        .getFirst();
    BROKER_RULE.getClock().addTime(Duration.ofSeconds(1));
    testClient.completeJobOfType(workflowInstanceKey, "task-2");
    testClient.completeJobOfType(workflowInstanceKey, "task-1");

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-1");
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  @Test
  public void shouldNotCompleteInstanceAfterIncidentIsRaisedOnEvent() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .parallelGateway()
            .serviceTask("task", t -> t.zeebeTaskType("task"))
            .endEvent("end-1")
            .moveToLastGateway()
            .intermediateCatchEvent(
                "catch", e -> e.message(m -> m.name("msg").zeebeCorrelationKey("key")))
            .endEvent("end-2")
            .done());

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // when
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    testClient.completeJobOfType(workflowInstanceKey, "task");

    testClient.updateVariables(
        incident.getValue().getElementInstanceKey(), Maps.of(entry("key", "123")));
    testClient.resolveIncident(incident.getKey());

    testClient.publishMessage("msg", "123");

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  @Test
  public void shouldNotCompleteInstanceAfterIncidentIsRaisedOnActivity() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .parallelGateway()
            .serviceTask("task-1", t -> t.zeebeTaskType("task-1"))
            .endEvent("end-1")
            .moveToLastGateway()
            .serviceTask("task-2", t -> t.zeebeTaskType("task-2").zeebeOutput("result", "r"))
            .endEvent("end-2")
            .done());

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // when
    testClient.completeJobOfType(workflowInstanceKey, "task-2");

    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    testClient.completeJobOfType(workflowInstanceKey, "task-1");

    testClient.updateVariables(
        incident.getValue().getElementInstanceKey(), Maps.of(entry("result", "123")));
    testClient.resolveIncident(incident.getKey());

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  @Test
  public void shouldNotCompleteInstanceAfterIncidentIsRaisedOnExclusiveGateway() {
    // given
    testClient.deploy(
        Bpmn.createExecutableProcess(processId)
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
            .condition("x < 21")
            .endEvent("end-3")
            .done());

    final long workflowInstanceKey =
        testClient.createWorkflowInstance(r -> r.setBpmnProcessId(processId)).getInstanceKey();

    // when
    final Record<IncidentRecordValue> incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    testClient.completeJobOfType(workflowInstanceKey, "task");

    testClient.updateVariables(
        incident.getValue().getElementInstanceKey(), Maps.of(entry("x", 123)));
    testClient.resolveIncident(incident.getKey());

    // then
    assertThatWorkflowInstanceCompletedAfter(workflowInstanceKey, "end-2");
  }

  private void assertThatWorkflowInstanceCompletedAfter(
      long workflowInstanceKey, String elementId) {
    final Record<WorkflowInstanceRecordValue> lastEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(elementId)
            .getFirst();

    final Record<WorkflowInstanceRecordValue> completedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(processId)
            .getFirst();

    assertThat(completedEvent.getPosition()).isGreaterThan(lastEvent.getPosition());
  }
}
