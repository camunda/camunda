/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.variables;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public final class ContainerElementVariablePropagationTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String AD_HOC_SUB_PROCESS_ELEMENTS = "adHocSubProcessElements";
  private static final String LOOP_COUNTER = "loopCounter";
  private static final String LOCAL_VAR = "localVar";
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldNotPropagateLocalVariablesIfNoOutputMappingOnMultiInstanceSubProcess() {
    // given
    final var processId = "processId";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sp",
                sp ->
                    sp.multiInstance(
                            mi ->
                                mi.zeebeInputCollectionExpression("= [1]")
                                    .zeebeInputElement("inputElement")
                                    .zeebeOutputCollection("output")
                                    .zeebeOutputElementExpression("outputElement"))
                        .zeebeInputExpression("= \"foo\"", LOCAL_VAR)
                        .embeddedSubProcess()
                        .startEvent()
                        .intermediateThrowEvent("nestedElement")
                        .zeebeOutputExpression("= \"bar\"", LOCAL_VAR)
                        .endEvent())
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, LOOP_COUNTER);
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, LOCAL_VAR);
  }

  @Test
  @Ignore("https://github.com/camunda/camunda/issues/55491")
  public void
      shouldNotPropagateLocalVariablesWithSameValueIfNoOutputMappingOnMultiInstanceSubProcess() {
    // given
    final var processId = "processId";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sp",
                sp ->
                    sp.multiInstance(
                            mi ->
                                mi.zeebeInputCollectionExpression("= [1]")
                                    .zeebeInputElement("inputElement")
                                    .zeebeOutputCollection("output")
                                    .zeebeOutputElementExpression("outputElement"))
                        .zeebeInputExpression("= \"foo\"", LOCAL_VAR)
                        .embeddedSubProcess()
                        .startEvent()
                        .intermediateThrowEvent("nestedElement")
                        // Note output expression value is the same as the existing variable in the
                        // container element
                        .zeebeOutputExpression("= \"foo\"", LOCAL_VAR)
                        .endEvent())
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, LOOP_COUNTER);
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, LOCAL_VAR);
  }

  @Test
  public void shouldPropagateLocalVariablesIfOutputMappingOnMultiInstanceSubProcess() {
    // given
    final var processId = "processId";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sp",
                sp ->
                    sp.multiInstance(
                            mi ->
                                mi.zeebeInputCollectionExpression("= [1]")
                                    .zeebeInputElement("inputElement")
                                    .zeebeOutputCollection("output")
                                    .zeebeOutputElementExpression("outputElement"))
                        .zeebeInputExpression("= \"foo\"", LOCAL_VAR)
                        .embeddedSubProcess()
                        .startEvent()
                        .intermediateThrowEvent("nestedElement")
                        .zeebeOutputExpression("= \"bar\"", LOCAL_VAR)
                        .endEvent())
            .zeebeOutputExpression(LOCAL_VAR, LOCAL_VAR)
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, LOOP_COUNTER);
    assertVariableIsPropagatedToProcessInstance(processInstanceKey, LOCAL_VAR);
  }

  @Test
  public void shouldNotPropagateLocalVariablesIfNoOutputMappingOnSubProcess() {
    // given
    final var processId = "processId";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sp",
                sp ->
                    sp.zeebeInputExpression("= \"foo\"", LOCAL_VAR)
                        .embeddedSubProcess()
                        .startEvent()
                        .intermediateThrowEvent("nestedElement")
                        .zeebeOutputExpression("= \"bar\"", LOCAL_VAR)
                        .endEvent())
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, LOCAL_VAR);
  }

  @Test
  @Ignore("https://github.com/camunda/camunda/issues/55491")
  public void shouldNotPropagateLocalVariablesWithSameValueIfNoOutputMappingOnSubProcess() {
    // given
    final var processId = "processId";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sp",
                sp ->
                    sp.zeebeInputExpression("= \"foo\"", LOCAL_VAR)
                        .embeddedSubProcess()
                        .startEvent()
                        .intermediateThrowEvent("nestedElement")
                        // Note output expression value is the same as the existing variable in the
                        // container element
                        .zeebeOutputExpression("= \"foo\"", LOCAL_VAR)
                        .endEvent())
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, LOCAL_VAR);
  }

  @Test
  public void shouldPropagateLocalVariablesIfOutputMappingOnSubProcess() {
    // given
    final var processId = "processId";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sp",
                sp ->
                    sp.zeebeInputExpression("= \"foo\"", LOCAL_VAR)
                        .embeddedSubProcess()
                        .startEvent()
                        .intermediateThrowEvent("nestedElement")
                        .zeebeOutputExpression("= \"bar\"", LOCAL_VAR)
                        .endEvent())
            .zeebeOutputExpression(LOCAL_VAR, LOCAL_VAR)
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertVariableIsPropagatedToProcessInstance(processInstanceKey, LOCAL_VAR);
  }

  @Test
  public void shouldNotPropagateLocalVariablesIfNoOutputMappingOnAdHocSubProcess() {
    // given
    final var processId = "processId";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .adHocSubProcess(
                "ahsp",
                ahsp -> {
                  ahsp.zeebeInputExpression("= \"foo\"", LOCAL_VAR)
                      .zeebeActiveElementsCollectionExpression("[\"nestedElement\"]");
                  ahsp.intermediateThrowEvent("nestedElement")
                      .zeebeOutputExpression("= \"bar\"", LOCAL_VAR);
                })
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, LOCAL_VAR);
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, AD_HOC_SUB_PROCESS_ELEMENTS);
  }

  @Test
  @Ignore("https://github.com/camunda/camunda/issues/55491")
  public void shouldNotPropagateLocalVariablesWithSameValueIfNoOutputMappingOnAdHocSubProcess() {
    // given
    final var processId = "processId";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .adHocSubProcess(
                "ahsp",
                ahsp -> {
                  ahsp.zeebeInputExpression("= \"foo\"", LOCAL_VAR)
                      .zeebeActiveElementsCollectionExpression("[\"nestedElement\"]");
                  ahsp.intermediateThrowEvent("nestedElement")
                      // Note output expression value is the same as the existing variable in the
                      // container element
                      .zeebeOutputExpression("= \"foo\"", LOCAL_VAR);
                })
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, LOCAL_VAR);
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, AD_HOC_SUB_PROCESS_ELEMENTS);
  }

  @Test
  public void shouldPropagateLocalVariablesIfOutputMappingOnAdHocSubProcess() {
    // given
    final var processId = "processId";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .adHocSubProcess(
                "ahsp",
                ahsp -> {
                  ahsp.zeebeInputExpression("= \"foo\"", LOCAL_VAR)
                      .zeebeActiveElementsCollectionExpression("[\"nestedElement\"]")
                      .zeebeOutputExpression(LOCAL_VAR, LOCAL_VAR);
                  ahsp.intermediateThrowEvent("nestedElement")
                      .zeebeOutputExpression("= \"bar\"", LOCAL_VAR);
                })
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertVariableIsPropagatedToProcessInstance(processInstanceKey, LOCAL_VAR);
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, AD_HOC_SUB_PROCESS_ELEMENTS);
  }

  @Test
  public void shouldNotPropagateLocalVariablesIfNoOutputMappingOnEventSubProcess() {
    // given
    final var processId = "processId";
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(processId);
    processBuilder
        .eventSubProcess("eventSubProcess")
        .zeebeInputExpression("= \"foo\"", LOCAL_VAR)
        .startEvent()
        .interrupting(true)
        .signal("signal")
        .intermediateThrowEvent("nestedElement")
        .zeebeOutputExpression("= \"bar\"", LOCAL_VAR)
        .endEvent();
    final var process =
        processBuilder
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("task"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withBpmnProcessId(processId)
        .await();
    ENGINE.signal().withSignalName("signal").broadcast();

    // then
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, LOCAL_VAR);
  }

  @Test
  @Ignore("https://github.com/camunda/camunda/issues/55491")
  public void shouldNotPropagateLocalVariablesWithSameValueIfNoOutputMappingOnEventSubProcess() {
    // given
    final var processId = "processId";
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(processId);
    processBuilder
        .eventSubProcess("eventSubProcess")
        .zeebeInputExpression("= \"foo\"", LOCAL_VAR)
        .startEvent()
        .interrupting(true)
        .signal("signal")
        .intermediateThrowEvent("nestedElement")
        // Note output expression value is the same as the existing variable in the
        // container element
        .zeebeOutputExpression("= \"foo\"", LOCAL_VAR)
        .endEvent();
    final var process =
        processBuilder
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("task"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withBpmnProcessId(processId)
        .await();
    ENGINE.signal().withSignalName("signal").broadcast();

    // then
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, LOCAL_VAR);
  }

  @Test
  public void shouldPropagateLocalVariablesIfOutputMappingOnEventSubProcess() {
    // given
    final var processId = "processId";
    final ProcessBuilder processBuilder = Bpmn.createExecutableProcess(processId);
    processBuilder
        .eventSubProcess("eventSubProcess")
        .zeebeInputExpression("= \"foo\"", LOCAL_VAR)
        .zeebeOutputExpression(LOCAL_VAR, LOCAL_VAR)
        .startEvent()
        .interrupting(true)
        .signal("signal")
        .intermediateThrowEvent("nestedElement")
        .zeebeOutputExpression("= \"bar\"", LOCAL_VAR)
        .endEvent();
    final var process =
        processBuilder
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType("task"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
        .withBpmnProcessId(processId)
        .await();
    ENGINE.signal().withSignalName("signal").broadcast();

    // then
    assertVariableIsPropagatedToProcessInstance(processInstanceKey, LOCAL_VAR);
  }

  @Test
  public void shouldNotPropagateLocalVariablesIfNoOutputMappingOnCallActivity() {
    // given
    final var parentProcessId = "parentProcessId";
    final var childProcessId = "childProcessId";
    final var parentProcess =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(
                "call",
                c ->
                    c.zeebeProcessId(childProcessId)
                        .zeebePropagateAllChildVariables(false)
                        .zeebeInputExpression("= \"foo\"", LOCAL_VAR))
            .endEvent()
            .done();
    final var childProcess =
        Bpmn.createExecutableProcess(childProcessId)
            .startEvent()
            .intermediateThrowEvent("nestedElement")
            .zeebeOutputExpression("= \"bar\"", LOCAL_VAR)
            .endEvent()
            .done();

    ENGINE
        .deployment()
        .withXmlResource("parent.bpmn", parentProcess)
        .withXmlResource("child.bpmn", childProcess)
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();

    // then
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, LOCAL_VAR);
  }

  @Test
  public void shouldNotPropagateLocalVariablesWithSameValueIfNoOutputMappingOnCallActivity() {
    // given
    final var parentProcessId = "parentProcessId";
    final var childProcessId = "childProcessId";
    final var parentProcess =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(
                "call",
                c ->
                    c.zeebeProcessId(childProcessId)
                        .zeebePropagateAllChildVariables(false)
                        .zeebeInputExpression("= \"foo\"", LOCAL_VAR))
            .endEvent()
            .done();
    final var childProcess =
        Bpmn.createExecutableProcess(childProcessId)
            .startEvent()
            .intermediateThrowEvent("nestedElement")
            .zeebeOutputExpression("= \"foo\"", LOCAL_VAR)
            .endEvent()
            .done();

    ENGINE
        .deployment()
        .withXmlResource("parent.bpmn", parentProcess)
        .withXmlResource("child.bpmn", childProcess)
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();

    // then
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, LOCAL_VAR);
  }

  @Test
  public void shouldPropagateLocalVariablesIfOutputMappingOnCallActivity() {
    // given
    final var parentProcessId = "parentProcessId";
    final var childProcessId = "childProcessId";
    final var parentProcess =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(
                "call",
                c ->
                    c.zeebeProcessId(childProcessId)
                        .zeebePropagateAllChildVariables(false)
                        .zeebeInputExpression("= \"foo\"", LOCAL_VAR)
                        .zeebeOutputExpression(LOCAL_VAR, LOCAL_VAR))
            .endEvent()
            .done();
    final var childProcess =
        Bpmn.createExecutableProcess(childProcessId)
            .startEvent()
            .intermediateThrowEvent("nestedElement")
            .zeebeOutputExpression("= \"bar\"", LOCAL_VAR)
            .endEvent()
            .done();

    ENGINE
        .deployment()
        .withXmlResource("parent.bpmn", parentProcess)
        .withXmlResource("child.bpmn", childProcess)
        .deploy();

    // when
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(parentProcessId).create();

    // then
    assertVariableIsPropagatedToProcessInstance(processInstanceKey, LOCAL_VAR);
  }

  private static void assertVariableIsNotPropagatedToProcessInstance(
      final long processInstanceKey, final String variableName) {
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords()
                .withIntent(VariableIntent.CREATED)
                .withScopeKey(processInstanceKey)
                .withName(variableName)
                .exists())
        .isFalse();
  }

  private static void assertVariableIsPropagatedToProcessInstance(
      final long processInstanceKey, final String variableName) {
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords()
                .withIntent(VariableIntent.CREATED)
                .withScopeKey(processInstanceKey)
                .withName(variableName)
                .exists())
        .isTrue();
  }
}
