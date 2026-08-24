/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.adhocsubprocess;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess.AdHocSubProcessInstructionRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BufferedCommandRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class AdHocSubProcessSuspensionGateTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldBufferInternalAdHocSubProcessActivateWhileSuspended() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final long processInstanceKey = deployAndStartProcessWithAdHocSubProcess(processId);
    final long adHocSubProcessInstanceKey = getAdHocSubProcessInstanceKey(processInstanceKey);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when - written directly rather than via the client: RecordToWrite carries no request
    // metadata, so this is an internal command (isInternalCommand() == true), which is the only
    // case that buffers
    ENGINE.writeRecords(
        RecordToWrite.command()
            .adHocSubProcessInstruction(
                AdHocSubProcessInstructionIntent.ACTIVATE,
                activateInstruction(adHocSubProcessInstanceKey)));

    // then - the command is buffered rather than executed
    assertThat(bufferedCommandIntent(processInstanceKey))
        .isEqualTo(AdHocSubProcessInstructionIntent.ACTIVATE);
  }

  @Test
  public void shouldRejectExternalAdHocSubProcessActivateWhileSuspended() {
    // given - external commands are rejected rather than buffered: buffering happens before
    // authorization runs, so a queued external command would replay as internal on drain and skip
    // its authorization check
    final String processId = Strings.newRandomValidBpmnId();
    final long processInstanceKey = deployAndStartProcessWithAdHocSubProcess(processId);
    final long adHocSubProcessInstanceKey = getAdHocSubProcessInstanceKey(processInstanceKey);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when - activate(username) stamps request metadata, marking the command external; the
    // plain activate() carries no request metadata and would be internal, unable to exercise this
    final var rejection =
        ENGINE
            .adHocSubProcessActivity()
            .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
            .withElementIds("A")
            .expectRejection()
            .activate("test-user");

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
  }

  @Test
  public void shouldBufferAdHocSubProcessCompleteWhileSuspended() {
    // given
    final String processId = Strings.newRandomValidBpmnId();
    final long processInstanceKey = deployAndStartProcessWithAdHocSubProcess(processId);
    final long adHocSubProcessInstanceKey = getAdHocSubProcessInstanceKey(processInstanceKey);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();

    // when - COMPLETE is internal-only (JobCompleteProcessor) and unreachable while suspended since
    // JOB.COMPLETE is already rejected, so it is written directly to exercise its gate
    // classification
    ENGINE.writeRecords(
        RecordToWrite.command()
            .adHocSubProcessInstruction(
                AdHocSubProcessInstructionIntent.COMPLETE,
                new AdHocSubProcessInstructionRecord()
                    .setAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)));

    // then - the command is buffered rather than completing activities while suspended
    assertThat(bufferedCommandIntent(processInstanceKey))
        .isEqualTo(AdHocSubProcessInstructionIntent.COMPLETE);
  }

  @Test
  public void shouldDrainAndActivateAdHocSubProcessAfterResume() {
    // given - an activate command buffered while suspended
    final String processId = Strings.newRandomValidBpmnId();
    final long processInstanceKey = deployAndStartProcessWithAdHocSubProcess(processId);
    final long adHocSubProcessInstanceKey = getAdHocSubProcessInstanceKey(processInstanceKey);
    ENGINE.processInstance().withInstanceKey(processInstanceKey).suspend();
    ENGINE.writeRecords(
        RecordToWrite.command()
            .adHocSubProcessInstruction(
                AdHocSubProcessInstructionIntent.ACTIVATE,
                activateInstruction(adHocSubProcessInstanceKey)));
    bufferedCommand(processInstanceKey);

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).resume();

    // then - the drained command activates the requested element
    assertThat(
            RecordingExporter.adHocSubProcessInstructionRecords()
                .withIntent(AdHocSubProcessInstructionIntent.ACTIVATED)
                .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
                .exists())
        .isTrue();
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementId("A")
                .exists())
        .isTrue();
  }

  private static AdHocSubProcessInstructionRecord activateInstruction(
      final long adHocSubProcessInstanceKey) {
    final var instruction =
        new AdHocSubProcessInstructionRecord()
            .setAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey);
    instruction.activateElements().add().setElementId("A");
    return instruction;
  }

  private static AdHocSubProcessInstructionIntent bufferedCommandIntent(
      final long processInstanceKey) {
    return (AdHocSubProcessInstructionIntent)
        ((BufferedCommandRecordValue) bufferedCommand(processInstanceKey).getValue()).getIntent();
  }

  private static Record<RecordValue> bufferedCommand(final long processInstanceKey) {
    return RecordingExporter.records()
        .withValueType(ValueType.BUFFERED_COMMAND)
        .withIntent(BufferedCommandIntent.BUFFERED)
        .filter(
            r ->
                ((BufferedCommandRecordValue) r.getValue()).getProcessInstanceKey()
                    == processInstanceKey)
        .getFirst();
  }

  private long deployAndStartProcessWithAdHocSubProcess(final String processId) {
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .adHocSubProcess(
                "ad-hoc",
                adHocSubProcess -> {
                  adHocSubProcess.task("A");
                  adHocSubProcess.task("B");
                })
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
    return ENGINE.processInstance().ofBpmnProcessId(processId).create();
  }

  private long getAdHocSubProcessInstanceKey(final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
        .getFirst()
        .getKey();
  }
}
