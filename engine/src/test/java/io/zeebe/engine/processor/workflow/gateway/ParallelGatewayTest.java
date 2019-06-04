/*
 * Zeebe Workflow Engine
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
package io.zeebe.engine.processor.workflow.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.ServiceTask;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;

public class ParallelGatewayTest {

  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance FORK_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .parallelGateway("fork")
          .serviceTask("task1", b -> b.zeebeTaskType("type1"))
          .endEvent("end1")
          .moveToNode("fork")
          .serviceTask("task2", b -> b.zeebeTaskType("type2"))
          .endEvent("end2")
          .done();

  private static final BpmnModelInstance FORK_JOIN_PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("start")
          .parallelGateway("fork")
          .sequenceFlowId("flow1")
          .parallelGateway("join")
          .endEvent("end")
          .moveToNode("fork")
          .sequenceFlowId("flow2")
          .connectTo("join")
          .done();

  @Rule public EngineRule engine = new EngineRule();

  @Test
  public void shouldActivateTasksOnParallelBranches() {
    // given
    engine.deploy(FORK_PROCESS);

    // when
    engine.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // then
    final List<Record<WorkflowInstanceRecordValue>> taskEvents =
        RecordingExporter.workflowInstanceRecords()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .filter(e -> isServiceTaskInProcess(e.getValue().getElementId(), FORK_PROCESS))
            .limit(2)
            .collect(Collectors.toList());

    assertThat(taskEvents).hasSize(2);
    assertThat(taskEvents)
        .extracting(e -> e.getValue().getElementId())
        .containsExactlyInAnyOrder("task1", "task2");
    assertThat(taskEvents.get(0).getKey()).isNotEqualTo(taskEvents.get(1).getKey());
  }

  @Test
  public void shouldCompleteScopeWhenAllPathsCompleted() {
    // given
    engine.deploy(FORK_PROCESS);
    final long workflowInstanceKey =
        engine
            .createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID))
            .getValue()
            .getInstanceKey();
    engine.job(workflowInstanceKey).withType("type1").complete();

    // when
    engine.job(workflowInstanceKey).withType("type2").complete();

    // then
    final List<Record<WorkflowInstanceRecordValue>> completedEvents =
        RecordingExporter.workflowInstanceRecords()
            .withElementType(BpmnElementType.END_EVENT)
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .limit(2)
            .collect(Collectors.toList());

    assertThat(completedEvents)
        .extracting(e -> e.getValue().getElementId())
        .containsExactly("end1", "end2");

    RecordingExporter.workflowInstanceRecords()
        .withElementId(PROCESS_ID)
        .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
        .getFirst();
  }

  @Test
  public void shouldCompleteScopeWithMultipleTokensOnSamePath() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .exclusiveGateway("join")
            .endEvent("end")
            .moveToNode("fork")
            .connectTo("join")
            .done();

    engine.deploy(process);

    // when
    engine.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // then
    final List<Record<WorkflowInstanceRecordValue>> workflowInstanceEvents =
        RecordingExporter.workflowInstanceRecords()
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(workflowInstanceEvents)
        .extracting(e -> e.getValue().getElementId(), e -> e.getMetadata().getIntent())
        .containsSubsequence(
            tuple("end", WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple("end", WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldPassThroughParallelGateway() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .sequenceFlowId("flow1")
            .parallelGateway("fork")
            .sequenceFlowId("flow2")
            .endEvent("end")
            .done();

    engine.deploy(process);

    // when
    engine.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // then
    final List<Record<WorkflowInstanceRecordValue>> workflowInstanceEvents =
        RecordingExporter.workflowInstanceRecords()
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(workflowInstanceEvents)
        .extracting(e -> e.getValue().getElementId(), e -> e.getMetadata().getIntent())
        .containsSequence(
            tuple("fork", WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple("fork", WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple("fork", WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple("fork", WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple("flow2", WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("end", WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple("end", WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple("end", WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple("end", WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompleteScopeOnParallelGateway() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .sequenceFlowId("flow1")
            .parallelGateway("fork")
            .done();

    engine.deploy(process);

    // when
    engine.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // then
    final List<Record<WorkflowInstanceRecordValue>> workflowInstanceEvents =
        RecordingExporter.workflowInstanceRecords()
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(workflowInstanceEvents)
        .extracting(e -> e.getValue().getElementId(), e -> e.getMetadata().getIntent())
        .containsSequence(
            tuple("fork", WorkflowInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETING));
  }

  @Test
  public void shouldMergeParallelBranches() {
    // given
    engine.deploy(FORK_JOIN_PROCESS);

    // when
    engine.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // then
    final List<Record<WorkflowInstanceRecordValue>> events =
        RecordingExporter.workflowInstanceRecords()
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(events)
        .extracting(e -> e.getValue().getElementId(), e -> e.getMetadata().getIntent())
        .containsSubsequence(
            tuple("flow1", WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("join", WorkflowInstanceIntent.ELEMENT_ACTIVATING))
        .containsSubsequence(
            tuple("flow2", WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("join", WorkflowInstanceIntent.ELEMENT_ACTIVATING))
        .containsOnlyOnce(tuple("join", WorkflowInstanceIntent.ELEMENT_ACTIVATING));
  }

  @Test
  public void shouldOnlyTriggerGatewayWhenAllBranchesAreActivated() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .parallelGateway("fork")
            .exclusiveGateway("exclusiveJoin")
            .moveToLastGateway()
            .connectTo("exclusiveJoin")
            .sequenceFlowId("joinFlow1")
            .parallelGateway("join")
            .moveToNode("fork")
            .serviceTask("waitState", b -> b.zeebeTaskType("type"))
            .sequenceFlowId("joinFlow2")
            .connectTo("join")
            .endEvent()
            .done();

    engine.deploy(process);

    final long workflowInstanceKey =
        engine
            .createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID))
            .getValue()
            .getInstanceKey();

    // waiting until we have signalled the first incoming sequence flow twice
    // => this should not trigger the gateway yet
    RecordingExporter.workflowInstanceRecords()
        .limit(r -> "joinFlow1".equals(r.getValue().getElementId()))
        .limit(2)
        .skip(1)
        .getFirst();

    // when
    // we complete the job
    engine.job(workflowInstanceKey).withType("type").complete();

    // then
    final List<Record<WorkflowInstanceRecordValue>> events =
        RecordingExporter.workflowInstanceRecords()
            .limit(
                r ->
                    "join".equals(r.getValue().getElementId())
                        && WorkflowInstanceIntent.ELEMENT_COMPLETED == r.getMetadata().getIntent())
            .collect(Collectors.toList());

    assertThat(events)
        .extracting(e -> e.getValue().getElementId(), e -> e.getMetadata().getIntent())
        .containsSubsequence(
            tuple("joinFlow1", WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("joinFlow1", WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("joinFlow2", WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN),
            tuple("join", WorkflowInstanceIntent.ELEMENT_ACTIVATING));
  }

  @Test
  public void shouldMergeAndSplitInOneGateway() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .parallelGateway("fork")
            .parallelGateway("join-fork")
            .moveToNode("fork")
            .connectTo("join-fork")
            .serviceTask("task1", b -> b.zeebeTaskType("type1"))
            .moveToLastGateway()
            .serviceTask("task2", b -> b.zeebeTaskType("type2"))
            .done();

    engine.deploy(process);

    // when
    engine.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // then
    final List<Record<WorkflowInstanceRecordValue>> elementInstances =
        RecordingExporter.workflowInstanceRecords()
            .filter(
                r ->
                    r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_ACTIVATED
                        && r.getValue().getBpmnElementType() == BpmnElementType.SERVICE_TASK)
            .limit(2)
            .collect(Collectors.toList());

    assertThat(elementInstances)
        .extracting(e -> e.getValue().getElementId())
        .contains("task1", "task2");
  }

  @Test
  public void shouldSplitWithUncontrolledFlow() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent("start")
            .serviceTask("task1", b -> b.zeebeTaskType("type1"))
            .moveToNode("start")
            .serviceTask("task2", b -> b.zeebeTaskType("type2"))
            .done();

    engine.deploy(process);

    // when
    engine.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // then
    final List<Record<WorkflowInstanceRecordValue>> taskEvents =
        RecordingExporter.workflowInstanceRecords()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .filter(e -> isServiceTaskInProcess(e.getValue().getElementId(), process))
            .limit(2)
            .collect(Collectors.toList());

    assertThat(taskEvents).hasSize(2);
    assertThat(taskEvents)
        .extracting(e -> e.getValue().getElementId())
        .containsExactlyInAnyOrder("task1", "task2");
    assertThat(taskEvents.get(0).getKey()).isNotEqualTo(taskEvents.get(1).getKey());
  }

  private static boolean isServiceTaskInProcess(String activityId, BpmnModelInstance process) {
    return process.getModelElementsByType(ServiceTask.class).stream()
        .anyMatch(t -> t.getId().equals(activityId));
  }
}
