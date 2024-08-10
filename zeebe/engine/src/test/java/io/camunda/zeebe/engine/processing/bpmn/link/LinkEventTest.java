/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.link;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class LinkEventTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "wf";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldTriggerEvent() {
    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(PROCESS_ID);
    processBuilder.startEvent().intermediateThrowEvent("throw", b -> b.link("linkA"));
    final BpmnModelInstance process =
        processBuilder.linkCatchEvent("catch").link("linkA").manualTask().endEvent().done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.INTERMEDIATE_THROW_EVENT, ELEMENT_COMPLETING),
            tuple(BpmnElementType.INTERMEDIATE_THROW_EVENT, ELEMENT_COMPLETED),
            tuple(BpmnElementType.INTERMEDIATE_CATCH_EVENT, ELEMENT_COMPLETING),
            tuple(BpmnElementType.INTERMEDIATE_CATCH_EVENT, ELEMENT_COMPLETED),
            tuple(BpmnElementType.MANUAL_TASK, ELEMENT_COMPLETING),
            tuple(BpmnElementType.MANUAL_TASK, ELEMENT_COMPLETED),
            tuple(BpmnElementType.END_EVENT, ELEMENT_COMPLETING),
            tuple(BpmnElementType.END_EVENT, ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ELEMENT_COMPLETING),
            tuple(BpmnElementType.PROCESS, ELEMENT_COMPLETED));
  }

  @Test
  public void shouldTriggerEventFromMultipleSources() {
    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(PROCESS_ID);
    processBuilder
        .startEvent()
        .parallelGateway("parallel")
        .intermediateThrowEvent("throwA", b -> b.link("link"))
        .moveToLastGateway()
        .intermediateThrowEvent("throwB", b -> b.link("link"));

    final BpmnModelInstance process =
        processBuilder.linkCatchEvent("catch").link("link").manualTask().endEvent().done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ELEMENT_COMPLETED)
                .limitToProcessInstanceCompleted()
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.INTERMEDIATE_CATCH_EVENT)
                .count())
        .isEqualTo(2);

    assertThat(
            RecordingExporter.processInstanceRecords(ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .describedAs("Expect to complete the process instance")
        .isTrue();
  }

  @Test
  public void shouldTriggerEventWithDifferentLinkEvents() {
    // given
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(PROCESS_ID);
    processBuilder.startEvent().intermediateThrowEvent("throwA", b -> b.link("linkA"));
    processBuilder
        .linkCatchEvent("catchA")
        .link("linkA")
        .intermediateThrowEvent("throwB", b -> b.link("linkB"));

    final BpmnModelInstance process =
        processBuilder.linkCatchEvent("catchB").link("linkB").endEvent("end").done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final var processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("throwA", ELEMENT_COMPLETING),
            tuple("throwA", ELEMENT_COMPLETED),
            tuple("catchA", ELEMENT_COMPLETING),
            tuple("catchA", ELEMENT_COMPLETED),
            tuple("throwB", ELEMENT_COMPLETING),
            tuple("throwB", ELEMENT_COMPLETED),
            tuple("catchB", ELEMENT_COMPLETING),
            tuple("catchB", ELEMENT_COMPLETED),
            tuple("end", ELEMENT_COMPLETING),
            tuple("end", ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ELEMENT_COMPLETING),
            tuple(PROCESS_ID, ELEMENT_COMPLETED));
  }
}
