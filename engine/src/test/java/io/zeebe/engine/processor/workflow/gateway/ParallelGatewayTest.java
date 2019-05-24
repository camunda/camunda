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

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.processor.workflow.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.ServiceTask;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.DirectBuffer;
import org.assertj.core.api.Assertions;
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
    final List<TypedRecord<WorkflowInstanceRecord>> taskEvents =
        engine.collectWorkflowInstanceRecords(
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            e -> isServiceTaskInProcess(e.getValue().getElementId(), FORK_PROCESS),
            2);

    assertThat(taskEvents).hasSize(2);
    assertThat(taskEvents)
        .extracting(e -> e.getValue().getElementId())
        .containsExactlyInAnyOrder(wrapString("task1"), wrapString("task2"));
    assertThat(taskEvents.get(0).getKey()).isNotEqualTo(taskEvents.get(1).getKey());
  }

  @Test
  public void shouldCompleteScopeWhenAllPathsCompleted() {
    // given
    engine.deploy(FORK_PROCESS);
    engine.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));
    engine.completeJobOfType("type1");

    // when
    engine.completeJobOfType("type2");

    // then
    final List<TypedRecord<WorkflowInstanceRecord>> completedEvents =
        engine.collectWorkflowInstanceRecords(
            WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnElementType.END_EVENT, 2);

    assertThat(completedEvents)
        .extracting(e -> e.getValue().getElementId())
        .containsExactly(wrapString("end1"), wrapString("end2"));

    Assertions.assertThat(
            engine.awaitWorkflowInstanceRecord(
                PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETED))
        .isNotNull();
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
    final List<TypedRecord<WorkflowInstanceRecord>> workflowInstanceEvents =
        engine.collectWorkflowInstanceRecords(
            WorkflowInstanceIntent.ELEMENT_COMPLETED,
            (r) ->
                r.getValue().getElementId().equals(wrapString(PROCESS_ID))
                    && r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_COMPLETED);

    assertThat(workflowInstanceEvents)
        .extracting(
            e -> bufferAsString(e.getValue().getElementId()), e -> e.getMetadata().getIntent())
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
    final List<TypedRecord<WorkflowInstanceRecord>> workflowInstanceEvents =
        engine.collectWorkflowInstanceRecordsUntilCompletion();

    assertThat(workflowInstanceEvents)
        .extracting(
            e -> bufferAsString(e.getValue().getElementId()), e -> e.getMetadata().getIntent())
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
            tuple(PROCESS_ID, WorkflowInstanceIntent.ELEMENT_COMPLETING));
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
    final List<TypedRecord<WorkflowInstanceRecord>> workflowInstanceEvents =
        engine.collectWorkflowInstanceRecordsUntilCompletion();

    assertThat(workflowInstanceEvents)
        .extracting(
            e -> bufferAsString(e.getValue().getElementId()), e -> e.getMetadata().getIntent())
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
    final List<TypedRecord<WorkflowInstanceRecord>> events =
        engine.collectWorkflowInstanceRecordsUntilCompletion();

    assertThat(events)
        .extracting(
            e -> bufferAsString(e.getValue().getElementId()), e -> e.getMetadata().getIntent())
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

    engine.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // waiting until we have signalled the first incoming sequence flow twice
    // => this should not trigger the gateway yet

    engine.collectWorkflowInstanceRecordsUntil(
        r -> wrapString("joinFlow1").equals(r.getValue().getElementId()));

    // when
    // we complete the job
    engine.completeJobOfType("type");

    // then
    final List<TypedRecord<WorkflowInstanceRecord>> events =
        engine.collectWorkflowInstanceRecordsUntil(
            r ->
                wrapString("join").equals(r.getValue().getElementId())
                    && WorkflowInstanceIntent.ELEMENT_COMPLETED == r.getMetadata().getIntent());

    assertThat(events)
        .extracting(
            e -> bufferAsString(e.getValue().getElementId()), e -> e.getMetadata().getIntent())
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
    final List<TypedRecord<WorkflowInstanceRecord>> elementInstances =
        engine.collectWorkflowInstanceRecords(
            WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnElementType.SERVICE_TASK, 2);

    assertThat(elementInstances)
        .extracting(e -> bufferAsString(e.getValue().getElementId()))
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
    final List<TypedRecord<WorkflowInstanceRecord>> taskEvents =
        engine.collectWorkflowInstanceRecords(
            WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnElementType.SERVICE_TASK, 2);

    assertThat(taskEvents).hasSize(2);
    assertThat(taskEvents)
        .extracting(e -> bufferAsString(e.getValue().getElementId()))
        .containsExactlyInAnyOrder("task1", "task2");
    assertThat(taskEvents.get(0).getKey()).isNotEqualTo(taskEvents.get(1).getKey());
  }

  private static boolean isServiceTaskInProcess(
      DirectBuffer activityId, BpmnModelInstance process) {
    final String activityIdString = BufferUtil.bufferAsString(activityId);

    return process.getModelElementsByType(ServiceTask.class).stream()
        .anyMatch(
            t -> {
              return t.getId().equals(activityIdString);
            });
  }
}
