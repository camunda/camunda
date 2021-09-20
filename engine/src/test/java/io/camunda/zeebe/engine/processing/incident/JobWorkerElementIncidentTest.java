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
import io.camunda.zeebe.engine.util.JobWorkerElementBuilder;
import io.camunda.zeebe.engine.util.JobWorkerElementBuilderProvider;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ZeebeJobWorkerElementBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Collection;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JobWorkerElementIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String TASK_ELEMENT_ID = "task";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameter public JobWorkerElementBuilder elementBuilder;

  @Parameters(name = "{0}")
  public static Collection<Object[]> parameters() {
    return JobWorkerElementBuilderProvider.buildersAsParameters();
  }

  private BpmnModelInstance process(
      final Consumer<ZeebeJobWorkerElementBuilder<?>> elementModifier) {
    final var processBuilder = Bpmn.createExecutableProcess(PROCESS_ID).startEvent();

    // default job type, can be overridden by elementModifier
    final var jobWorkerElementBuilder =
        elementBuilder.build(
            processBuilder, element -> elementModifier.accept(element.zeebeJobType("test")));

    return jobWorkerElementBuilder.id("task").done();
  }

  // ----- JobType related tests
  // --------------------------------------------------------------------------

  @Test
  public void shouldCreateIncidentIfJobTypeExpressionEvaluationFailed() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeJobTypeExpression("x"))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var recordThatLeadsToIncident =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(elementBuilder.getElementType())
            .getFirst();

    // then
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentCreated.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage("failed to evaluate expression 'x': no variable found for name 'x'")
        .hasElementId(TASK_ELEMENT_ID)
        .hasElementInstanceKey(recordThatLeadsToIncident.getKey())
        .hasJobKey(-1L)
        .hasVariableScopeKey(recordThatLeadsToIncident.getKey());
  }

  @Test
  public void shouldCreateIncidentIfJobTypeExpressionOfInvalidType() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeJobTypeExpression("false"))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var recordThatLeadsToIncident =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(elementBuilder.getElementType())
            .getFirst();

    // then
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentCreated.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected result of the expression 'false' to be 'STRING', but was 'BOOLEAN'.")
        .hasElementId(TASK_ELEMENT_ID)
        .hasElementInstanceKey(recordThatLeadsToIncident.getKey())
        .hasJobKey(-1L)
        .hasVariableScopeKey(recordThatLeadsToIncident.getKey());
  }

  @Test
  public void shouldResolveIncidentAfterJobTypeExpressionEvaluationFailed() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeJobTypeExpression("x"))).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when

    // ... update state to resolve issue
    ENGINE
        .variables()
        .ofScope(incidentCreated.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("x", "test")))
        .update();

    // ... resolve incident
    final var incidentResolved =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreated.getKey())
            .resolve();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(TASK_ELEMENT_ID)
                .exists())
        .isTrue();

    assertThat(incidentResolved.getKey()).isEqualTo(incidentCreated.getKey());
  }

  // ----- JobRetries related tests
  // --------------------------------------------------------------------------

  @Test
  public void shouldCreateIncidentIfJobRetriesExpressionEvaluationFailed() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeJobRetriesExpression("x"))).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var recordThatLeadsToIncident =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(elementBuilder.getElementType())
            .getFirst();

    // then
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentCreated.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage("failed to evaluate expression 'x': no variable found for name 'x'")
        .hasElementId(TASK_ELEMENT_ID)
        .hasElementInstanceKey(recordThatLeadsToIncident.getKey())
        .hasJobKey(-1L)
        .hasVariableScopeKey(recordThatLeadsToIncident.getKey());
  }

  @Test
  public void shouldCreateIncidentIfJobRetriesExpressionOfInvalidType() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeJobRetriesExpression("false")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var recordThatLeadsToIncident =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(elementBuilder.getElementType())
            .getFirst();

    // then
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentCreated.getValue())
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected result of the expression 'false' to be 'NUMBER', but was 'BOOLEAN'.")
        .hasElementId(TASK_ELEMENT_ID)
        .hasElementInstanceKey(recordThatLeadsToIncident.getKey())
        .hasJobKey(-1L)
        .hasVariableScopeKey(recordThatLeadsToIncident.getKey());
  }

  @Test
  public void shouldResolveIncidentAfterJobRetriesExpressionEvaluationFailed() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeJobRetriesExpression("x"))).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when

    // ... update state to resolve issue
    ENGINE
        .variables()
        .ofScope(incidentCreated.getValue().getElementInstanceKey())
        .withDocument(Maps.of(entry("x", 3)))
        .update();

    // ... resolve incident
    final var incidentResolved =
        ENGINE
            .incident()
            .ofInstance(processInstanceKey)
            .withKey(incidentCreated.getKey())
            .resolve();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId(TASK_ELEMENT_ID)
                .exists())
        .isTrue();

    assertThat(incidentResolved.getKey()).isEqualTo(incidentCreated.getKey());
  }

  @Test
  public void shouldResolveIncidentAndCreateNewIncidentWhenContinuationFails() {
    // given a deployed process with a service task with an input expression
    ENGINE
        .deployment()
        .withXmlResource(process(t -> t.zeebeInputExpression("unknown_var", "input")))
        .deploy();

    // and an instance of that process is created without a variable for the input expression
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // and an incident created
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when we try to resolve the incident
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentCreated.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.RESOLVED)
                .withProcessInstanceKey(processInstanceKey)
                .withRecordKey(incidentCreated.getKey())
                .exists())
        .describedAs("original incident is resolved")
        .isTrue();

    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .filter(i -> i.getKey() != incidentCreated.getKey())
                .exists())
        .describedAs("a new incident is created")
        .isTrue();
  }

  @Test
  public void shouldResolveIncidentIfTaskIsTerminated() {
    // given
    ENGINE.deployment().withXmlResource(process(t -> t.zeebeJobTypeExpression("x"))).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .incidentRecords()
                .withRecordKey(incidentCreated.getKey()))
        .extracting(Record::getIntent)
        .containsExactly(IncidentIntent.CREATED, IncidentIntent.RESOLVED);
  }
}
