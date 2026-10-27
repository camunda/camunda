/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn;

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
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class StartEventFormTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String TEST_FORM = "/form/test-form-1.form";
  private static final String FORM_ID = "Form_0w7r08e";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldDeployProcessWithFormIdWhenFormIsNotYetDeployed() {
    // when
    deployProcess("form-id");

    // then
    assertProcessDeployed();
  }

  @Test
  public void shouldDeployProcessWithFormIdWithFormDeployed() {
    // when
    deployForm();
    deployProcess(FORM_ID);

    // then
    assertProcessDeployed();
  }

  @Test
  public void shouldStartAndCompleteProcessWithForm() {
    // given
    deployForm();
    deployProcess(FORM_ID);
    assertProcessDeployed();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertProcessInstanceCompleted(processInstanceKey);
  }

  private void deployProcess(final String formId) {
    final BpmnModelInstance processWithFormId =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().zeebeFormId(formId).endEvent().done();

    ENGINE.deployment().withXmlResource(processWithFormId).deploy();
  }

  private void deployForm() {
    final var deploymentEvent = ENGINE.deployment().withJsonClasspathResource(TEST_FORM).deploy();

    Assertions.assertThat(deploymentEvent)
        .hasIntent(DeploymentIntent.CREATED)
        .hasValueType(ValueType.DEPLOYMENT)
        .hasRecordType(RecordType.EVENT);
  }

  private void assertProcessDeployed() {
    assertThat(
            RecordingExporter.deploymentRecords()
                .limit(r -> r.getIntent() == DeploymentIntent.CREATED))
        .extracting(Record::getRecordType, Record::getIntent)
        .containsSequence(
            tuple(RecordType.COMMAND, DeploymentIntent.CREATE),
            tuple(RecordType.EVENT, DeploymentIntent.CREATED));
  }

  private void assertProcessInstanceCompleted(final long processInstanceKey) {
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(Record::getRecordType, Record::getIntent)
        .containsSequence(
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(RecordType.EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }
}
