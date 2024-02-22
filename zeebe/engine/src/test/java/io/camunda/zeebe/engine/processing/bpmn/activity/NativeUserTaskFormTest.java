/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class NativeUserTaskFormTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String TEST_FORM_1 = "/form/test-form-1.form";
  private static final String TEST_FORM_2 = "/form/test-form-2.form";
  private static final String FORM_ID_1 = "Form_0w7r08e";
  private static final String FORM_ID_2 = "Form_6s1b76p";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldActivateUserTaskIfFormIsAlreadyDeployed() {
    // given
    deployForm(TEST_FORM_1);
    deployProcess(FORM_ID_1);

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertUserTaskActivation(processInstanceKey);
  }

  @Test
  public void shouldRaiseAnIncidentIfFormIsNotDeployed() {
    // given
    final var formId = "non-existent-form-id";
    deployProcess(formId);

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertFormIncident(processInstanceKey, formId);
  }

  @Test
  public void shouldResolveAnIncidentIfFormIsDeployed() {
    // given
    deployProcess(FORM_ID_2);

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var incidentCreated = assertFormIncident(processInstanceKey, FORM_ID_2);

    // when
    deployForm(TEST_FORM_2);
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentCreated.getKey()).resolve();

    // then
    assertThat(RecordingExporter.incidentRecords().onlyEvents())
        .extracting(Record::getKey, Record::getIntent)
        .describedAs("form not found incident is resolved and no new incident is created")
        .containsExactly(
            tuple(incidentCreated.getKey(), IncidentIntent.CREATED),
            tuple(incidentCreated.getKey(), IncidentIntent.RESOLVED));

    assertUserTaskActivation(processInstanceKey);
  }

  private void deployProcess(final String formId) {
    final BpmnModelInstance processWithFormId =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask("task")
            .zeebeFormId(formId)
            .zeebeUserTask()
            .endEvent()
            .done();
    // given
    ENGINE.deployment().withXmlResource(processWithFormId).deploy();
  }

  private void deployForm(final String formPath) {
    final var deploymentEvent = ENGINE.deployment().withJsonClasspathResource(formPath).deploy();

    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);
  }

  private Record<IncidentRecordValue> assertFormIncident(
      final long processInstanceKey, final String formId) {
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(incidentCreated.getValue())
        .extracting(r -> tuple(r.getErrorType(), r.getErrorMessage()))
        .isEqualTo(
            tuple(
                ErrorType.FORM_NOT_FOUND,
                String.format(
                    "Expected to find a form with id '%s', but no form with this id is found, at least a form with this id should be available. To resolve the Incident please deploy a form with the same id",
                    formId)));

    return incidentCreated;
  }

  private void assertUserTaskActivation(final long processInstanceKey) {
    assertThat(
            RecordingExporter.userTaskRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(1)
                .getFirst()
                .getValue()
                .getFormKey())
        .isGreaterThan(-1L);

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.USER_TASK)
                .limit(3))
        .extracting(Record::getRecordType, Record::getIntent)
        .containsSequence(
            tuple(RecordType.COMMAND, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }
}
