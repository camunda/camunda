/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.variables;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.util.JsonUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
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
  public void shouldMergeNestedTargetWhenPropagatingLocalVariablesFromMultiInstanceSubProcess() {
    // given: a pre-existing "container" variable at the process instance scope, and the MI
    // sub-process's own output mapping targets a nested path inside it, so the merge must
    // preserve the pre-existing sibling key rather than overwrite the whole object
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
            .zeebeOutputExpression(LOCAL_VAR, "container.nested")
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(Map.of("container", Map.of("existing", 1)))
            .create();

    // then
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, LOOP_COUNTER);
    final var mergedContainer =
        RecordingExporter.variableRecords(VariableIntent.UPDATED)
            .withProcessInstanceKey(processInstanceKey)
            .withScopeKey(processInstanceKey)
            .withName("container")
            .getFirst()
            .getValue()
            .getValue();
    JsonUtil.assertEquality(mergedContainer, "{\"existing\":1,\"nested\":\"bar\"}");
  }

  @Test
  public void shouldNotPropagateLoopCounterFromMultiInstanceBodyItself() {
    // given: a plain MI service task directly under the process root - no enclosing sub-process
    // at all - isolating the MI body's OWN non-propagation from the sub-process-boundary
    // mechanism exercised above
    final var processId = "processId";
    final var jobType = "miBodyIsolationJobType";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(jobType)
                        .multiInstance(
                            m ->
                                m.zeebeInputCollectionExpression("=[1]").zeebeInputElement("item")))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    ENGINE.job().withType(jobType).ofInstance(processInstanceKey).complete();

    // then
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, LOOP_COUNTER);
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
  public void shouldWalkUpToNearestOwnerElseCreateAtRoot() {
    // given: "a" is already owned by the process root; "b" is owned nowhere. Neither is ever
    // owned by the sub-process in between
    final var processId = "processId";
    final var jobType = "walkUpJobType";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sp",
                sp ->
                    sp.embeddedSubProcess()
                        .startEvent()
                        .serviceTask("task", t -> t.zeebeJobType(jobType))
                        .endEvent())
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("a", 0).create();

    // when: the task completes without any output mapping - the raw job payload propagates
    ENGINE
        .job()
        .withType(jobType)
        .ofInstance(processInstanceKey)
        .withVariables(Map.of("a", 1, "b", 2))
        .complete();

    // then: "a" is updated at the root (nearest - and only - owner); "b" is created at the root
    assertThat(
            RecordingExporter.variableRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withScopeKey(processInstanceKey)
                .limit(3))
        .extracting(r -> r.getValue().getName(), r -> r.getValue().getValue(), Record::getIntent)
        .contains(
            tuple("a", "0", VariableIntent.CREATED),
            tuple("a", "1", VariableIntent.UPDATED),
            tuple("b", "2", VariableIntent.CREATED));
  }

  @Test
  public void shouldStopPropagationAtNearestOwningSubProcessScope() {
    // given: the sub-process's own input mapping creates "x" locally, so the walk-up from the
    // completing task finds it there first and never reaches the process root
    final var processId = "processId";
    final var jobType = "boundaryStopJobType";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sp",
                sp ->
                    sp.zeebeInputExpression("=0", "x")
                        .embeddedSubProcess()
                        .startEvent()
                        .serviceTask("task", t -> t.zeebeJobType(jobType))
                        .endEvent())
            .endEvent()
            .done();
    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();
    final long subProcessInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("sp")
            .getFirst()
            .getKey();

    // when
    ENGINE
        .job()
        .withType(jobType)
        .ofInstance(processInstanceKey)
        .withVariables(Map.of("x", 5))
        .complete();

    // then: updated at the sub-process's own scope ...
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.UPDATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("x")
                .withScopeKey(subProcessInstanceKey)
                .getFirst()
                .getValue()
                .getValue())
        .isEqualTo("5");
    // ... and never created at the process root
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, "x");
  }

  @Test
  @Ignore("https://github.com/camunda/camunda/issues/35251")
  public void shouldNotLeakUntouchedLocalSiblingIntoParentScope() {
    // given: 'a' is set locally on the sub-process with an untouched sibling 'p' that is never
    // targeted by any output mapping
    final var processId = "processId";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .subProcess(
                "sp",
                sp ->
                    sp.zeebeInputExpression("={p:0}", "a")
                        .zeebeOutputExpression("1", "a.b")
                        .embeddedSubProcess()
                        .startEvent()
                        .endEvent())
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process).deploy();

    // when: only a.b is mapped - 'p' should stay local, never reach the process root
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var propagatedA =
        RecordingExporter.records()
            .limitToProcessInstance(processInstanceKey)
            .variableRecords()
            .withScopeKey(processInstanceKey)
            .withName("a")
            .getFirst()
            .getValue()
            .getValue();
    assertThat(propagatedA).isEqualTo("{\"b\":1}");
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

  @Test
  @Ignore("https://github.com/camunda/camunda/issues/51496")
  public void shouldMakeChildVariablesAvailableLocallyOnCallActivityWithoutOutputMapping() {
    // given: propagateAllChildVariables=false and no output mapping - per the issue, child
    // variables should still become local to the call activity scope instead of being discarded
    final var parentProcessId = "parentProcessId";
    final var childProcessId = "childProcessId";
    final var parentProcess =
        Bpmn.createExecutableProcess(parentProcessId)
            .startEvent()
            .callActivity(
                "call",
                c -> c.zeebeProcessId(childProcessId).zeebePropagateAllChildVariables(false))
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
    final long callActivityInstanceKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId("call")
            .getFirst()
            .getKey();

    // then: LOCAL_VAR must be created at the call activity's own scope, not discarded entirely
    assertThat(
            RecordingExporter.records()
                .limitToProcessInstance(processInstanceKey)
                .variableRecords()
                .withIntent(VariableIntent.CREATED)
                .withScopeKey(callActivityInstanceKey)
                .withName(LOCAL_VAR)
                .exists())
        .isTrue();
    // ... and never propagated to the process root
    assertVariableIsNotPropagatedToProcessInstance(processInstanceKey, LOCAL_VAR);
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
