/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
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
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class NativeUserTaskFormTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String TEST_FORM_1 = "/form/test-form-1.form";
  private static final String TEST_FORM_1_V2 = "/form/test-form-1_v2.form";
  private static final String TEST_FORM_1_WITH_VERSION_TAG_V1 =
      "/form/test-form-1-with-version-tag-v1.form";
  private static final String TEST_FORM_1_WITH_VERSION_TAG_V1_NEW =
      "/form/test-form-1-with-version-tag-v1-new.form";
  private static final String TEST_FORM_1_WITH_VERSION_TAG_V2 =
      "/form/test-form-1-with-version-tag-v2.form";
  private static final String TEST_FORM_2 = "/form/test-form-2.form";
  private static final String FORM_ID_1 = "Form_0w7r08e";
  private static final String FORM_ID_2 = "Form_6s1b76p";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldActivateUserTaskIfFormIsAlreadyDeployed() {
    // given
    final var form = deployForm(TEST_FORM_1);
    deployProcess(FORM_ID_1);

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertUserTaskActivation(processInstanceKey, form.getFormKey());
  }

  @Test
  public void shouldActivateUserTaskWithLatestFormVersionIfBindingTypeNotSet() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask("task")
            .zeebeFormId(FORM_ID_1)
            .zeebeUserTask()
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).withXmlClasspathResource(TEST_FORM_1).deploy();
    final var latestForm = deployForm(TEST_FORM_1_V2);

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertUserTaskActivation(processInstanceKey, latestForm.getFormKey());
  }

  @Test
  public void shouldActivateUserTaskWithLatestFormVersionForBindingTypeLatest() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask("task")
            .zeebeFormId(FORM_ID_1)
            .zeebeFormBindingType(ZeebeBindingType.latest)
            .zeebeUserTask()
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).withXmlClasspathResource(TEST_FORM_1).deploy();
    final var latestForm = deployForm(TEST_FORM_1_V2);

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertUserTaskActivation(processInstanceKey, latestForm.getFormKey());
  }

  @Test
  public void shouldActivateUserTaskWithFormVersionInSameDeploymentForBindingTypeDeployment() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask("task")
            .zeebeFormId(FORM_ID_1)
            .zeebeFormBindingType(ZeebeBindingType.deployment)
            .zeebeUserTask()
            .endEvent()
            .done();
    final var deployment =
        ENGINE.deployment().withXmlResource(process).withXmlClasspathResource(TEST_FORM_1).deploy();
    final var formInSameDeployment = deployment.getValue().getFormMetadata().getFirst();
    deployForm(TEST_FORM_1_V2);

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertUserTaskActivation(processInstanceKey, formInSameDeployment.getFormKey());
  }

  @Test
  public void shouldActivateUserTaskWithLatestFormVersionWithVersionTagForBindingTypeVersionTag() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask("task")
            .zeebeFormId(FORM_ID_1)
            .zeebeFormBindingType(ZeebeBindingType.versionTag)
            .zeebeFormVersionTag("v1.0")
            .zeebeUserTask()
            .endEvent()
            .done();
    ENGINE
        .deployment()
        .withXmlResource(process)
        .withXmlClasspathResource(TEST_FORM_1_WITH_VERSION_TAG_V1)
        .deploy();
    final var deployedFormV1New = deployForm(TEST_FORM_1_WITH_VERSION_TAG_V1_NEW);
    deployForm(TEST_FORM_1_WITH_VERSION_TAG_V2);
    deployForm(TEST_FORM_1);

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertUserTaskActivation(processInstanceKey, deployedFormV1New.getFormKey());
  }

  @Test
  public void shouldRaiseAnIncidentIfFormIsNotDeployed() {
    // given
    final var formId = "non-existent-form-id";
    deployProcess(formId);

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertFormIncident(
        processInstanceKey,
        """
            Expected to find a form with id '%s', but no form with this id is found, \
            at least a form with this id should be available. \
            To resolve the Incident please deploy a form with the same id"""
            .formatted(formId));
  }

  @Test
  public void shouldResolveAnIncidentIfFormIsDeployed() {
    // given
    deployProcess(FORM_ID_2);

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();
    final var incidentCreated =
        assertFormIncident(
            processInstanceKey,
            """
                Expected to find a form with id '%s', but no form with this id is found, \
                at least a form with this id should be available. \
                To resolve the Incident please deploy a form with the same id"""
                .formatted(FORM_ID_2));

    // when
    final var form = deployForm(TEST_FORM_2);
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentCreated.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.incidentRecords()
                .onlyEvents()
                .limit(r -> r.getIntent() == IncidentIntent.RESOLVED))
        .extracting(Record::getKey, Record::getIntent)
        .describedAs("form not found incident is resolved and no new incident is created")
        .containsExactly(
            tuple(incidentCreated.getKey(), IncidentIntent.CREATED),
            tuple(incidentCreated.getKey(), IncidentIntent.RESOLVED));

    assertUserTaskActivation(processInstanceKey, form.getFormKey());
  }

  @Test
  public void shouldRaiseAnIncidentIfFormIsNotDeployedInSameDeploymentForBindingTypeDeployment() {
    // given
    deployForm(TEST_FORM_1);
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask("task")
            // an incident can only occur at run time if the target form ID is an expression;
            // static IDs are already checked at deploy time
            .zeebeFormId("=formIdVariable")
            .zeebeFormBindingType(ZeebeBindingType.deployment)
            .zeebeUserTask()
            .endEvent()
            .done();
    final var deployment = ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("formIdVariable", FORM_ID_1)
            .create();

    // then
    assertFormIncident(
        processInstanceKey,
        """
        Expected to use a form with id '%s' with binding type 'deployment', \
        but no such form found in the deployment with key %s which contained the current process. \
        To resolve this incident, migrate the process instance to a process definition \
        that is deployed together with the intended form to use.\
        """
            .formatted(FORM_ID_1, deployment.getKey()));
  }

  @Test
  public void shouldRaiseAnIncidentIfFormWithVersionTagIsNotDeployedForBindingTypeVersionTag() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask("task")
            .zeebeFormId(FORM_ID_1)
            .zeebeFormBindingType(ZeebeBindingType.versionTag)
            .zeebeFormVersionTag("v1.0")
            .zeebeUserTask()
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertFormIncident(
        processInstanceKey,
        """
        Expected to use a form with id '%s' and version tag '%s', but no such form found. \
        To resolve the incident, deploy a form with the given id and version tag.
        """
            .formatted(FORM_ID_1, "v1.0"));
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

  private FormMetadataValue deployForm(final String formPath) {
    final var deploymentEvent = ENGINE.deployment().withJsonClasspathResource(formPath).deploy();

    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);

    final var formMetadata = deploymentEvent.getValue().getFormMetadata();
    assertThat(formMetadata).hasSize(1);
    return formMetadata.getFirst();
  }

  private Record<IncidentRecordValue> assertFormIncident(
      final long processInstanceKey, final String expectedErrorMessage) {
    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(incidentCreated.getValue())
        .extracting(r -> tuple(r.getErrorType(), r.getErrorMessage()))
        .isEqualTo(tuple(ErrorType.FORM_NOT_FOUND, expectedErrorMessage));

    return incidentCreated;
  }

  private void assertUserTaskActivation(final long processInstanceKey, final long expectedFormKey) {
    final var userTaskRecord =
        RecordingExporter.userTaskRecords()
            .withProcessInstanceKey(processInstanceKey)
            .limit(1)
            .getFirst()
            .getValue();
    assertThat(userTaskRecord.getFormKey()).isEqualTo(expectedFormKey);

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
