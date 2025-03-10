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
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessActivityActivationRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessActivityActivationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ActivateAdHocSubProcessActivityTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String AD_HOC_SUB_PROCESS_ELEMENT_ID = "ad-hoc";

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
                })
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(processInstance).deploy();
    processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

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
    final var adHocSubProcessActivityActivationRecord =
        new AdHocSubProcessActivityActivationRecord()
            .setAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey));
    adHocSubProcessActivityActivationRecord.elements().add().setElementId("A");

    ENGINE.writeRecords(
        RecordToWrite.command()
            .adHocSubProcessActivityActivation(adHocSubProcessActivityActivationRecord)
            .key(adHocSubProcessInstanceKey));

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
      givenRunningAdhocSubProcessInstanceWhenSuccessfullyActivatingAElementThenSubsequentActivatedEventIsSent() {
    final var adHocSubProcessActivityActivationRecord =
        new AdHocSubProcessActivityActivationRecord()
            .setAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey));
    adHocSubProcessActivityActivationRecord.elements().add().setElementId("A");

    ENGINE.writeRecords(
        RecordToWrite.command()
            .adHocSubProcessActivityActivation(adHocSubProcessActivityActivationRecord)
            .key(adHocSubProcessInstanceKey));

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
    final var adHocSubProcessActivityActivationRecord =
        new AdHocSubProcessActivityActivationRecord()
            .setAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey));
    adHocSubProcessActivityActivationRecord.elements().add().setElementId("A");
    adHocSubProcessActivityActivationRecord.elements().add().setElementId("B");

    ENGINE.writeRecords(
        RecordToWrite.command()
            .adHocSubProcessActivityActivation(adHocSubProcessActivityActivationRecord)
            .key(adHocSubProcessInstanceKey));

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
    final var adHocSubProcessActivityActivationRecord =
        new AdHocSubProcessActivityActivationRecord()
            .setAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey));
    adHocSubProcessActivityActivationRecord.elements().add().setElementId("does-not-exist");

    ENGINE.writeRecords(
        RecordToWrite.command()
            .adHocSubProcessActivityActivation(adHocSubProcessActivityActivationRecord)
            .key(adHocSubProcessInstanceKey));

    assertThat(
            RecordingExporter.adHocSubProcessActivityActivationRecords()
                .withAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey))
                .onlyCommandRejections()
                .limit(1))
        .extracting(r -> r.getValue().getElements().getFirst().getElementId(), Record::getIntent)
        .describedAs("Expected flow node that doesn't exist to be rejected.")
        .contains(tuple("does-not-exist", AdHocSubProcessActivityActivationIntent.ACTIVATE));
  }

  @Test
  public void
      givenAdhocSubProcessInATerminalStateWhenActivatingElementsThenTheActivationIsRejected() {
    final var adHocSubProcessActivityActivationRecord =
        new AdHocSubProcessActivityActivationRecord()
            .setAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey));
    adHocSubProcessActivityActivationRecord.elements().add().setElementId("A");

    ENGINE.writeRecords(
        RecordToWrite.command()
            .adHocSubProcessActivityActivation(adHocSubProcessActivityActivationRecord)
            .key(adHocSubProcessInstanceKey));

    ENGINE.writeRecords(
        RecordToWrite.command()
            .adHocSubProcessActivityActivation(adHocSubProcessActivityActivationRecord)
            .key(adHocSubProcessInstanceKey));

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
    final var adHocSubProcessActivityActivationRecord =
        new AdHocSubProcessActivityActivationRecord()
            .setAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey));
    adHocSubProcessActivityActivationRecord.elements().add().setElementId("A");
    adHocSubProcessActivityActivationRecord.elements().add().setElementId("A");

    ENGINE.writeRecords(
        RecordToWrite.command()
            .adHocSubProcessActivityActivation(adHocSubProcessActivityActivationRecord)
            .key(adHocSubProcessInstanceKey));

    assertThat(
            RecordingExporter.adHocSubProcessActivityActivationRecords()
                .withAdHocSubProcessInstanceKey(String.valueOf(adHocSubProcessInstanceKey))
                .onlyCommandRejections()
                .limit(1))
        .describedAs(
            "Expected activation to be rejected because duplicate flow nodes are provided.")
        .isNotEmpty();
  }
}
