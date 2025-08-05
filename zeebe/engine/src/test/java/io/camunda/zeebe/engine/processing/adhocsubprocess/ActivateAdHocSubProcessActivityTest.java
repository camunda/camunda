/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.adhocsubprocess;

import static io.camunda.zeebe.model.bpmn.impl.ZeebeConstants.AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.AdHocSubProcessBuilder;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordAssert;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AdHocSubProcessInstructionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.ProcessInstanceRecordStream;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ActivateAdHocSubProcessActivityTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String AD_HOC_SUB_PROCESS_ELEMENT_ID = "ad-hoc";
  private static final String AHSP_INNER_INSTANCE_ELEMENT_ID =
      "ad-hoc" + AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX;
  private static final String COMPLETION_CONDITION_VAR = "completionCondition";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  private long processInstanceKey;
  private long adHocSubProcessInstanceKey;

  @Before
  public void setUp() {
    deployProcess(
        PROCESS_ID,
        adHocSubProcess -> {
          adHocSubProcess.task("A");
          adHocSubProcess.task("B");
          adHocSubProcess.task("C");
          adHocSubProcess.completionCondition(COMPLETION_CONDITION_VAR);
        });

    processInstanceKey = getProcessInstanceKey(PROCESS_ID);
    adHocSubProcessInstanceKey = getAdHocSubProcessInstanceKey(processInstanceKey);
  }

  @Test
  public void shouldActivateElement() {
    ENGINE
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
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
        .doesNotContain(
            tuple("B", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple("C", ProcessInstanceIntent.ACTIVATE_ELEMENT));
  }

  @Test
  public void shouldSetElementTreePath() {
    ENGINE
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
        .withElementIds("A")
        .activate();

    final var activatedInnerInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(AHSP_INNER_INSTANCE_ELEMENT_ID)
            .getFirst();

    final var activatedElementInstance =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("A")
            .getFirst();

    final var expectedElementPath =
        List.of(
            List.of(
                processInstanceKey,
                adHocSubProcessInstanceKey,
                activatedInnerInstance.getKey(),
                activatedElementInstance.getKey()));

    assertThat(activatedElementInstance.getValue().getElementInstancePath())
        .isEqualTo(expectedElementPath);
  }

  @Test
  public void shouldCompleteAdHocSubProcessWhenCompletionConditionIsMet() {
    // prepare the test case by setting the completion condition variable to false which ensures
    // that the ad-hoc sub-process doesn't complete before we want it to.
    ENGINE
        .variables()
        .withDocument(Map.of(COMPLETION_CONDITION_VAR, false))
        .ofScope(adHocSubProcessInstanceKey)
        .update();

    ENGINE
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
        .withElementIds("A")
        .activate();

    final var signalName = "signal";
    ENGINE.signal().withSignalName(signalName).broadcast();

    assertThat(recordsUntilSignal(signalName))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .contains(tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED))
        .doesNotContain(tuple("ad-hoc", ProcessInstanceIntent.ELEMENT_COMPLETED));

    // set the completion condition variable to `true` to ensure that the ad-hoc sub-process
    // completes.
    ENGINE
        .variables()
        .withDocument(Map.of(COMPLETION_CONDITION_VAR, true))
        .ofScope(adHocSubProcessInstanceKey)
        .update();
    ENGINE
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
        .withElementIds("B")
        .activate();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .contains(
            tuple("B", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("ad-hoc", ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldConfirmActivationCommand() {
    ENGINE
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
        .withElementIds("A")
        .activate();

    assertThat(
            RecordingExporter.adHocSubProcessInstructionRecords()
                .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
                .limit(record -> record.getIntent() == AdHocSubProcessInstructionIntent.ACTIVATED))
        .extracting(
            r -> r.getValue().getActivateElements().getFirst().getElementId(), Record::getIntent)
        .contains(tuple("A", AdHocSubProcessInstructionIntent.ACTIVATED));
  }

  @Test
  public void shouldActivateMultipleElements() {
    ENGINE
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
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
        .doesNotContain(tuple("C", ProcessInstanceIntent.ACTIVATE_ELEMENT));
  }

  @Test
  public void shouldRejectCommandIfElementDoesntExist() {
    final var nonExistingActivities = List.of("does_not_exist");
    final var rejection =
        ENGINE
            .adHocSubProcessActivity()
            .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
            .withElementIds(nonExistingActivities.toArray(new String[0]))
            .expectRejection()
            .activate();

    RecordAssert.assertThat(rejection)
        .describedAs("Expected rejection because given activities do not exist.")
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to activate activities for ad-hoc sub-process with key '%s', but the given elements %s do not exist."
                .formatted(adHocSubProcessInstanceKey, nonExistingActivities));

    assertThat(
            RecordingExporter.adHocSubProcessInstructionRecords()
                .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
                .limit(2))
        .extracting(Record::getRecordType, Record::getIntent)
        .contains(
            tuple(RecordType.COMMAND, AdHocSubProcessInstructionIntent.ACTIVATE),
            tuple(RecordType.COMMAND_REJECTION, AdHocSubProcessInstructionIntent.ACTIVATE))
        .doesNotContain(tuple(RecordType.EVENT, AdHocSubProcessInstructionIntent.ACTIVATED));
  }

  @Test
  public void shouldRejectCommandIfAdHocSubProcessInstanceDoesntExist() {
    final var nonExistingAdHocSubProcessInstanceKey = 1L;

    final var rejection =
        ENGINE
            .adHocSubProcessActivity()
            .withAdHocSubProcessInstanceKey(nonExistingAdHocSubProcessInstanceKey)
            .withElementIds("A")
            .expectRejection()
            .activate();

    RecordAssert.assertThat(rejection)
        .describedAs("Expected rejection because ad-hoc sub-process does not exist.")
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to activate activities for ad-hoc sub-process but no ad-hoc sub-process instance found with key '%s'."
                .formatted(nonExistingAdHocSubProcessInstanceKey));

    assertThat(
            RecordingExporter.adHocSubProcessInstructionRecords()
                .withAdHocSubProcessInstanceKey(nonExistingAdHocSubProcessInstanceKey)
                .limit(2))
        .extracting(Record::getRecordType, Record::getIntent)
        .contains(
            tuple(RecordType.COMMAND, AdHocSubProcessInstructionIntent.ACTIVATE),
            tuple(RecordType.COMMAND_REJECTION, AdHocSubProcessInstructionIntent.ACTIVATE));
  }

  @Test
  public void shouldRejectCommandIfAdHocSubProcessInstanceIsNotActive() {
    final var faultyProcessId = "process-rejection-test";
    deployProcess(
        faultyProcessId,
        adHocSubProcess -> {
          adHocSubProcess.task("A");
          adHocSubProcess.task("B");
          adHocSubProcess.task("C");
          // add invalid expression to ensure that processing ends without completing the ad-hoc
          // sub-process
          adHocSubProcess.zeebeOutputExpression("assert(x, x != null)", "y");
        });

    final var faultyProcessInstanceKey = getProcessInstanceKey(faultyProcessId);
    final var faultyAdHocSubProcessInstanceKey =
        getAdHocSubProcessInstanceKey(faultyProcessInstanceKey);

    ENGINE
        .adHocSubProcessActivity()
        .withAdHocSubProcessInstanceKey(faultyAdHocSubProcessInstanceKey)
        .withElementIds("A")
        .activate();

    final var rejection =
        ENGINE
            .adHocSubProcessActivity()
            .withAdHocSubProcessInstanceKey(faultyAdHocSubProcessInstanceKey)
            .withElementIds("A")
            .expectRejection()
            .activate();

    RecordAssert.assertThat(rejection)
        .describedAs("Expected rejection because ad-hoc sub-process is not active.")
        .hasRejectionType(RejectionType.INVALID_STATE)
        .hasRejectionReason(
            "Expected to activate activities for ad-hoc sub-process with key '%s', but it is not active."
                .formatted(faultyAdHocSubProcessInstanceKey));
  }

  @Test
  public void shouldRejectCommandIfElementIsDuplicated() {
    final var rejection =
        ENGINE
            .adHocSubProcessActivity()
            .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
            .withElementIds("A")
            .withElementIds("A")
            .expectRejection()
            .activate();

    RecordAssert.assertThat(rejection)
        .describedAs(
            "Expected activation to be rejected because duplicate flow nodes are provided.")
        .hasRejectionType(RejectionType.INVALID_ARGUMENT)
        .hasRejectionReason(
            "Expected to activate activities for ad-hoc sub-process with key '%s', but duplicate activities were given."
                .formatted(adHocSubProcessInstanceKey));

    assertThat(
            RecordingExporter.adHocSubProcessInstructionRecords()
                .withAdHocSubProcessInstanceKey(adHocSubProcessInstanceKey)
                .limit(2))
        .extracting(Record::getRecordType, Record::getIntent)
        .contains(
            tuple(RecordType.COMMAND, AdHocSubProcessInstructionIntent.ACTIVATE),
            tuple(RecordType.COMMAND_REJECTION, AdHocSubProcessInstructionIntent.ACTIVATE))
        .doesNotContain(tuple(RecordType.EVENT, AdHocSubProcessInstructionIntent.ACTIVATED));
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

  private void deployProcess(
      final String processId, final Consumer<AdHocSubProcessBuilder> modifier) {
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .adHocSubProcess(
                ActivateAdHocSubProcessActivityTest.AD_HOC_SUB_PROCESS_ELEMENT_ID, modifier)
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();
  }

  private long getProcessInstanceKey(final String processId) {
    return ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .withVariables(Map.of(COMPLETION_CONDITION_VAR, true))
        .create();
  }

  private long getAdHocSubProcessInstanceKey(final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
        .map(Record::getKey)
        .limit(1)
        .toList()
        .getFirst();
  }
}
