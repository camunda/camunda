/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class UserTaskFormTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRaiseAnIncidentIfFormIsNotDeployed() {
    final BpmnModelInstance processWithFormId =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .userTask("task")
            .zeebeFormId("form-id")
            .endEvent()
            .done();
    // given
    ENGINE.deployment().withXmlResource(processWithFormId).deploy();

    // when
    ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertIncident();
  }

  private void assertIncident() {
    assertThat(RecordingExporter.incidentRecords().getFirst().getValue())
        .extracting(r -> tuple(r.getErrorType(), r.getErrorMessage()))
        .isEqualTo(
            tuple(
                ErrorType.FORM_NOT_FOUND,
                "Expected to find a form with id 'form-id', but no form with this id it is found, at least a form with this id should be available"));
  }

  private FormRecord sampleFormRecord() {
    return new FormRecord()
        .setFormId("form-id")
        .setVersion(1)
        .setFormKey(1L)
        .setResourceName("form-1.form")
        .setChecksum(wrapString("checksum"))
        .setResource(wrapString("form-resource"));
  }
}
