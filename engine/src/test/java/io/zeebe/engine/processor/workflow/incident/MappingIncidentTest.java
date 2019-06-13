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
package io.zeebe.engine.processor.workflow.incident;

import static io.zeebe.protocol.intent.IncidentIntent.CREATED;
import static io.zeebe.protocol.intent.IncidentIntent.RESOLVE;
import static io.zeebe.protocol.intent.IncidentIntent.RESOLVED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.exporter.api.record.Assertions;
import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.IncidentRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.ErrorType;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.HashMap;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class MappingIncidentTest {

  @ClassRule public static final EngineRule ENGINE = new EngineRule();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static final BpmnModelInstance WORKFLOW_INPUT_MAPPING =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask("failingTask", t -> t.zeebeTaskType("test").zeebeInput("foo", "foo"))
          .done();

  private static final BpmnModelInstance WORKFLOW_OUTPUT_MAPPING =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask("failingTask", t -> t.zeebeTaskType("test").zeebeOutput("foo", "foo"))
          .done();

  private static final Map<String, Object> VARIABLES = Maps.of(entry("foo", "bar"));

  private static final String VARIABLES_JSON =
      "{'string':'value', 'jsonObject':{'testAttr':'test'}}";

  @Test
  public void shouldCreateIncidentForInputMappingFailure() {
    // given
    final long workflowKey =
        ENGINE
            .deployment()
            .withXmlResource(WORKFLOW_INPUT_MAPPING)
            .deploy()
            .getValue()
            .getDeployedWorkflows()
            .get(0)
            .getWorkflowKey();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("process").create();

    // then
    final Record failureEvent =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("failingTask")
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();
    final Record createIncidentEvent =
        RecordingExporter.incidentRecords()
            .onlyCommands()
            .withIntent(IncidentIntent.CREATE)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .onlyEvents()
            .withIntent(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertThat(createIncidentEvent.getSourceRecordPosition()).isEqualTo(failureEvent.getPosition());
    assertThat(incidentEvent.getSourceRecordPosition())
        .isEqualTo(createIncidentEvent.getPosition());
    assertThat(incidentEvent.getValue().getVariableScopeKey()).isEqualTo(failureEvent.getKey());

    final IncidentRecordValue incidentEventValue = incidentEvent.getValue();
    Assertions.assertThat(incidentEventValue)
        .hasErrorType(ErrorType.IO_MAPPING_ERROR.name())
        .hasErrorMessage("No data found for query foo.")
        .hasBpmnProcessId("process")
        .hasWorkflowKey(workflowKey)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey());
  }

  @Test
  public void shouldCreateIncidentForNonMatchingAndMatchingValueOnInputMapping() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask(
                    "service",
                    t ->
                        t.zeebeTaskType("external")
                            .zeebeInput("notExisting", "nullVal")
                            .zeebeInput("string", "existing"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("process").withVariables(VARIABLES_JSON).create();

    final Record<WorkflowInstanceRecordValue> failureEvent =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("service")
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    // then incident is created
    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertThat(incidentEvent.getValue().getVariableScopeKey()).isEqualTo(failureEvent.getKey());

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR.name())
        .hasErrorMessage("No data found for query notExisting.")
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("service")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey());
  }

  @Test
  public void shouldCreateIncidentForOutputMappingFailure() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW_OUTPUT_MAPPING).deploy();

    // when
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("process").create();

    ENGINE
        .job()
        .withType("test")
        .ofInstance(workflowInstanceKey)
        .withVariables(VARIABLES_JSON)
        .complete();

    // then
    final Record failureEvent =
        RecordingExporter.workflowInstanceRecords()
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETING)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    final Record createIncidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATE)
            .getFirst();
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertThat(createIncidentEvent.getSourceRecordPosition()).isEqualTo(failureEvent.getPosition());
    assertThat(incidentEvent.getSourceRecordPosition())
        .isEqualTo(createIncidentEvent.getPosition());

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR.name())
        .hasErrorMessage("No data found for query foo.")
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey());
  }

  @Test
  public void shouldResolveIncidentForInputMappingFailure() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW_INPUT_MAPPING).deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("process").create();

    final Record<WorkflowInstanceRecordValue> failureEvent =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("failingTask")
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    // when
    ENGINE
        .variables()
        .ofScope(failureEvent.getValue().getFlowScopeKey())
        .withDocument(VARIABLES)
        .update();
    final Record<IncidentRecordValue> incidentResolvedEvent =
        ENGINE.incident().ofInstance(workflowInstanceKey).withKey(incidentEvent.getKey()).resolve();

    // then
    final Record<WorkflowInstanceRecordValue> followUpEvent =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("failingTask")
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    final Record incidentResolveCommand =
        RecordingExporter.incidentRecords()
            .withIntent(RESOLVE)
            .withRecordKey(incidentEvent.getKey())
            .getFirst();

    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentEvent.getKey());
    assertThat(incidentResolveCommand.getPosition())
        .isEqualTo(followUpEvent.getSourceRecordPosition());
    assertThat(incidentResolveCommand.getPosition())
        .isEqualTo(incidentResolvedEvent.getSourceRecordPosition());

    Assertions.assertThat(incidentResolvedEvent.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR.name())
        .hasErrorMessage("No data found for query foo.")
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey());
  }

  @Test
  public void shouldResolveIncidentForOutputMappingFailure() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW_OUTPUT_MAPPING).deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("process").create();
    ENGINE
        .job()
        .ofInstance(workflowInstanceKey)
        .withType("test")
        .withVariables(VARIABLES_JSON)
        .complete();

    final Record failureEvent =
        RecordingExporter.workflowInstanceRecords()
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withIntent(WorkflowInstanceIntent.ELEMENT_COMPLETING)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    final Record incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(CREATED)
            .getFirst();

    // when
    ENGINE.variables().ofScope(failureEvent.getKey()).withDocument(VARIABLES).update();
    final Record<IncidentRecordValue> incidentResolvedEvent =
        ENGINE.incident().ofInstance(workflowInstanceKey).withKey(incidentEvent.getKey()).resolve();

    // then
    final Record<WorkflowInstanceRecordValue> followUpEvent =
        RecordingExporter.workflowInstanceRecords()
            .withElementType(BpmnElementType.SERVICE_TASK)
            .withIntent(ELEMENT_COMPLETED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    final Record incidentResolveCommand =
        RecordingExporter.incidentRecords()
            .withIntent(RESOLVE)
            .withRecordKey(incidentEvent.getKey())
            .getFirst();

    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentEvent.getKey());
    assertThat(incidentResolveCommand.getPosition())
        .isEqualTo(followUpEvent.getSourceRecordPosition());
    assertThat(incidentResolveCommand.getPosition())
        .isEqualTo(incidentResolvedEvent.getSourceRecordPosition());

    Assertions.assertThat(incidentResolvedEvent.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR.name())
        .hasErrorMessage("No data found for query foo.")
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey());
  }

  @Test
  public void shouldCreateNewIncidentAfterResolvedFirstOne() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "failingTask",
                t -> t.zeebeTaskType("external").zeebeInput("foo", "foo").zeebeInput("bar", "bar"))
            .done();

    ENGINE.deployment().withXmlResource(modelInstance).deploy();
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("process").create();

    final Record failureEvent =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId("failingTask")
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .getFirst();

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    Assertions.assertThat(incidentEvent.getValue()).hasErrorMessage("No data found for query foo.");

    // when
    ENGINE.variables().ofScope(failureEvent.getKey()).withDocument(VARIABLES).update();
    final Record<IncidentRecordValue> resolvedEvent =
        ENGINE.incident().ofInstance(workflowInstanceKey).withKey(incidentEvent.getKey()).resolve();

    // then
    assertThat(resolvedEvent.getKey()).isEqualTo(incidentEvent.getKey());

    final Record<IncidentRecordValue> secondIncidentEvent =
        RecordingExporter.incidentRecords()
            .onlyEvents()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .skipUntil(e -> e.getMetadata().getIntent() == RESOLVED)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    Assertions.assertThat(secondIncidentEvent.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR.name())
        .hasErrorMessage("No data found for query bar.")
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey());
  }

  @Test
  public void shouldResolveIncidentAfterPreviousResolvingFailed() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW_INPUT_MAPPING).deploy();
    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("process").create();

    final Record failureEvent =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("failingTask")
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    final Record firstIncident =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    ENGINE.variables().ofScope(failureEvent.getKey()).withDocument(new HashMap<>()).update();
    ENGINE.incident().ofInstance(workflowInstanceKey).withKey(firstIncident.getKey()).resolve();

    final Record<IncidentRecordValue> secondIncident =
        RecordingExporter.incidentRecords()
            .onlyEvents()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .skipUntil(e -> e.getMetadata().getIntent() == RESOLVED)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    // when
    ENGINE.variables().ofScope(failureEvent.getKey()).withDocument(VARIABLES).update();

    final Record<IncidentRecordValue> secondResolvedIncident =
        ENGINE
            .incident()
            .ofInstance(workflowInstanceKey)
            .withKey(secondIncident.getKey())
            .resolve();

    // then
    assertThat(secondResolvedIncident.getKey()).isGreaterThan(firstIncident.getKey());
    Assertions.assertThat(secondResolvedIncident.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR.name())
        .hasErrorMessage("No data found for query foo.")
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey());
  }

  @Test
  public void shouldResolveMultipleIncidents() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW_INPUT_MAPPING).deploy();

    // create and resolve an first incident
    long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("process").create();
    Record failureEvent =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("failingTask")
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    ENGINE.variables().ofScope(failureEvent.getKey()).withDocument(VARIABLES).update();
    ENGINE.incident().ofInstance(workflowInstanceKey).resolve();

    // create a second incident
    workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("process").create();
    failureEvent =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("failingTask")
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    final long secondIncidentKey =
        RecordingExporter.incidentRecords(CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst()
            .getKey();

    // when
    ENGINE.variables().ofScope(failureEvent.getKey()).withDocument(VARIABLES).update();
    final Record incidentResolvedEvent =
        ENGINE.incident().ofInstance(workflowInstanceKey).resolve();

    // then
    assertThat(incidentResolvedEvent.getKey()).isEqualTo(secondIncidentKey);
  }

  @Test
  public void shouldResolveIncidentIfActivityTerminated() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW_INPUT_MAPPING).deploy();

    final long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("process").create();

    final Record incidentCreatedEvent =
        RecordingExporter.incidentRecords()
            .withIntent(CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    final Record activityTerminated =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("failingTask")
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(WorkflowInstanceIntent.ELEMENT_TERMINATED)
            .getFirst();

    final Record<IncidentRecordValue> incidentResolvedEvent =
        RecordingExporter.incidentRecords()
            .withIntent(RESOLVED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentCreatedEvent.getKey());
    assertThat(activityTerminated.getPosition())
        .isEqualTo(incidentResolvedEvent.getSourceRecordPosition());

    Assertions.assertThat(incidentResolvedEvent.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR.name())
        .hasErrorMessage("No data found for query foo.")
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(incidentResolvedEvent.getValue().getElementInstanceKey())
        .hasVariableScopeKey(incidentResolvedEvent.getValue().getElementInstanceKey());
  }

  @Test
  public void shouldProcessIncidentsAfterMultipleTerminations() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW_INPUT_MAPPING).deploy();

    // create and cancel instance with incident
    long workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("process").create();
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // create and cancel instance without incident
    workflowInstanceKey =
        ENGINE.workflowInstance().ofBpmnProcessId("process").withVariables(VARIABLES_JSON).create();
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // create another instance which creates an incidentworkflowInstanceKey =
    workflowInstanceKey = ENGINE.workflowInstance().ofBpmnProcessId("process").create();
    final Record incidentCreatedEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    // when
    ENGINE.workflowInstance().withInstanceKey(workflowInstanceKey).cancel();

    // then
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withIntent(RESOLVED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    assertThat(incidentEvent.getKey()).isEqualTo(incidentCreatedEvent.getKey());

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR.name())
        .hasErrorMessage("No data found for query foo.")
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(incidentEvent.getValue().getElementInstanceKey())
        .hasVariableScopeKey(incidentEvent.getValue().getElementInstanceKey());
  }
}
