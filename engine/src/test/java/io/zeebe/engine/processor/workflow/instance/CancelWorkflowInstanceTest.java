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
package io.zeebe.engine.processor.workflow.instance;

import static io.zeebe.protocol.intent.WorkflowInstanceIntent.CANCEL;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_TERMINATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.api.record.value.job.Headers;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.RecordType;
import io.zeebe.protocol.RejectionType;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

public class CancelWorkflowInstanceTest {

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("WORKFLOW")
          .startEvent()
          .serviceTask("task", t -> t.zeebeTaskType("test").zeebeTaskRetries(5))
          .endEvent()
          .done();

  private static final BpmnModelInstance SUB_PROCESS_WORKFLOW =
      Bpmn.createExecutableProcess("SUB_PROCESS_WORKFLOW")
          .startEvent()
          .subProcess("subProcess")
          .embeddedSubProcess()
          .startEvent()
          .serviceTask("task", t -> t.zeebeTaskType("test").zeebeTaskRetries(5))
          .endEvent()
          .subProcessDone()
          .endEvent()
          .done();

  private static final BpmnModelInstance FORK_PROCESS;

  static {
    final AbstractFlowNodeBuilder<?, ?> builder =
        Bpmn.createExecutableProcess("FORK_PROCESS")
            .startEvent("start")
            .parallelGateway("fork")
            .serviceTask("task1", b -> b.zeebeTaskType("type1"))
            .endEvent("end1")
            .moveToNode("fork");

    FORK_PROCESS =
        builder.serviceTask("task2", b -> b.zeebeTaskType("type2")).endEvent("end2").done();
  }

  @ClassRule public static final EngineRule ENGINE = new EngineRule();

  @BeforeClass
  public static void init() {
    ENGINE.deploy(WORKFLOW);
    ENGINE.deploy(SUB_PROCESS_WORKFLOW);
    ENGINE.deploy(FORK_PROCESS);
  }

