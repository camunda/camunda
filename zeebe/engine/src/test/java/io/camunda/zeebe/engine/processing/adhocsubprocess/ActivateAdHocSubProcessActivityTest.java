/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.adhocsubprocess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordAssert;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessActivityActivationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.ProcessInstanceRecordStream;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ActivateAdHocSubProcessActivityTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String AD_HOC_SUB_PROCESS_ELEMENT_ID = "ad-hoc";
  private static final String COMPLETION_CONDITION_VAR = "completionCondition";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  private long processInstanceKey;
  private long adHocSubProcessInstanceKey;

  @Before
  public void setUp() {
    final BpmnModelInstance processInstance =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .adHocSubProcess(
                AD_HOC_SUB_PROCESS_ELEMENT_ID,
                adHocSubProcess -> {
                  adHocSubProcess.task("A");
                  adHocSubProcess.task("B");
                  adHocSubProcess.task("C");
                  adHocSubProcess.completionCondition(COMPLETION_CONDITION_VAR);
                })
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(processInstance).deploy();
    processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(Map.of(COMPLETION_CONDITION_VAR, true))
            .create();

    adHocSubProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .map(Record::getKey)
            .limit(1)
            .toList()
            .getFirst();
  }

  @Test
  public void
      givenRunningAdhocSubProcessInstanceWhenActivatingExistingElementThenTheElementIsActivated() {
    ENGINE
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey))
        .withElementIds("A")
        .activate();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .contains(
            tuple("A", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple("A", ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .doesNotContainAnyElementsOf(
            List.of(
                tuple("B", ProcessInstanceIntent.ACTIVATE_ELEMENT),
                tuple("C", ProcessInstanceIntent.ACTIVATE_ELEMENT)));
  }

  @Test
  public void
      givenRunningAdhocSubProcessInstanceWhenActivatingExistingElementThenTheElementHasCorrectTreePath() {
    ENGINE
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey))
        .withElementIds("A")
        .activate();

    final var generatedActivityInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.TASK)
            .withElementId("A")
            .map(Record::getKey)
            .limit(1)
            .toList()
            .getFirst();

    final var expectedElementPath =
        List.of(
            List.of(processInstanceKey, adHocSubProcessInstanceKey, generatedActivityInstanceKey));
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), r -> r.getValue().getElementInstancePath())
        .contains(tuple("A", expectedElementPath));
  }

  @Test
  public void
      givenRunningAdhocSubprocessInstanceWhenActivatingElementsThenAdhocSubprocessIsOnlyCompletedWhenCompletionConditionIsMet() {
    // prepare the test case by setting the completion condition variable to false which ensures
    // that the adhoc subprocess doesn't complete before we want it to.
    ENGINE
        .variables()
        .withDocument(Map.of(COMPLETION_CONDITION_VAR, false))
        .ofScope(adHocSubProcessInstanceKey)
        .update();

    ENGINE
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey))
        .withElementIds("A")
        .activate();

    final var signalName = "signal";
    ENGINE.signal().withSignalName(signalName).broadcast();

    assertThat(recordsUntilSignal(signalName))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .contains(tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContainAnyElementsOf(
            List.of(tuple("ad-hoc", ProcessInstanceIntent.ELEMENT_COMPLETED)));

    // set the completion condition variable to `true` to ensure that the adhoc subprocess
    // completes.
    ENGINE
        .variables()
        .withDocument(Map.of(COMPLETION_CONDITION_VAR, true))
        .ofScope(adHocSubProcessInstanceKey)
        .update();
    ENGINE
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey))
        .withElementIds("B")
        .activate();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .contains(
            tuple("B", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("ad-hoc", ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void
      givenRunningAdhocSubProcessInstanceWhenElementIsSuccessfullyActivatedThenActivatedEventIsWrittenToLog() {
    ENGINE
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey))
        .withElementIds("A")
        .activate();

    assertThat(
            RecordingExporter.adHocSubProcessActivityActivationRecords()
                .withAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey))
                .limitToAdHocSubProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElements().getFirst().getElementId(), Record::getIntent)
        .contains(tuple("A", AdHocSubProcessActivityActivationIntent.ACTIVATED));
  }

  @Test
  public void
      givenRunningAdhocSubProcessInstanceWhenActivatingMoreThanOneElementThenAllGivenElementsAreActivated() {
    ENGINE
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey))
        .withElementIds("A")
        .withElementIds("B")
        .activate();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .contains(
            tuple("A", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple("A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("B", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple("B", ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .doesNotContainAnyElementsOf(List.of(tuple("C", ProcessInstanceIntent.ACTIVATE_ELEMENT)));
  }

  @Test
  public void
      givenRunningAdhocSubProcessInstanceWhenActivatingElementThatDoesNotExistThenTheActivationIsRejected() {
    final var nonExistingActivities = List.of("does_not_exist");
    final var rejection =
        ENGINE
            .adHocSubProcessActivity()
            .withAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey))
            .withElementIds(nonExistingActivities.toArray(new String[0]))
            .expectRejection()
            .activate();

    RecordAssert.assertThat(rejection)
        .describedAs("Expected rejection because given activities do not exist.")
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to activate activities for ad-hoc subprocess with key '%s', but the given elements %s do not exist."
                .formatted(adHocSubProcessInstanceKey, nonExistingActivities));

    assertThat(
            RecordingExporter.adHocSubProcessActivityActivationRecords()
                .withAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey))
                .withIntent(AdHocSubProcessActivityActivationIntent.ACTIVATED)
                .exists())
        .isFalse();
  }

  @Test
  public void
      givenAdhocSubProcessThatDoesNotExistWhenActivatingElementsThenTheActivationIsRejected() {
    final var nonExistingAdhocSubProcessInstanceKey = "1";

    final var rejection =
        ENGINE
            .adHocSubProcessActivity()
            .withAdHocSubProcessInstanceKey(nonExistingAdhocSubProcessInstanceKey)
            .withElementIds("A")
            .expectRejection()
            .activate();

    RecordAssert.assertThat(rejection)
        .describedAs("Expected rejection because adhoc subprocess does not exist.")
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to activate activities for ad-hoc subprocess but no ad-hoc subprocess instance found with key '%s'."
                .formatted(nonExistingAdhocSubProcessInstanceKey));

    assertThat(
            RecordingExporter.adHocSubProcessActivityActivationRecords()
                .withAdHocSubProcessInstanceKey(nonExistingAdhocSubProcessInstanceKey)
                .onlyCommandRejections()
                .limit(1))
        .describedAs(
            "Expected activation to be rejected because the adhoc subprocess instance does not exist.")
        .isNotEmpty();
  }

  @Test
  public void givenAdhocSubProcessInFinalStateWhenActivatingElementsThenTheActivationIsRejected() {
    ENGINE
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey))
        .withElementIds("A")
        .activate();

    ENGINE
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey))
        .withElementIds("A")
        .expectRejection()
        .activate();

    assertThat(
            RecordingExporter.adHocSubProcessActivityActivationRecords()
                .withAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey))
                .onlyCommandRejections()
                .limit(1))
        .describedAs(
            "Expected activation to be rejected because the adhoc subprocess has completed.")
        .isNotEmpty();
  }

  @Test
  public void
      givenRunningAdhocSubProcessWhenAttemptingToActivateDuplicateElementsThenTheActivationIsRejected() {
    final var rejection =
        ENGINE
            .adHocSubProcessActivity()
            .withAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey))
            .withElementIds("A")
            .withElementIds("A")
            .expectRejection()
            .activate();

    RecordAssert.assertThat(rejection)
        .describedAs(
            "Expected activation to be rejected because duplicate flow nodes are provided.")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to activate activities for ad-hoc subprocess with key '%s', but duplicate activities were given."
                .formatted(adHocSubProcessInstanceKey));

    assertThat(
            RecordingExporter.adHocSubProcessActivityActivationRecords()
                .withAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey))
                .withIntent(AdHocSubProcessActivityActivationIntent.ACTIVATED)
                .exists())
        .isFalse();
  }

  private ProcessInstanceRecordStream recordsUntilSignal(final String signalName) {
    return RecordingExporter.records()
        .limit(
            r ->
                r.getIntent() == SignalIntent.BROADCASTED
                    && ((SignalRecord) r.getValue()).getSignalName().equals(signalName))
        .processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey);
  }
}
