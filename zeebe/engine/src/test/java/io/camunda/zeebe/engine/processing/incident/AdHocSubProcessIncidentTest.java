/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.incident;

import static io.camunda.zeebe.model.bpmn.impl.ZeebeConstants.AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AdHocSubProcessBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class AdHocSubProcessIncidentTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String AD_HOC_SUB_PROCESS_ELEMENT_ID = "ad-hoc";
  private static final String AHSP_INNER_ELEMENT_ID =
      "ad-hoc" + AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX;

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  private BpmnModelInstance process(final Consumer<AdHocSubProcessBuilder> modifier) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .adHocSubProcess(AD_HOC_SUB_PROCESS_ELEMENT_ID, modifier)
        .endEvent()
        .done();
  }

  @Test
  public void shouldCreateIncidentIfActiveElementsIsNull() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("activeElements");
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("activeElements", null)
            .create();

    // then
    Assertions.assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
            Failed to activate ad-hoc elements. Expected result of the expression 'activeElements' \
            to be 'ARRAY', but was 'NULL'.""");
  }

  @Test
  public void shouldCreateIncidentIfActiveElementsIsNoListOfStrings() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("activeElements");
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("activeElements", List.of(1))
            .create();

    // then
    Assertions.assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            """
            Failed to activate ad-hoc elements. Expected result of the expression 'activeElements' \
            to be 'ARRAY' containing 'STRING' items, but was 'ARRAY' containing at least one \
            non-'STRING' item.""");
  }

  @Test
  public void shouldCreateIncidentIfActiveElementsContainsInvalidId() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("activeElements");
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("activeElements", List.of("A", "D", "E"))
            .create();

    // then
    final var ahspKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
            .getFirst()
            .getKey();
    Assertions.assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected to activate activities for ad-hoc sub-process with key '%s', but the given elements [D, E] do not exist."
                .formatted(ahspKey));
  }

  @Test
  public void shouldCreateIncidentIfActiveElementsContainsNonStartId() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("activeElements");
              adHocSubProcess.task("A1").task("A2").task("A3");
              adHocSubProcess.task("B");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("activeElements", List.of("A1", "A2", "A3", "B"))
            .create();

    // then
    final var ahspKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
            .getFirst()
            .getKey();
    Assertions.assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected to activate activities for ad-hoc sub-process with key '%d', but the given elements [A2, A3] do not exist."
                .formatted(ahspKey));
  }

  @Test
  public void shouldCreateIncidentIfActiveElementsContainsBoundaryEvent() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("activateElements");
              adHocSubProcess
                  .serviceTask("A", t -> t.zeebeJobType("task"))
                  .boundaryEvent("boundaryEvent", b -> b.error("error"))
                  .intermediateThrowEvent("error");
              adHocSubProcess.task("B");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("activateElements", List.of("boundaryEvent"))
            .create();

    // then
    final var ahspKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
            .getFirst()
            .getKey();
    Assertions.assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .hasElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Expected to activate activities for ad-hoc sub-process with key '%d', but the given elements [boundaryEvent] do not exist."
                .formatted(ahspKey));
  }

  @Test
  public void shouldCreateIncidentIfCompletionConditionDoesNotResolveToBoolean() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("activeElements");
              adHocSubProcess.completionCondition("completionCondition");
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(
                Map.ofEntries(
                    Map.entry("activeElements", List.of("A", "C")),
                    Map.entry("completionCondition", "not a boolean")))
            .create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(AHSP_INNER_ELEMENT_ID)
            .getFirst();

    Assertions.assertThat(incidentCreated.getValue())
        .hasElementId(AHSP_INNER_ELEMENT_ID)
        .hasErrorType(ErrorType.EXTRACT_VALUE_ERROR)
        .hasErrorMessage(
            "Failed to evaluate completion condition. Expected result of the expression 'completionCondition' to be 'BOOLEAN', but was 'STRING'.");

    // when
    ENGINE
        .variables()
        .ofScope(incidentCreated.getValue().getVariableScopeKey())
        .withDocument(Map.of("completionCondition", true))
        .update();

    ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentCreated.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldResolveIncidentWithActiveElements() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("activeElements");
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("activeElements", null)
            .create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
            .getFirst();

    // when
    ENGINE
        .variables()
        .ofScope(incidentCreated.getValue().getVariableScopeKey())
        .withDocument(Map.of("activeElements", List.of("A", "B")))
        .update();

    ENGINE.incident().ofInstance(processInstanceKey).withKey(incidentCreated.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit("B", ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSequence(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(AHSP_INNER_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(AHSP_INNER_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(AHSP_INNER_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(AHSP_INNER_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("B", ProcessInstanceIntent.ACTIVATE_ELEMENT));
  }

  @Test
  public void shouldResolveIncidentIfTerminated() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("activeElements");
              adHocSubProcess.task("A");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("activeElements", null)
            .create();

    final var incidentCreated =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
            .getFirst();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.incidentRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withRecordKey(incidentCreated.getKey())
                .limit(2))
        .extracting(Record::getIntent)
        .contains(IncidentIntent.CREATED, IncidentIntent.RESOLVED);
  }
}