  @Test
  public void shouldCancelWorkflowInstance() {
    // given
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("WORKFLOW").create();
    RecordingExporter.workflowInstanceRecords()
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementId("task")
        .withIntent(ELEMENT_ACTIVATED)
        .getFirst();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    final Record<WorkflowInstanceRecordValue> workflowInstanceCanceledEvent =
        RecordingExporter.workflowInstanceRecords()
            .withRecordKey(workflowInstanceKey)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(ELEMENT_TERMINATED)
            .getFirst();

    Assertions.assertThat(workflowInstanceCanceledEvent.getValue())
        .hasBpmnProcessId("WORKFLOW")
        .hasVersion(1)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("WORKFLOW");

    final List<Record<WorkflowInstanceRecordValue>> workflowEvents =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .skipUntil(r -> r.getMetadata().getIntent() == CANCEL)
            .limit(
                r ->
                    r.getKey() == workflowInstanceKey
                        && r.getMetadata().getIntent() == ELEMENT_TERMINATED)
            .asList();

    assertThat(workflowEvents)
        .hasSize(5)
        .extracting(e -> e.getValue().getElementId(), e -> e.getMetadata().getIntent())
        .containsSequence(
            tuple("", CANCEL),
            tuple("WORKFLOW", WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("task", WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("task", ELEMENT_TERMINATED),
            tuple("WORKFLOW", ELEMENT_TERMINATED));
  }

  @Test
  public void shouldNotCancelElementInstance() {
    // given
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("WORKFLOW").create();
    final Record<WorkflowInstanceRecordValue> task =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("task")
            .withIntent(ELEMENT_ACTIVATED)
            .getFirst();

    // when
    final Record<WorkflowInstanceRecordValue> rejectedCancel =
        ENGINE
            .workflowInstance()
            .withInstanceKey(task.getKey())
            .onPartition(1)
            .expectRejection()
            .cancel();

    // then
    assertThat(rejectedCancel.getMetadata().getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejectedCancel.getMetadata().getRejectionReason())
        .isEqualTo(
            "Expected to cancel a workflow instance with key '"
                + task.getKey()
                + "', but no such workflow was found");
  }

  @Test
  public void shouldCancelWorkflowInstanceWithEmbeddedSubProcess() {
    // given
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("SUB_PROCESS_WORKFLOW").create();

    RecordingExporter.workflowInstanceRecords()
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementId("task")
        .withIntent(ELEMENT_ACTIVATED)
        .getFirst();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    final List<Record<WorkflowInstanceRecordValue>> workflowEvents =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .skipUntil(r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.CANCEL)
            .limitToWorkflowInstanceTerminated()
            .asList();

    assertThat(workflowEvents)
        .hasSize(7)
        .extracting(e -> e.getValue().getElementId(), e -> e.getMetadata().getIntent())
        .containsSequence(
            tuple("", WorkflowInstanceIntent.CANCEL),
            tuple("SUB_PROCESS_WORKFLOW", WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("subProcess", WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("task", WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple("task", ELEMENT_TERMINATED),
            tuple("subProcess", ELEMENT_TERMINATED),
            tuple("SUB_PROCESS_WORKFLOW", ELEMENT_TERMINATED));
  }

  @Test
  public void shouldCancelActivityInstance() {
    // given
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("WORKFLOW").create();
    final Record<WorkflowInstanceRecordValue> activityActivatedEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("task")
            .withIntent(ELEMENT_ACTIVATED)
            .getFirst();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    final Record<WorkflowInstanceRecordValue> activityTerminatedEvent =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("task")
            .withIntent(ELEMENT_TERMINATED)
            .getFirst();

    assertThat(activityTerminatedEvent.getKey()).isEqualTo(activityActivatedEvent.getKey());

    Assertions.assertThat(activityActivatedEvent.getValue())
        .hasBpmnProcessId("WORKFLOW")
        .hasVersion(1)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("task");
  }

  @Test
  public void shouldCancelWorkflowInstanceWithParallelExecution() {
    // given
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("FORK_PROCESS").create();

    RecordingExporter.workflowInstanceRecords()
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementType(BpmnElementType.SERVICE_TASK)
        .withIntent(ELEMENT_ACTIVATED)
        .limit(2)
        .asList();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    final List<Record<WorkflowInstanceRecordValue>> terminatedElements =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .skipUntil(r -> r.getMetadata().getIntent() == WorkflowInstanceIntent.CANCEL)
            .limitToWorkflowInstanceTerminated()
            .filter(r -> r.getMetadata().getIntent() == ELEMENT_TERMINATED)
            .asList();

    assertThat(terminatedElements).hasSize(3);
    assertThat(terminatedElements)
        .extracting(Record::getValue)
        .extracting(WorkflowInstanceRecordValue::getElementId)
        .containsSubsequence("task1", "FORK_PROCESS")
        .containsSubsequence("task2", "FORK_PROCESS")
        .contains("task1", "task2", "FORK_PROCESS");
  }

  @Test
  public void shouldCancelIntermediateCatchEvent() {
    // given
    ENGINE.deploy(
        Bpmn.createExecutableProcess("shouldCancelIntermediateCatchEvent")
            .startEvent()
            .intermediateCatchEvent("catch-event")
            .message(b -> b.name("msg").zeebeCorrelationKey("id"))
            .done());

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("shouldCancelIntermediateCatchEvent")
            .withVariable("id", "123")
            .create();

    RecordingExporter.workflowInstanceRecords()
        .withWorkflowInstanceKey(workflowInstanceKey)
        .withElementId("catch-event")
        .withIntent(ELEMENT_ACTIVATED)
        .getFirst();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    final Record<WorkflowInstanceRecordValue> terminatedEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("shouldCancelIntermediateCatchEvent")
            .withIntent(ELEMENT_TERMINATED)
            .getFirst();

    Assertions.assertThat(terminatedEvent.getValue())
        .hasBpmnProcessId("shouldCancelIntermediateCatchEvent")
        .hasVersion(1)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("shouldCancelIntermediateCatchEvent");
  }

  @Test
  public void shouldCancelJobForActivity() {
    // given
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("WORKFLOW").create();
    final Record<JobRecordValue> jobCreatedEvent =
        RecordingExporter.jobRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(JobIntent.CREATED)
            .getFirst();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    final Record<WorkflowInstanceRecordValue> terminateActivity =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("task")
            .withIntent(WorkflowInstanceIntent.ELEMENT_TERMINATING)
            .getFirst();

    final Record<JobRecordValue> jobCancelCmd =
        RecordingExporter.jobRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .onlyCommands()
            .withIntent(JobIntent.CANCEL)
            .getFirst();
    final Record<JobRecordValue> jobCanceledEvent =
        RecordingExporter.jobRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(JobIntent.CANCELED)
            .getFirst();

    assertThat(jobCanceledEvent.getKey()).isEqualTo(jobCreatedEvent.getKey());
    assertThat(jobCancelCmd.getSourceRecordPosition()).isEqualTo(terminateActivity.getPosition());
    assertThat(jobCanceledEvent.getSourceRecordPosition()).isEqualTo(jobCancelCmd.getPosition());

    final Headers headers = jobCanceledEvent.getValue().getHeaders();
    Assertions.assertThat(headers)
        .hasElementId("task")
        .hasWorkflowDefinitionVersion(1)
        .hasBpmnProcessId("WORKFLOW")
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }

  @Test
  public void shouldRejectCancelNonExistingWorkflowInstance() {
    // when
    final Record<WorkflowInstanceRecordValue> rejectedCancel =
        ENGINE.workflowInstance().withInstanceKey(-1).onPartition(1).expectRejection().cancel();

    // then
    assertThat(rejectedCancel.getMetadata().getRecordType())
        .isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejectedCancel.getMetadata().getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejectedCancel.getMetadata().getRejectionReason())
        .isEqualTo(
            "Expected to cancel a workflow instance with key '-1', but no such workflow was found");

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withPosition(rejectedCancel.getSourceRecordPosition())
                .withIntent(CANCEL)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldRejectCancelCompletedWorkflowInstance() {
    // given
    ENGINE.deploy(
        Bpmn.createExecutableProcess("shouldRejectCancelCompletedWorkflowInstance")
            .startEvent()
            .endEvent()
            .done());

    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("shouldRejectCancelCompletedWorkflowInstance")
            .create();

    RecordingExporter.workflowInstanceRecords()
        .withElementId("shouldRejectCancelCompletedWorkflowInstance")
        .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
        .getFirst();

    // when
    final Record<WorkflowInstanceRecordValue> rejectedCancel =
        ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).expectRejection().cancel();

    // then
    assertThat(rejectedCancel.getMetadata().getRecordType())
        .isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejectedCancel.getMetadata().getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejectedCancel.getMetadata().getRejectionReason())
        .isEqualTo(
            "Expected to cancel a workflow instance with key '"
                + workflowInstanceKey
                + "', but no such workflow was found");

    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withPosition(rejectedCancel.getSourceRecordPosition())
                .withIntent(CANCEL)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldRejectCancelAlreadyCanceledWorkflowInstance() {
    // given
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("WORKFLOW").create();
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // when
    final Record<WorkflowInstanceRecordValue> rejectedCancel =
        ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).expectRejection().cancel();

    // then
    assertThat(rejectedCancel.getMetadata().getRecordType())
        .isEqualTo(RecordType.COMMAND_REJECTION);
    assertThat(rejectedCancel.getMetadata().getRejectionType()).isEqualTo(RejectionType.NOT_FOUND);
    assertThat(rejectedCancel.getMetadata().getRejectionReason())
        .isEqualTo(
            "Expected to cancel a workflow instance with key '"
                + workflowInstanceKey
                + "', but no such workflow was found");
  }

  @Test
  public void shouldWriteEntireEventOnCancel() {
    // given
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("WORKFLOW").create();
    final Record<WorkflowInstanceRecordValue> activatedEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId("WORKFLOW")
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    // when
    final Record<WorkflowInstanceRecordValue> canceledRecord =
        ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    assertThat(canceledRecord.getValue()).isEqualTo(activatedEvent.getValue());
    //    MsgPackUtil.assertEqualityExcluding(
    //        response.getRawValue(), activatedEvent.getValue().toJson(), "variables");
    //
    //    final Record<WorkflowInstanceRecordValue> cancelingEvent =
    //        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_TERMINATING)
    //            .withElementId(JobAssert.PROCESS_ID)
    //            .getFirst();

    //    assertThat(cancelingEvent.getValue()).isEqualTo(activatedEvent.getValue());
  }
}
