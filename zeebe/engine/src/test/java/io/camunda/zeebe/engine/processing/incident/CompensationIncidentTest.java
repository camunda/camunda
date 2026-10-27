/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CompensationSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class CompensationIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "compensation-process";
  private static final String COMPENSATION_HANDLER_ID = "Undo-A";
  private static final String COMPENSATION_HANDLER_JOB_TYPE = "Undo-A";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private BpmnModelInstance processWithCompensation(
      final Consumer<ServiceTaskBuilder> elementModifier) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .manualTask("A")
        .boundaryEvent()
        .compensation(
            compensation -> {
              final var serviceTask =
                  compensation
                      .serviceTask(COMPENSATION_HANDLER_ID)
                      .zeebeJobType(COMPENSATION_HANDLER_JOB_TYPE);
              elementModifier.accept(serviceTask);
            })
        .moveToActivity("A")
        .endEvent()
        .compensateEventDefinition()
        .done();
  }

  @Test
  public void shouldCreateIncidentForCompensationHandler() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithCompensation(compensationHandler -> {}))
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(COMPENSATION_HANDLER_JOB_TYPE).fail();

    // then
    final var compensationHandlerActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(COMPENSATION_HANDLER_ID)
            .getFirst();

    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    Assertions.assertThat(incidentCreated.getValue())
        .hasErrorType(ErrorType.JOB_NO_RETRIES)
        .hasErrorMessage("No more retries left.")
        .hasElementId(COMPENSATION_HANDLER_ID)
        .hasElementInstanceKey(compensationHandlerActivated.getKey())
        .hasVariableScopeKey(compensationHandlerActivated.getKey())
        .hasJobKey(jobCreated.getKey())
        .hasProcessInstanceKey(processInstanceKey)
        .hasProcessDefinitionKey(compensationHandlerActivated.getValue().getProcessDefinitionKey())
        .hasBpmnProcessId(PROCESS_ID)
        .hasTenantId(compensationHandlerActivated.getValue().getTenantId());
  }

  @Test
  public void shouldCreateIncidentIfCompensationHandlerFails() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithCompensation(compensationHandler -> {}))
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(COMPENSATION_HANDLER_JOB_TYPE).fail();

    // then
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType(COMPENSATION_HANDLER_JOB_TYPE)
        .withRetries(1)
        .updateRetries();
    ENGINE.incident().ofInstance(processInstanceKey).resolve();
    ENGINE.job().ofInstance(processInstanceKey).withType(COMPENSATION_HANDLER_JOB_TYPE).complete();

    assertThat(RecordingExporter.records().limitToProcessInstance(processInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.TRIGGERED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(ValueType.JOB, JobIntent.FAILED),
            tuple(ValueType.INCIDENT, IncidentIntent.CREATED),
            tuple(ValueType.INCIDENT, IncidentIntent.RESOLVED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.COMPLETED));
  }

  @Test
  public void shouldCreateIncidentIfInputMappingsFail() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithCompensation(
                compensationHandler ->
                    compensationHandler.zeebeInputExpression("assert(x, x != null)", "not_null")))
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    ENGINE.variables().ofScope(processInstanceKey).withDocument(Map.of("x", "1")).update();
    ENGINE.incident().ofInstance(processInstanceKey).resolve();
    ENGINE.job().ofInstance(processInstanceKey).withType(COMPENSATION_HANDLER_JOB_TYPE).complete();

    assertThat(RecordingExporter.records().limitToProcessInstance(processInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.TRIGGERED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(ValueType.INCIDENT, IncidentIntent.CREATED),
            tuple(ValueType.INCIDENT, IncidentIntent.RESOLVED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.COMPLETED));
  }

  @Test
  public void shouldCreateIncidentIfOutputMappingsFail() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            processWithCompensation(
                compensationHandler ->
                    compensationHandler.zeebeOutputExpression("assert(x, x != null)", "not_null")))
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType(COMPENSATION_HANDLER_JOB_TYPE).complete();

    // then
    ENGINE.variables().ofScope(processInstanceKey).withDocument(Map.of("x", "1")).update();
    ENGINE.incident().ofInstance(processInstanceKey).resolve();

    assertThat(RecordingExporter.records().limitToProcessInstance(processInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.TRIGGERED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(ValueType.INCIDENT, IncidentIntent.CREATED),
            tuple(ValueType.INCIDENT, IncidentIntent.RESOLVED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.COMPLETED));
  }

  @Test
  public void shouldResolveIncidentIfCompensationHandlerTerminates() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(processWithCompensation(compensationHandler -> {}))
        .deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE.job().ofInstance(processInstanceKey).withType(COMPENSATION_HANDLER_JOB_TYPE).fail();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(RecordingExporter.records().limitToProcessInstance(processInstanceKey))
        .extracting(Record::getValueType, Record::getIntent)
        .containsSubsequence(
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.TRIGGERED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(ValueType.INCIDENT, IncidentIntent.CREATED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.CANCEL),
            tuple(ValueType.COMPENSATION_SUBSCRIPTION, CompensationSubscriptionIntent.DELETED),
            tuple(ValueType.INCIDENT, IncidentIntent.RESOLVED),
            tuple(ValueType.PROCESS_INSTANCE, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }
}
