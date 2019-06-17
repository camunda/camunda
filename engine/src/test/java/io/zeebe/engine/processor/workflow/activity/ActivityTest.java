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
package io.zeebe.engine.processor.workflow.activity;

import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordValue;
import io.zeebe.protocol.record.intent.TimerIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.zeebe.test.util.record.WorkflowInstances;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ActivityTest {
  private static final String PROCESS_ID = "process";

  private static final BpmnModelInstance WITHOUT_BOUNDARY_EVENTS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask(
              "task",
              b -> b.zeebeTaskType("type").zeebeInput("foo", "bar").zeebeOutput("bar", "oof"))
          .endEvent()
          .done();

  private static final BpmnModelInstance WITH_BOUNDARY_EVENTS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask("task", b -> b.zeebeTaskType("type"))
          .boundaryEvent("timer1")
          .timerWithDuration("PT10S")
          .endEvent()
          .moveToActivity("task")
          .boundaryEvent("timer2")
          .timerWithDuration("PT20S")
          .endEvent()
          .moveToActivity("task")
          .endEvent("taskEnd")
          .done();

  @ClassRule public static final EngineRule ENGINE = new EngineRule();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldApplyInputMappingOnReady() {
    // given
    ENGINE.deployment().withXmlResource(WITHOUT_BOUNDARY_EVENTS).deploy();
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{ \"foo\": 1, \"boo\": 2 }")
            .create();

    // when
    final Record<WorkflowInstanceRecordValue> record =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("task")
            .withIntent(ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    // then
    final Map<String, String> variables =
        WorkflowInstances.getCurrentVariables(workflowInstanceKey, record.getPosition());
    assertThat(variables).contains(entry("bar", "1"));
  }

  @Test
  public void shouldApplyOutputMappingOnCompleting() {
    // given
    ENGINE.deployment().withXmlResource(WITHOUT_BOUNDARY_EVENTS).deploy();
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{ \"foo\": 1, \"boo\": 2 }")
            .create();

    // when
    ENGINE.job().withType("type").ofInstance(workflowInstanceKey).complete();

    // then
    final Record<WorkflowInstanceRecordValue> record =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("task")
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();
    final Map<String, String> variables =
        WorkflowInstances.getCurrentVariables(workflowInstanceKey, record.getPosition());
    assertThat(variables).contains(entry("bar", "1"));
  }

  @Test
  public void shouldSubscribeToBoundaryEventTriggersOnReady() {
    // given
    ENGINE.deployment().withXmlResource(WITH_BOUNDARY_EVENTS).deploy();

    // when
    ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final List<Record<RecordValue>> records =
        RecordingExporter.records()
            .skipUntil(
                r ->
                    r.getValue() instanceof WorkflowInstanceRecord
                        && ((WorkflowInstanceRecord) r.getValue()).getElementId().equals("task")
                        && r.getMetadata().getIntent() == ELEMENT_ACTIVATING)
            .limit(
                r ->
                    r.getValue() instanceof WorkflowInstanceRecord
                        && ((WorkflowInstanceRecord) r.getValue()).getElementId().equals("task")
                        && r.getMetadata().getIntent() == ELEMENT_ACTIVATED)
            .asList();

    assertThat(records).hasSize(4);
    assertThat(records)
        .extracting(r -> r.getMetadata().getIntent())
        .contains(ELEMENT_ACTIVATING, TimerIntent.CREATE, TimerIntent.CREATE, ELEMENT_ACTIVATED);
  }

  @Test
  public void shouldUnsubscribeFromBoundaryEventTriggersOnCompleting() {
    // given
    ENGINE.deployment().withXmlResource(WITH_BOUNDARY_EVENTS).deploy();
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().withType("type").ofInstance(workflowInstanceKey).complete();

    // then
    shouldUnsubscribeFromBoundaryEventTrigger(
        workflowInstanceKey,
        WorkflowInstanceIntent.ELEMENT_COMPLETING,
        WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldUnsubscribeFromBoundaryEventTriggersOnTerminating() {
    // given
    ENGINE.deployment().withXmlResource(WITH_BOUNDARY_EVENTS).deploy();
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    RecordingExporter.workflowInstanceRecords()
        .withElementId("task")
        .withIntent(ELEMENT_ACTIVATED)
        .withWorkflowInstanceKey(workflowInstanceKey)
        .getFirst();
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    shouldUnsubscribeFromBoundaryEventTrigger(
        workflowInstanceKey,
        WorkflowInstanceIntent.ELEMENT_TERMINATING,
        WorkflowInstanceIntent.ELEMENT_TERMINATED);
  }

  @Test
  public void shouldIgnoreTaskHeadersIfEmpty() {
    createWorkflowAndAssertIgnoredHeaders("");
  }

  @Test
  public void shouldIgnoreTaskHeadersIfNull() {
    createWorkflowAndAssertIgnoredHeaders(null);
  }

  private void createWorkflowAndAssertIgnoredHeaders(String testValue) {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process")
            .startEvent("start")
            .serviceTask("task1", b -> b.zeebeTaskType("type1").zeebeTaskHeader("key", testValue))
            .endEvent("end")
            .moveToActivity("task1")
            .serviceTask("task2", b -> b.zeebeTaskType("type2").zeebeTaskHeader(testValue, "value"))
            .connectTo("end")
            .moveToActivity("task1")
            .serviceTask(
                "task3", b -> b.zeebeTaskType("type3").zeebeTaskHeader(testValue, testValue))
            .connectTo("end")
            .done();

    // when
    ENGINE.deployment().withXmlResource(model).deploy();
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    ENGINE.job().ofInstance(workflowInstanceKey).withType("type1").complete();
    ENGINE.job().ofInstance(workflowInstanceKey).withType("type2").complete();

    final JobRecordValue thirdJob =
        RecordingExporter.jobRecords().withType("type3").getFirst().getValue();
    assertThat(thirdJob.getCustomHeaders()).isEmpty();
  }

  private void shouldUnsubscribeFromBoundaryEventTrigger(
      long workflowInstanceKey,
      WorkflowInstanceIntent leavingState,
      WorkflowInstanceIntent leftState) {
    // given
    final List<Record<RecordValue>> records =
        RecordingExporter.records()
            .limitToWorkflowInstance(workflowInstanceKey)
            .between(
                r ->
                    r.getValue() instanceof WorkflowInstanceRecord
                        && ((WorkflowInstanceRecord) r.getValue()).getElementId().equals("task")
                        && r.getMetadata().getIntent() == leavingState,
                r ->
                    r.getValue() instanceof WorkflowInstanceRecord
                        && ((WorkflowInstanceRecord) r.getValue()).getElementId().equals("task")
                        && r.getMetadata().getIntent() == leftState)
            .asList();

    // then
    assertThat(records)
        .extracting(r -> r.getMetadata().getIntent())
        .contains(leavingState, TimerIntent.CANCEL, TimerIntent.CANCEL, leftState);
  }
}
