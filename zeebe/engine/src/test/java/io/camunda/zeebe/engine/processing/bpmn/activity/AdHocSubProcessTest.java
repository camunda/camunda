/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.model.bpmn.impl.ZeebeConstants.AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX;
import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AdHocSubProcessBuilder;
import io.camunda.zeebe.protocol.impl.record.value.signal.SignalRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class AdHocSubProcessTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String AD_HOC_SUB_PROCESS_ELEMENT_ID = "ad-hoc";
  private static final String AHSP_INNER_INSTANCE_ELEMENT_ID =
      "ad-hoc" + AD_HOC_SUB_PROCESS_INNER_INSTANCE_ID_POSTFIX;
  private static final String AD_HOC_SUB_PROCESS_ELEMENTS_VARIABLE = "adHocSubProcessElements";

  @Rule public final RecordingExporterTestWatcher watcher = new RecordingExporterTestWatcher();

  private BpmnModelInstance process(final Consumer<AdHocSubProcessBuilder> modifier) {
    return Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .adHocSubProcess(AD_HOC_SUB_PROCESS_ELEMENT_ID, modifier)
        .endEvent()
        .done();
  }

  @Test
  public void shouldDeployProcess() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A1").task("A2");
              adHocSubProcess.task("B");
            });

    // when
    final Record<DeploymentRecordValue> deploymentEvent =
        ENGINE.deployment().withXmlResource(process).deploy();

    // then
    assertThat(deploymentEvent).hasRecordType(RecordType.EVENT).hasIntent(DeploymentIntent.CREATED);
  }

  @Test
  public void shouldActivateAdHocSubProcess() {
    // given
    final BpmnModelInstance process = process(adHocSubProcess -> adHocSubProcess.task("A"));

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSequence(
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED));

    final var activatedEvent =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
            .getFirst();

    Assertions.assertThat(activatedEvent.getValue())
        .hasElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
        .hasBpmnElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
        .hasBpmnEventType(BpmnEventType.UNSPECIFIED)
        .hasFlowScopeKey(processInstanceKey);
  }

  @Test
  public void shouldActivateElements() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("activateElements");
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
            .withVariable("activateElements", List.of("A", "C"))
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit("C", ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("C", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple("A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("C", ProcessInstanceIntent.ELEMENT_ACTIVATED));

    final var adHocSubProcessActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
            .getFirst();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withFlowScopeKey(adHocSubProcessActivated.getKey())
                .limit(2))
        .describedAs("Has activated two inner instances")
        .hasSize(2)
        .extracting(r -> r.getValue().getElementId())
        .containsOnly(AHSP_INNER_INSTANCE_ELEMENT_ID);

    final var innerInstanceKeys =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(AHSP_INNER_INSTANCE_ELEMENT_ID)
            .limit(2)
            .map(Record::getKey)
            .toList();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .filter(record -> innerInstanceKeys.contains(record.getValue().getFlowScopeKey())))
        .extracting(r -> r.getValue().getElementId())
        .containsOnly("A", "C");
  }

  @Test
  public void shouldActivateNoElementsIfListIsEmpty() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("activateElements");
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
            .withVariable("activateElements", Collections.emptyList())
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSequence(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldActivateNoElementsIfExpressionIsEmpty() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("");
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSequence(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldCompleteAdHocSubProcess() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("activateElements");
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
            .withVariable("activateElements", List.of("A", "C"))
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("C", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompleteAdHocSubProcessWhenCompletionConditionApplies() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("activateElements");
              adHocSubProcess.completionCondition("condition");
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
            .withVariables(Map.of("activateElements", List.of("A", "C"), "condition", true))
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("C", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCompleteAdHocSubProcessWhenCompletionConditionAppliesAfterCompletingActivity() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("activateElements");
              adHocSubProcess.completionCondition("condition");
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.serviceTask("ServiceTask", b -> b.zeebeJobType("testType"));
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(
                Map.of("activateElements", List.of("A", "ServiceTask"), "condition", false))
            .create();

    // helps to stop at a specific point after the ad-hoc sub-process is activated
    ENGINE.signal().withSignalName("signal").broadcast();

    assertThat(
            RecordingExporter.records()
                .limit(signalBroadcasted("signal"))
                .processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .describedAs(
            "Expect task A to complete while service task is activated and prevents sub-process completion")
        .containsSubsequence(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("ServiceTask", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .describedAs(
            "Expect service task not to be terminated because completion condition does not apply")
        .doesNotContain(
            tuple("ServiceTask", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));

    // complete service task + update condition variable
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("testType")
        .withVariable("condition", true)
        .complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .describedAs("Expect ad-hoc sub-process to complete after service task is completed")
        .containsSubsequence(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldNotCompleteAdHocSubProcessWhenCompletionConditionDoesNotApply() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("activateElements");
              adHocSubProcess.completionCondition("condition");
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
            .withVariables(Map.of("activateElements", List.of("A", "C"), "condition", false))
            .create();

    // helps to stop at a specific point after the ad-hoc sub-process is activated
    ENGINE.signal().withSignalName("signal").broadcast();

    // then
    assertThat(
            RecordingExporter.records()
                .limit(signalBroadcasted("signal"))
                .processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .describedAs("Expect activated activities to be completed")
        .containsSubsequence(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("C", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .describedAs("Expect ad-hoc sub-process to not complete when condition does not apply")
        .doesNotContain(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCancelRemainingInstancesWhenCancellationIsEnabled() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("activateElements");
              adHocSubProcess.completionCondition("condition");
              adHocSubProcess.cancelRemainingInstances(true);
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.serviceTask("ServiceTask", b -> b.zeebeJobType("testType")).task("C");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(
                Map.of("activateElements", List.of("A", "ServiceTask"), "condition", true))
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .describedAs("Expect ad-hoc sub-process to complete and to terminate service task")
        .containsSubsequence(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("ServiceTask", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.TERMINATE_ELEMENT),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple("ServiceTask", ProcessInstanceIntent.TERMINATE_ELEMENT),
            tuple("ServiceTask", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .describedAs(
            "Expect service task and dependent activity to never complete as it is terminated")
        .doesNotContain(
            tuple("ServiceTask", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("C", ProcessInstanceIntent.ELEMENT_ACTIVATED));
  }

  @Test
  public void shouldNotCancelRemainingInstancesWhenCancellationIsDisabled() {
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("activateElements");
              adHocSubProcess.completionCondition("condition");
              adHocSubProcess.cancelRemainingInstances(false);
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.serviceTask("ServiceTask", b -> b.zeebeJobType("testType")).task("C");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    // create instance and wait until ad-hoc sub-process blocks on ServiceTask being completed
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables(
                Map.of("activateElements", List.of("A", "ServiceTask"), "condition", true))
            .create();

    // helps to stop at a specific point after the ad-hoc sub-process is activated
    ENGINE.signal().withSignalName("signal").broadcast();

    // expect process not to complete until service task is completed
    assertThat(
            RecordingExporter.records()
                .limit(signalBroadcasted("signal"))
                .processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .describedAs("Expect service task be activated and ad-hoc sub-process to not complete")
        .containsSubsequence(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("ServiceTask", ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED))
        .describedAs(
            "Expect service task not to be terminated because cancelRemainingInstances is false")
        .doesNotContain(
            tuple("ServiceTask", ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));

    // complete service task
    ENGINE.job().ofInstance(processInstanceKey).withType("testType").complete();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .describedAs(
            "Expect ad-hoc sub-process to complete after service task is completed and condition applies")
        .containsSubsequence(
            tuple("ServiceTask", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldApplyInputMappings() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess
                  .zeebeInputExpression("append(activateElements, \"B\")", "activateElements")
                  .zeebeActiveElementsCollectionExpression("activateElements");
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
            .withVariable("activateElements", List.of("A"))
            .create();

    // then
    final Record<ProcessInstanceRecordValue> adHocSubProcessActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .getFirst();

    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .filter(v -> v.getValue().getName().equals("activateElements"))
                .limit(2))
        .extracting(Record::getValue)
        .extracting(
            VariableRecordValue::getName,
            VariableRecordValue::getValue,
            VariableRecordValue::getScopeKey)
        .contains(
            tuple("activateElements", "[\"A\"]", processInstanceKey),
            tuple("activateElements", "[\"A\",\"B\"]", adHocSubProcessActivated.getKey()));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit("B", ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSequence(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("B", ProcessInstanceIntent.ACTIVATE_ELEMENT));
  }

  @Test
  public void shouldApplyOutputMappings() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess
                  .zeebeInputExpression("[]", "result")
                  .zeebeActiveElementsCollectionExpression("activateElements")
                  .zeebeOutputExpression("result", "adHocResult");

              adHocSubProcess
                  .serviceTask("A")
                  .zeebeJobType("A")
                  .zeebeOutputExpression("append(result, 1)", "result");

              adHocSubProcess
                  .serviceTask("B")
                  .zeebeJobType("B")
                  .zeebeOutputExpression("append(result, 2)", "result");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("activateElements", List.of("A", "B"))
            .create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("A").complete();
    ENGINE.job().ofInstance(processInstanceKey).withType("B").complete();

    // then
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords())
        .extracting(Record::getValue)
        .extracting(
            VariableRecordValue::getName,
            VariableRecordValue::getValue,
            VariableRecordValue::getScopeKey)
        .contains(tuple("adHocResult", "[1,2]", processInstanceKey));
  }

  @Test
  public void shouldCreateLocalAdHocSubProcessElementsVariable() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.task("A");
            });

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("activateElements", List.of("A"))
            .create();

    final var adHocSubProcessKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(AD_HOC_SUB_PROCESS_ELEMENT_ID)
            .getFirst()
            .getKey();

    // then
    // actual variable assertions are done in detail in AdHocSubProcessElementsVariableTest
    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .filter(
                    v ->
                        v.getIntent() == VariableIntent.CREATED
                            && v.getValue().getName().equals(AD_HOC_SUB_PROCESS_ELEMENTS_VARIABLE))
                .limit(1))
        .first()
        .describedAs(
            "Variable adHocSubProcessElements should be created as local variable in sub-process scope")
        .satisfies(
            variableRecord ->
                assertThat(variableRecord.getValue()).hasScopeKey(adHocSubProcessKey));
  }

  @Test
  public void shouldInvokeStartExecutionListener() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess
                  .zeebeStartExecutionListener("start-EL")
                  .zeebeActiveElementsCollectionExpression("activateElements");
              adHocSubProcess.task("A");
              adHocSubProcess.task("B");
              adHocSubProcess.task("C");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("start-EL")
        .withVariable("activateElements", List.of("A", "B"))
        .complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limit("B", ProcessInstanceIntent.ELEMENT_ACTIVATED))
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSequence(
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(AD_HOC_SUB_PROCESS_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", ProcessInstanceIntent.ACTIVATE_ELEMENT),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(AHSP_INNER_INSTANCE_ELEMENT_ID, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("B", ProcessInstanceIntent.ACTIVATE_ELEMENT));

    final Record<VariableRecordValue> variableCreated =
        RecordingExporter.variableRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withName("activateElements")
            .getFirst();

    assertThat(variableCreated.getValue()).hasValue("[\"A\",\"B\"]");
  }

  @Test
  public void shouldInvokeEndExecutionListener() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess
                  .zeebeActiveElementsCollectionExpression("activateElements")
                  .zeebeEndExecutionListener("end-EL");
              adHocSubProcess.task("A");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("activateElements", List.of("A"))
            .create();

    // when
    ENGINE.job().ofInstance(processInstanceKey).withType("end-EL").complete();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                BpmnElementType.AD_HOC_SUB_PROCESS_INNER_INSTANCE,
                ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(
                BpmnElementType.AD_HOC_SUB_PROCESS,
                ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldInterruptAdHocSubProcess() {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .adHocSubProcess(
                AD_HOC_SUB_PROCESS_ELEMENT_ID, adHocSubProcess -> adHocSubProcess.task("A"))
            .boundaryEvent()
            .timerWithDuration(Duration.ZERO)
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.BOUNDARY_EVENT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.END_EVENT, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCancelAdHocSubProcess() {
    // given
    final BpmnModelInstance process = process(adHocSubProcess -> adHocSubProcess.task("A"));

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
        .await();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldCancelAdHocSubProcessAndTerminateChildInstances() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("[\"A\"]");
              adHocSubProcess.userTask("A");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withProcessInstanceKey(processInstanceKey)
        .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
        .await();

    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceTerminated())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(
                BpmnElementType.AD_HOC_SUB_PROCESS_INNER_INSTANCE,
                ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(
                BpmnElementType.AD_HOC_SUB_PROCESS_INNER_INSTANCE,
                ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_TERMINATED));
  }

  @Test
  public void shouldCompleteMultiInstanceAdHocSubProcess() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess
                  .multiInstance()
                  .zeebeInputCollectionExpression("activateElements")
                  .zeebeInputElement("element");
              adHocSubProcess.zeebeActiveElementsCollectionExpression("[element]");
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
            .withVariable("activateElements", List.of("A", "B", "C"))
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getBpmnElementType(), Record::getIntent)
        .containsSubsequence(
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            // then complete:
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.AD_HOC_SUB_PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));

    final List<Long> adHocSubProcessInnerInstanceKeys =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS_INNER_INSTANCE)
            .limit(3)
            .map(Record::getKey)
            .toList();

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted()
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementType(BpmnElementType.TASK))
        .extracting(Record::getValue)
        .extracting(
            ProcessInstanceRecordValue::getElementId, ProcessInstanceRecordValue::getFlowScopeKey)
        .hasSize(3)
        .containsExactly(
            tuple("A", adHocSubProcessInnerInstanceKeys.get(0)),
            tuple("B", adHocSubProcessInnerInstanceKeys.get(1)),
            tuple("C", adHocSubProcessInnerInstanceKeys.get(2)));
  }

  @Test
  public void shouldActivateMultiInstanceElement() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess.zeebeActiveElementsCollectionExpression("activateElements");
              adHocSubProcess.task("A").multiInstance().zeebeInputCollectionExpression("[1,2]");
              adHocSubProcess.task("B");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("activateElements", List.of("A"))
            .create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .limitToProcessInstanceCompleted())
        .extracting(
            r -> r.getValue().getElementId(),
            r -> r.getValue().getBpmnElementType(),
            Record::getIntent)
        .containsSubsequence(
            tuple(
                "A", BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", BpmnElementType.TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", BpmnElementType.TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("A", BpmnElementType.TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", BpmnElementType.TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                "A", BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldAppendOutputCollection() {
    // given
    final BpmnModelInstance process =
        process(
            adHocSubProcess -> {
              adHocSubProcess
                  .zeebeActiveElementsCollectionExpression("activateElements")
                  .zeebeOutputCollection("results")
                  .zeebeOutputElementExpression("result");
              adHocSubProcess.serviceTask("A", t -> t.zeebeJobType("A"));
              adHocSubProcess.serviceTask("B", t -> t.zeebeJobType("B"));
              adHocSubProcess.task("C");
            });

    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable("activateElements", List.of("A", "B"))
            .create();

    final var ahsp =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
            .getFirst();
    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("results")
                .getFirst()
                .getValue())
        .describedAs("Should create output collection in scope of ad-hoc sub-process")
        .hasName("results")
        .hasValue("[]")
        .hasScopeKey(ahsp.getKey());

    final var innerInstanceKeys =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS_INNER_INSTANCE)
            .limit(2)
            .map(Record::getKey)
            .toList();
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("result")
                .limit(2))
        .describedAs("Should create output elements in scope of inner instances")
        .map(Record::getValue)
        .extracting(
            VariableRecordValue::getName,
            VariableRecordValue::getValue,
            VariableRecordValue::getScopeKey)
        .containsExactlyInAnyOrder(
            tuple("result", "null", innerInstanceKeys.get(0)),
            tuple("result", "null", innerInstanceKeys.get(1)));

    // when
    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("A")
        .withVariable("result", "a")
        .complete();

    ENGINE
        .job()
        .ofInstance(processInstanceKey)
        .withType("B")
        .withVariable("result", "b")
        .complete();

    // then
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.UPDATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("result")
                .limit(2))
        .describedAs("Should update output elements in scope of inner instances")
        .map(Record::getValue)
        .extracting(
            VariableRecordValue::getName,
            VariableRecordValue::getValue,
            VariableRecordValue::getScopeKey)
        .containsExactlyInAnyOrder(
            tuple("result", "\"a\"", innerInstanceKeys.get(0)),
            tuple("result", "\"b\"", innerInstanceKeys.get(1)));

    assertThat(
            RecordingExporter.variableRecords(VariableIntent.UPDATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("results")
                .limit(2))
        .describedAs("Should have updated the output collection twice")
        .extracting(Record::getValue)
        .extracting(
            VariableRecordValue::getName,
            VariableRecordValue::getValue,
            VariableRecordValue::getScopeKey)
        .containsSequence(
            tuple("results", "[\"a\"]", ahsp.getKey()),
            tuple("results", "[\"a\",\"b\"]", ahsp.getKey()));
  }

  private static Predicate<Record<RecordValue>> signalBroadcasted(final String signalName) {
    return r ->
        r.getIntent() == SignalIntent.BROADCASTED
            && ((SignalRecord) r.getValue()).getSignalName().equals(signalName);
  }
}
