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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.ExecuteCommandResponseDecoder;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceCreationIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.protocol.record.value.VariableRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceCreationRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.Strings;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.io.File;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.assertj.core.util.Files;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class WorkflowInstanceFunctionalTest {

  @ClassRule public static final EngineRule ENGINE = new EngineRule();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateWorkflowInstance() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(Bpmn.createExecutableProcess(processId).startEvent().endEvent().done())
        .deploy();

    // when
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId(processId).withVariable("foo", "bar").create();

    // then
    final long workflowCompletedPosition =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withElementId(processId)
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .getFirst()
            .getPosition();
    final Record<WorkflowInstanceCreationRecordValue> workflowCreated =
        RecordingExporter.workflowInstanceCreationRecords()
            .withIntent(WorkflowInstanceCreationIntent.CREATED)
            .withInstanceKey(workflowInstanceKey)
            .getFirst();
    final Record<WorkflowInstanceRecordValue> workflowActivating =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId(processId)
            .getFirst();
    final List<Record<VariableRecordValue>> variablesRecords =
        RecordingExporter.records()
            .between(workflowActivating.getSourceRecordPosition(), workflowCompletedPosition)
            .variableRecords()
            .collect(Collectors.toList());

    assertThat(workflowActivating.getKey()).isGreaterThan(0).isEqualTo(workflowInstanceKey);
    assertThat(workflowActivating.getSourceRecordPosition())
        .isGreaterThan(0)
        .isEqualTo(workflowCreated.getSourceRecordPosition());
    Assertions.assertThat(workflowActivating.getValue())
        .hasBpmnElementType(BpmnElementType.PROCESS)
        .hasBpmnProcessId(processId)
        .hasElementId(processId)
        .hasWorkflowInstanceKey(workflowInstanceKey);
    assertThat(variablesRecords).hasSize(1);
    Assertions.assertThat(variablesRecords.get(0))
        .hasSourceRecordPosition(workflowActivating.getSourceRecordPosition());
    Assertions.assertThat(variablesRecords.get(0).getValue()).hasName("foo").hasValue("\"bar\"");
  }

  @Test
  public void shouldStartWorkflowInstanceAtNoneStartEvent() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String startId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent(startId).endEvent().done())
        .deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // then
    final Record<WorkflowInstanceCreationRecordValue> workflowCreated =
        RecordingExporter.workflowInstanceCreationRecords()
            .withIntent(WorkflowInstanceCreationIntent.CREATED)
            .withInstanceKey(workflowInstanceKey)
            .getFirst();
    final Record<WorkflowInstanceRecordValue> startEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId(startId)
            .getFirst();

    assertThat(startEvent.getKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
    assertThat(startEvent.getPosition()).isGreaterThan(workflowCreated.getPosition());
    Assertions.assertThat(startEvent.getValue())
        .hasBpmnElementType(BpmnElementType.START_EVENT)
        .hasBpmnProcessId(processId)
        .hasElementId(startId)
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }

  @Test
  public void shouldTakeSequenceFlowFromStartEvent() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String sequenceId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .sequenceFlowId(sequenceId)
                .endEvent()
                .done())
        .deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // then
    final Record<WorkflowInstanceRecordValue> sequenceFlow =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .withElementId(sequenceId)
            .getFirst();

    assertThat(sequenceFlow.getKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
    Assertions.assertThat(sequenceFlow.getValue())
        .hasBpmnElementType(BpmnElementType.SEQUENCE_FLOW)
        .hasBpmnProcessId(processId)
        .hasElementId(sequenceId)
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }

  @Test
  public void shouldOccurEndEvent() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String endId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().endEvent(endId).done())
        .deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // then
    final Record<WorkflowInstanceRecordValue> endEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId(endId)
            .getFirst();

    assertThat(endEvent).isNotNull();
    Assertions.assertThat(endEvent.getValue())
        .hasBpmnElementType(BpmnElementType.END_EVENT)
        .hasBpmnProcessId(processId)
        .hasElementId(endId)
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }

  @Test
  public void shouldActivateServiceTask() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String taskId = Strings.newRandomValidBpmnId();
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(taskId, t -> t.zeebeTaskType("bar"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(model).deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // then
    final Record<WorkflowInstanceRecordValue> activityReady =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withElementId(taskId)
            .getFirst();

    final Record<WorkflowInstanceRecordValue> activatedEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId(taskId)
            .getFirst();

    assertThat(activatedEvent.getKey()).isGreaterThan(0).isNotEqualTo(workflowInstanceKey);
    assertThat(activatedEvent.getSourceRecordPosition()).isEqualTo(activityReady.getPosition());
    Assertions.assertThat(activatedEvent.getValue())
        .hasBpmnElementType(BpmnElementType.SERVICE_TASK)
        .hasBpmnProcessId(processId)
        .hasElementId(taskId)
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }

  @Test
  public void shouldCreateTaskWhenServiceTaskIsActivated() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String taskId = Strings.newRandomValidBpmnId();
    final String taskType = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(taskId, t -> t.zeebeTaskType(taskType).zeebeTaskRetries(5))
                .endEvent()
                .done())
        .deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // then
    final Record<WorkflowInstanceRecordValue> activityActivated =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId(taskId)
            .getFirst();
    final Record<JobRecordValue> createJobCmd =
        RecordingExporter.jobRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(JobIntent.CREATE)
            .withType(taskType)
            .getFirst();

    assertThat(createJobCmd.getKey()).isEqualTo(ExecuteCommandResponseDecoder.keyNullValue());
    assertThat(createJobCmd.getSourceRecordPosition()).isEqualTo(activityActivated.getPosition());
    Assertions.assertThat(createJobCmd.getValue()).hasRetries(5).hasType(taskType);
    Assertions.assertThat(createJobCmd.getValue().getHeaders())
        .hasBpmnProcessId(processId)
        .hasElementId(taskId)
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }

  @Test
  public void shouldCreateJobWithWorkflowInstanceAndCustomHeaders() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String taskId = Strings.newRandomValidBpmnId();
    final String taskType = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    taskId,
                    t ->
                        t.zeebeTaskType(taskType)
                            .zeebeTaskHeader("a", "b")
                            .zeebeTaskHeader("c", "d"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // then
    final Record<JobRecordValue> event =
        RecordingExporter.jobRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(JobIntent.CREATE)
            .withType(taskType)
            .getFirst();

    Assertions.assertThat(event.getValue().getHeaders())
        .hasBpmnProcessId(processId)
        .hasElementId(taskId)
        .hasWorkflowInstanceKey(workflowInstanceKey);

    final Map<String, String> customHeaders = event.getValue().getCustomHeaders();
    assertThat(customHeaders).containsEntry("a", "b").containsEntry("c", "d");
  }

  @Test
  public void shouldCompleteServiceTaskWhenTaskIsCompleted() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String taskId = Strings.newRandomValidBpmnId();
    final String taskType = Strings.newRandomValidBpmnId();
    final BpmnModelInstance definition =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(taskId, t -> t.zeebeTaskType(taskType))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(definition).deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE.job().ofInstance(workflowInstanceKey).withType(taskType).complete();

    // then
    final Record<WorkflowInstanceRecordValue> activityActivatedEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId(taskId)
            .getFirst();
    final Record<JobRecordValue> jobCompleted =
        RecordingExporter.jobRecords().withIntent(JobIntent.COMPLETED).getFirst();
    final Record<WorkflowInstanceRecordValue> activityCompleting =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETING)
            .withElementId(taskId)
            .getFirst();
    final Record<WorkflowInstanceRecordValue> activityCompletedEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId(taskId)
            .getFirst();

    assertThat(activityCompleting.getSourceRecordPosition()).isEqualTo(jobCompleted.getPosition());
    assertThat(activityCompletedEvent.getKey()).isEqualTo(activityActivatedEvent.getKey());
    assertThat(activityCompletedEvent.getSourceRecordPosition())
        .isEqualTo(activityCompleting.getPosition());
    Assertions.assertThat(activityCompletedEvent.getValue())
        .hasBpmnElementType(BpmnElementType.SERVICE_TASK)
        .hasBpmnProcessId(processId)
        .hasElementId(taskId)
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }

  @Test
  public void testWorkflowInstanceStatesWithServiceTask() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final String startId = Strings.newRandomValidBpmnId();
    final String taskId = Strings.newRandomValidBpmnId();
    final String endId = Strings.newRandomValidBpmnId();
    final String taskType = Strings.newRandomValidBpmnId();
    final BpmnModelInstance definition =
        Bpmn.createExecutableProcess(processId)
            .startEvent(startId)
            .serviceTask(taskId, t -> t.zeebeTaskType(taskType))
            .endEvent(endId)
            .done();

    ENGINE.deployment().withXmlResource(definition).deploy();
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE.job().ofInstance(workflowInstanceKey).withType(taskType).complete();

    // then
    final List<Record<WorkflowInstanceRecordValue>> workflowEvents =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .collect(Collectors.toList());

    assertThat(workflowEvents)
        .extracting(Record::getIntent)
        .containsExactly(
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED,
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED,
            WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN,
            WorkflowInstanceIntent.ELEMENT_ACTIVATING,
            WorkflowInstanceIntent.ELEMENT_ACTIVATED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED,
            WorkflowInstanceIntent.ELEMENT_COMPLETING,
            WorkflowInstanceIntent.ELEMENT_COMPLETED);
  }

  @Test
  public void shouldCreateAndCompleteInstanceOfYamlWorkflow() throws URISyntaxException {
    // given
    final String processId = "yaml-workflow";
    final File yamlFile =
        new File(getClass().getResource("/workflows/simple-workflow.yaml").toURI());
    final String yamlWorkflow = Files.contentOf(yamlFile, UTF_8);

    ENGINE.deployment().withYamlResource(yamlWorkflow.getBytes(UTF_8)).deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId(processId).create();

    // when
    ENGINE.job().withType("foo").ofInstance(workflowInstanceKey).complete();
    ENGINE.job().withType("bar").ofInstance(workflowInstanceKey).complete();

    // then
    final Record<WorkflowInstanceRecordValue> event =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limitToWorkflowInstanceCompleted()
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withElementId("yaml-workflow")
            .getFirst();

    assertThat(event.getKey()).isEqualTo(workflowInstanceKey);
    Assertions.assertThat(event.getValue())
        .hasBpmnElementType(BpmnElementType.PROCESS)
        .hasBpmnProcessId(processId)
        .hasElementId("yaml-workflow")
        .hasWorkflowInstanceKey(workflowInstanceKey);
  }
}
