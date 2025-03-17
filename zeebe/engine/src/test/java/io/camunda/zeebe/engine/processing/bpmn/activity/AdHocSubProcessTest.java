/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AdHocSubProcessBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
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
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class AdHocSubProcessTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String AD_HOC_SUB_PROCESS_ELEMENT_ID = "ad-hoc";

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
            tuple("A", ProcessInstanceIntent.ACTIVATE_ELEMENT),
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
        .hasSize(2)
        .extracting(r -> r.getValue().getElementId())
        .contains("A", "C");
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
            RecordingExporter.variableRecords().withProcessInstanceKey(processInstanceKey).limit(2))
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
            tuple("A", ProcessInstanceIntent.ACTIVATE_ELEMENT),
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
            tuple("A", ProcessInstanceIntent.ACTIVATE_ELEMENT),
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
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATING),
            tuple(BpmnElementType.USER_TASK, ProcessInstanceIntent.ELEMENT_TERMINATED),
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

    final List<Long> adHocSubProcessKeys =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.AD_HOC_SUB_PROCESS)
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
            tuple("A", adHocSubProcessKeys.get(0)),
            tuple("B", adHocSubProcessKeys.get(1)),
            tuple("C", adHocSubProcessKeys.get(2)));
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
            tuple("A", BpmnElementType.TASK, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple("A", BpmnElementType.TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("A", BpmnElementType.TASK, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(
                "A", BpmnElementType.MULTI_INSTANCE_BODY, ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple(PROCESS_ID, BpmnElementType.PROCESS, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }
}
