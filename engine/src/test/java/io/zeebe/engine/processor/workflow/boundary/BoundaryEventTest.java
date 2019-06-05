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
package io.zeebe.engine.processor.workflow.boundary;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.RecordMetadata;
import io.zeebe.exporter.api.record.RecordValue;
import io.zeebe.exporter.api.record.value.IncidentRecordValue;
import io.zeebe.exporter.api.record.value.VariableRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.ErrorType;
import io.zeebe.protocol.ValueType;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.WorkflowInstances;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Test;

public class BoundaryEventTest {
  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance MULTIPLE_SEQUENCE_FLOWS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("timer")
          .cancelActivity(true)
          .timerWithDuration("PT0.1S")
          .endEvent("end1")
          .moveToNode("timer")
          .endEvent("end2")
          .moveToActivity("task")
          .endEvent()
          .done();

  private static final BpmnModelInstance NON_INTERRUPTING_WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("event")
          .cancelActivity(false)
          .timerWithCycle("R/PT1S")
          .endEvent()
          .done();

  @ClassRule public static final EngineRule ENGINE = new EngineRule();

  @Test
  public void shouldTakeAllOutgoingSequenceFlowsIfTriggered() {
    // given
    ENGINE.deploy(MULTIPLE_SEQUENCE_FLOWS);
    final long workflowInstanceKey =
        ENGINE.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // when
    RecordingExporter.timerRecords()
        .withHandlerNodeId("timer")
        .withIntent(TimerIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .getFirst();
    ENGINE.increaseTime(Duration.ofMinutes(1));

    // then
    assertThat(
            RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
                .withElementType(BpmnElementType.END_EVENT)
                .withWorkflowInstanceKey(workflowInstanceKey)
                .limit(2))
        .extracting(Record::getValue)
        .extracting(WorkflowInstanceRecordValue::getElementId)
        .contains("end1", "end2");
  }

  @Test
  public void shouldActivateBoundaryEventWhenEventTriggered() {
    // given
    ENGINE.deploy(MULTIPLE_SEQUENCE_FLOWS);
    final long workflowInstanceKey =
        ENGINE.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // when
    RecordingExporter.timerRecords()
        .withHandlerNodeId("timer")
        .withIntent(TimerIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .getFirst();
    ENGINE.increaseTime(Duration.ofMinutes(1));

    // then
    final List<Record<RecordValue>> records =
        RecordingExporter.records()
            .limitToWorkflowInstance(workflowInstanceKey)
            .limit(
                r ->
                    r.getValue() instanceof WorkflowInstanceRecord
                        && ((WorkflowInstanceRecord) r.getValue()).getElementId().equals("timer")
                        && r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .asList();

    assertThat(records)
        .extracting(Record::getMetadata)
        .extracting(RecordMetadata::getValueType, RecordMetadata::getIntent)
        .containsSequence(
            tuple(ValueType.TIMER, TimerIntent.TRIGGERED),
            tuple(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(ValueType.JOB, JobIntent.CANCEL),
            tuple(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(ValueType.JOB, JobIntent.CANCELED),
            tuple(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_ACTIVATING));
  }
  //
  @Test
  public void shouldApplyOutputMappingOnTriggering() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", b -> b.zeebeTaskType("type"))
            .boundaryEvent("event")
            .message(m -> m.name("message").zeebeCorrelationKey("key"))
            .zeebeOutput("foo", "bar")
            .endEvent("endTimer")
            .moveToActivity("task")
            .endEvent()
            .done();
    ENGINE.deploy(workflow);
    ENGINE.createWorkflowInstance(
        r -> r.setBpmnProcessId(PROCESS_ID).setVariables(asMsgPack("key", "123")));

    // when
    ENGINE
        .message()
        .withName("message")
        .withCorrelationKey("123")
        .withVariables(asMsgPack("foo", 3))
        .publish();

    // then
    final Record<VariableRecordValue> variableEvent =
        RecordingExporter.variableRecords().withName("bar").getFirst();
    Assertions.assertThat(variableEvent.getValue()).hasValue("3");
  }

  @Test
  public void shouldUseScopeVariablesWhenApplyingOutputMappings() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", b -> b.zeebeTaskType("type").zeebeInput("oof", "baz"))
            .boundaryEvent("timer")
            .cancelActivity(true)
            .timerWithDuration("PT1S")
            .endEvent("endTimer")
            .moveToActivity("task")
            .endEvent()
            .done();
    ENGINE.deploy(workflow);
    final long workflowInstanceKey =
        ENGINE.createWorkflowInstance(
            r ->
                r.setBpmnProcessId(PROCESS_ID)
                    .setVariables(asMsgPack("{ \"foo\": 1, \"oof\": 2 }")));

    // when
    RecordingExporter.timerRecords()
        .withHandlerNodeId("timer")
        .withIntent(TimerIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .getFirst();
    ENGINE.increaseTime(Duration.ofMinutes(1));

    // then
    final Record<WorkflowInstanceRecordValue> boundaryTriggered =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("timer")
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();
    final Map<String, String> variables =
        WorkflowInstances.getCurrentVariables(workflowInstanceKey, boundaryTriggered.getPosition());
    assertThat(variables).contains(entry("foo", "1"), entry("oof", "2"));
  }

  @Test
  public void shouldTerminateSubProcessBeforeTriggeringBoundaryEvent() {
    // given
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .subProcess("sub")
            .embeddedSubProcess()
            .startEvent()
            .serviceTask("task", t -> t.zeebeTaskType("type"))
            .endEvent()
            .subProcessDone()
            .boundaryEvent("timer")
            .cancelActivity(true)
            .timerWithDuration("PT1S")
            .endEvent("endTimer")
            .moveToActivity("sub")
            .endEvent()
            .done();
    ENGINE.deploy(workflow);
    ENGINE.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // when
    RecordingExporter.jobRecords().withIntent(JobIntent.CREATED).getFirst();
    ENGINE.increaseTime(Duration.ofMinutes(1));

    final List<Record<RecordValue>> records =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getValue() instanceof WorkflowInstanceRecord
                        && ((WorkflowInstanceRecord) r.getValue()).getElementId().equals("timer")
                        && r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .asList();

    assertThat(records)
        .extracting(Record::getMetadata)
        .extracting(RecordMetadata::getValueType, RecordMetadata::getIntent)
        .endsWith(
            tuple(ValueType.TIMER, TimerIntent.TRIGGERED),
            tuple(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_TERMINATING),
            tuple(ValueType.JOB, JobIntent.CANCEL),
            tuple(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(ValueType.JOB, JobIntent.CANCELED),
            tuple(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_TERMINATED),
            tuple(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_ACTIVATING),
            tuple(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_ACTIVATED),
            tuple(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldNotTerminateActivityForNonInterruptingBoundaryEvents() {
    // given
    ENGINE.deploy(NON_INTERRUPTING_WORKFLOW);
    final long workflowInstanceKey =
        ENGINE.createWorkflowInstance(r -> r.setBpmnProcessId(PROCESS_ID));

    // when
    RecordingExporter.jobRecords()
        .withType("type")
        .withIntent(JobIntent.CREATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .getFirst();
    ENGINE.increaseTime(Duration.ofSeconds(1));
    RecordingExporter.timerRecords()
        .withHandlerNodeId("event")
        .withIntent(TimerIntent.TRIGGER)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .getFirst();
    ENGINE.job().ofInstance(workflowInstanceKey).withType("type").complete();

    // then
    final List<Record<RecordValue>> records =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getValue() instanceof WorkflowInstanceRecord
                        && ((WorkflowInstanceRecord) r.getValue()).getElementId().equals("task")
                        && r.getMetadata().getIntent() == WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .asList();

    assertThat(records)
        .extracting(Record::getMetadata)
        .extracting(RecordMetadata::getValueType, RecordMetadata::getIntent)
        .containsSubsequence(
            tuple(ValueType.TIMER, TimerIntent.TRIGGERED),
            tuple(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.EVENT_OCCURRED),
            tuple(ValueType.TIMER, TimerIntent.CREATE),
            tuple(ValueType.JOB, JobIntent.COMPLETED),
            tuple(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_COMPLETING),
            tuple(ValueType.TIMER, TimerIntent.CANCEL),
            tuple(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldUseScopeToExtractCorrelationKeys() {
    // given
    final String processId = "shouldHaveScopeKeyIfBoundaryEvent";
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", c -> c.zeebeTaskType("type").zeebeInput("bar", "foo"))
            .boundaryEvent(
                "event", b -> b.message(m -> m.zeebeCorrelationKey("foo").name("message")))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done();
    ENGINE.deploy(workflow);

    // when
    ENGINE.createWorkflowInstance(
        r ->
            r.setBpmnProcessId(processId)
                .setVariables(MsgPackUtil.asMsgPack(m -> m.put("foo", 1).put("bar", 2))));

    ENGINE.message().withName("message").withCorrelationKey("1").publish();

    // then
    // if correlation key was extracted from the task, then foo in the task scope would be 2 and
    // no event occurred would be published
    assertThat(
            RecordingExporter.workflowInstanceRecords()
                .withElementId("task")
                .withIntent(WorkflowInstanceIntent.EVENT_OCCURRED)
                .getFirst())
        .isNotNull();
  }

  @Test
  public void shouldHaveScopeKeyIfBoundaryEvent() {
    // given
    final String processId = "shouldHaveScopeKeyIfBoundaryEvent";
    final BpmnModelInstance workflow =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", c -> c.zeebeTaskType("type"))
            .boundaryEvent(
                "event", b -> b.message(m -> m.zeebeCorrelationKey("orderId").name("message")))
            .endEvent()
            .moveToActivity("task")
            .endEvent()
            .done();
    ENGINE.deploy(workflow);

    // when
    final long workflowInstanceKey =
        ENGINE.createWorkflowInstance(
            r ->
                r.setBpmnProcessId(processId).setVariables(MsgPackUtil.asMsgPack("orderId", true)));
    final Record<WorkflowInstanceRecordValue> failureEvent =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("task")
            .getFirst();

    // then
    final Record<IncidentRecordValue> incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentRecord.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR.name())
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("task")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasJobKey(-1L)
        .hasVariableScopeKey(workflowInstanceKey);
  }
}
