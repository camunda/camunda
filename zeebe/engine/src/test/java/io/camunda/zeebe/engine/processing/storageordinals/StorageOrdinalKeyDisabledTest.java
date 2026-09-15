/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.Rule;
import org.junit.Test;

/**
 * Pins that with archiverless mode disabled the engine selects the disabled provider, so every
 * record carries storage ordinal key {@code 0} and routes to the main index. A fixed key is
 * deliberately configured alongside to prove it is ignored while the mode is off.
 */
public final class StorageOrdinalKeyDisabledTest {

  private static final int FIXED_KEY = 1234;

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(
              c -> c.setArchiverlessEnabled(false).setFixedStorageOrdinalKey(FIXED_KEY));

  @Test
  public void shouldAssignMainIndexKeyWhenArchiverlessIsDisabled() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("disabled-ordinal-process")
                .startEvent()
                .serviceTask("service-task", t -> t.zeebeJobType("disabled-ordinal-job"))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine.processInstance().ofBpmnProcessId("disabled-ordinal-process").create();

    // then
    final var jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(jobCreated.getValue().getStorageOrdinalKey()).isZero();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit("service-task", ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinalKey())
        .containsOnly(0);
  }

  @Test
  public void shouldIgnoreConfiguredFixedKeyWhenArchiverlessIsDisabled() {
    // given
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("ignored-fixed-key-process")
                .startEvent()
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId("ignored-fixed-key-process")
            .withVariable("ordinalVariable", 1)
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .isNotEmpty()
        .extracting(record -> record.getValue().getStorageOrdinalKey())
        .containsOnly(0);

    final var variableCreated =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withName("ordinalVariable")
            .getFirst();
    assertThat(variableCreated.getValue().getStorageOrdinalKey()).isZero();
  }
}
