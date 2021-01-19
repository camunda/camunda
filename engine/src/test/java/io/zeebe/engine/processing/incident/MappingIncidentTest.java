/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.incident;

import static io.zeebe.protocol.record.intent.IncidentIntent.CREATED;
import static io.zeebe.protocol.record.intent.IncidentIntent.RESOLVE;
import static io.zeebe.protocol.record.intent.IncidentIntent.RESOLVED;
import static io.zeebe.protocol.record.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Assertions;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.protocol.record.value.IncidentRecordValue;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.collection.Maps;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.HashMap;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class MappingIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final BpmnModelInstance WORKFLOW_INPUT_MAPPING =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask(
              "failingTask", t -> t.zeebeJobType("test").zeebeInputExpression("foo", "foo"))
          .done();
  private static final BpmnModelInstance WORKFLOW_OUTPUT_MAPPING =
      Bpmn.createExecutableProcess("process")
          .startEvent()
          .serviceTask(
              "failingTask", t -> t.zeebeJobType("test").zeebeOutputExpression("foo", "foo"))
          .done();
  private static final Map<String, Object> VARIABLES = Maps.of(entry("foo", "bar"));
  private static final String VARIABLES_JSON =
      "{'string':'value', 'jsonObject':{'testAttr':'test'}}";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

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
            .withIntent(WorkflowInstanceIntent.ACTIVATE_ELEMENT)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();
    final Record activationAttemptEvent =
        RecordingExporter.workflowInstanceRecords()
            .withElementId("failingTask")
            .withIntent(WorkflowInstanceIntent.ELEMENT_ACTIVATING)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();
    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .onlyEvents()
            .withIntent(IncidentIntent.CREATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertThat(incidentEvent.getSourceRecordPosition()).isEqualTo(failureEvent.getPosition());
    assertThat(incidentEvent.getValue().getVariableScopeKey())
        .isEqualTo(activationAttemptEvent.getKey());

    final IncidentRecordValue incidentEventValue = incidentEvent.getValue();
    Assertions.assertThat(incidentEventValue)
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId("process")
        .hasWorkflowKey(workflowKey)
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(activationAttemptEvent.getKey())
        .hasVariableScopeKey(activationAttemptEvent.getKey());

    assertThat(incidentEventValue.getErrorMessage()).contains("no variable found for name 'foo'");
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
                        t.zeebeJobType("external")
                            .zeebeInputExpression("notExisting", "nullVal")
                            .zeebeInputExpression("string", "existing"))
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
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("service")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey());

    assertThat(incidentEvent.getValue().getErrorMessage())
        .contains("no variable found for name 'notExisting'");
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

    final Record<IncidentRecordValue> incidentEvent =
        RecordingExporter.incidentRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    assertThat(incidentEvent.getKey()).isGreaterThan(0);
    assertThat(incidentEvent.getSourceRecordPosition()).isEqualTo(failureEvent.getPosition());

    Assertions.assertThat(incidentEvent.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey());

    assertThat(incidentEvent.getValue().getErrorMessage())
        .contains("no variable found for name 'foo'");
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
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey());

    assertThat(incidentEvent.getValue().getErrorMessage())
        .contains("no variable found for name 'foo'");
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
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey());

    assertThat(incidentResolvedEvent.getValue().getErrorMessage())
        .contains("no variable found for name 'foo'");
  }

  @Test
  public void shouldCreateNewIncidentAfterResolvedFirstOne() {
    // given
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "failingTask",
                t ->
                    t.zeebeJobType("external")
                        .zeebeInputExpression("foo", "foo")
                        .zeebeInputExpression("bar", "bar"))
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

    assertThat(incidentEvent.getValue().getErrorMessage())
        .contains("no variable found for name 'foo'");

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
            .skipUntil(e -> e.getIntent() == RESOLVED)
            .withIntent(IncidentIntent.CREATED)
            .getFirst();

    Assertions.assertThat(secondIncidentEvent.getValue())
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey());

    assertThat(secondIncidentEvent.getValue().getErrorMessage())
        .contains("no variable found for name 'bar'");
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
            .skipUntil(e -> e.getIntent() == RESOLVED)
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
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(failureEvent.getKey())
        .hasVariableScopeKey(failureEvent.getKey());

    assertThat(secondResolvedIncident.getValue().getErrorMessage())
        .contains("no variable found for name 'foo'");
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
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(incidentResolvedEvent.getValue().getElementInstanceKey())
        .hasVariableScopeKey(incidentResolvedEvent.getValue().getElementInstanceKey());

    assertThat(incidentResolvedEvent.getValue().getErrorMessage())
        .contains("no variable found for name 'foo'");
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
        .hasErrorType(ErrorType.IO_MAPPING_ERROR)
        .hasBpmnProcessId("process")
        .hasWorkflowInstanceKey(workflowInstanceKey)
        .hasElementId("failingTask")
        .hasElementInstanceKey(incidentEvent.getValue().getElementInstanceKey())
        .hasVariableScopeKey(incidentEvent.getValue().getElementInstanceKey());

    assertThat(incidentEvent.getValue().getErrorMessage())
        .contains("no variable found for name 'foo'");
  }
}
