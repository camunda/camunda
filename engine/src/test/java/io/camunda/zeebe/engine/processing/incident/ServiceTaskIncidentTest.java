/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ServiceTaskIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String SERVICE_TASK_ID = "task";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private static BpmnModelInstance createBPMNModel(final Consumer<ServiceTaskBuilder> consumer) {
    final var builder =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().serviceTask(SERVICE_TASK_ID);

    builder.zeebeJobType("test"); // default job type, can be overridden by consumer

    consumer.accept(builder);

    return builder.endEvent().done();
  }

  // ----- JobType related tests
  // --------------------------------------------------------------------------

  @Test
  public void shouldCreateIncidentIfJobTypeExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            createBPMNModel(
                t ->
                    t.zeebeJobTypeExpression(
                        "lorem.ipsum"))) // invalid expression, will fail at runtime
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<ProcessInstanceRecordValue> recordThatLeadsToIncident =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    // then
    final Record<IncidentRecordValue> incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentRecord.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "failed to evaluate expression 'lorem.ipsum': no variable found for name 'lorem'")
        .hasElementId(SERVICE_TASK_ID)
        .hasElementInstanceKey(recordThatLeadsToIncident.getKey())
        .hasJobKey(-1L)
        .hasVariableScopeKey(recordThatLeadsToIncident.getKey());
  }

  @Test
  public void shouldCreateIncidentIfJobTypeExpressionOfInvalidType() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            createBPMNModel(
                t -> t.zeebeJobTypeExpression("false"))) // boolean expression, has wrong type
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<ProcessInstanceRecordValue> recordThatLeadsToIncident =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    // then
    final Record<IncidentRecordValue> incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentRecord.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected result of the expression 'false' to be 'STRING', but was 'BOOLEAN'.")
        .hasElementId(SERVICE_TASK_ID)
        .hasElementInstanceKey(recordThatLeadsToIncident.getKey())
        .hasJobKey(-1L)
        .hasVariableScopeKey(recordThatLeadsToIncident.getKey());
  }

  @Test
  public void shouldResolveIncidentAfterJobTypeExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            createBPMNModel(
                t -> t.zeebeJobTypeExpression("lorem"))) // invalid expression, will fail at runtime
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<IncidentRecordValue> incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when

    // ... update state to resolve issue
    ENGINE
        .variables()
        .ofScope(incidentRecord.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("lorem", "order123")))
        .update();

    // ... resolve incident
    final Record<IncidentRecordValue> incidentResolvedEvent =
        ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentRecord.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(SERVICE_TASK_ID)
                .exists())
        .isTrue();

    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentRecord.getKey());
  }

  // ----- JobRetries related tests
  // --------------------------------------------------------------------------

  @Test
  public void shouldCreateIncidentIfJobRetriesExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            createBPMNModel(
                t ->
                    t.zeebeJobRetriesExpression(
                        "lorem.ipsum"))) // invalid expression, will fail at runtime
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<ProcessInstanceRecordValue> recordThatLeadsToIncident =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    // then
    final Record<IncidentRecordValue> incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentRecord.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "failed to evaluate expression 'lorem.ipsum': no variable found for name 'lorem'")
        .hasElementId(SERVICE_TASK_ID)
        .hasElementInstanceKey(recordThatLeadsToIncident.getKey())
        .hasJobKey(-1L)
        .hasVariableScopeKey(recordThatLeadsToIncident.getKey());
  }

  @Test
  public void shouldCreateIncidentIfJobRetriesExpressionOfInvalidType() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            createBPMNModel(
                t -> t.zeebeJobRetriesExpression("false"))) // boolean expression, has wrong type
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<ProcessInstanceRecordValue> recordThatLeadsToIncident =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    // then
    final Record<IncidentRecordValue> incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentRecord.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected result of the expression 'false' to be 'NUMBER', but was 'BOOLEAN'.")
        .hasElementId(SERVICE_TASK_ID)
        .hasElementInstanceKey(recordThatLeadsToIncident.getKey())
        .hasJobKey(-1L)
        .hasVariableScopeKey(recordThatLeadsToIncident.getKey());
  }

  @Test
  public void shouldResolveIncidentAfterJobRetriesExpressionEvaluationFailed() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            createBPMNModel(
                t ->
                    t.zeebeJobRetriesExpression(
                        "lorem"))) // invalid expression, will fail at runtime
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final Record<IncidentRecordValue> incidentRecord =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when

    // ... update state to resolve issue
    ENGINE
        .variables()
        .ofScope(incidentRecord.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("lorem", 3)))
        .update();

    // ... resolve incident
    final Record<IncidentRecordValue> incidentResolvedEvent =
        ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentRecord.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(SERVICE_TASK_ID)
                .exists())
        .isTrue();

    assertThat(incidentResolvedEvent.getKey()).isEqualTo(incidentRecord.getKey());
  }

  @Test
  public void shouldResolveIncidentAndCreateNewIncidentWhenContinuationFails() {
    // given a deployed process with a service task with an input expression
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process-incident-on-leaving-task")
                .startEvent()
                .serviceTask(
                    "task",
                    b -> b.zeebeJobType("task").zeebeInputExpression("unknown_var", "input"))
                .done())
        .deploy();

    // and an instance of that process is created without a variable for the input expression
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId("process-incident-on-leaving-task").create();

    // and an incident created
    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when we try to resolve the incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
                .withProcessInstanceKey(processInstanceKey)
                .withRecordKey(incident.getKey())
                .exists())
        .describedAs("original incident is resolved")
        .isTrue();

    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .filter(i -> i.getKey() != incident.getKey())
                .exists())
        .describedAs("a new incident is created")
        .isTrue();
  }
}
