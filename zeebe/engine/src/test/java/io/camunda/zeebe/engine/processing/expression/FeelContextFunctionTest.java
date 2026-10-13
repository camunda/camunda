/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 * Regression tests for <a href="https://github.com/camunda/camunda/issues/12740">#12740</a>: the
 * FEEL context function must convert both literal entries and entries read from process variables.
 */
public final class FeelContextFunctionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String CONTEXT_LIST =
      "[{\"key\":\"a\",\"value\":1},{\"key\":\"b\",\"value\":2}]";
  private static final List<Map<String, Object>> CONTEXT_ENTRIES =
      List.of(Map.of("key", "a", "value", 1), Map.of("key", "b", "value", 2));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final TestName testName = new TestName();

  @Test
  public void shouldConvertMappedVariableInScriptTask() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(testName.getMethodName())
                .startEvent()
                .zeebeOutputExpression(CONTEXT_LIST, "contextList")
                .scriptTask(
                    "script",
                    task ->
                        task.zeebeExpression("context(contextList)")
                            .zeebeResultVariable("resultContext"))
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(testName.getMethodName()).create();

    // then
    assertResultContext(processInstanceKey);
  }

  @Test
  public void shouldConvertLiteralInScriptTask() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(testName.getMethodName())
                .startEvent()
                .scriptTask(
                    "script",
                    task ->
                        task.zeebeExpression("context(" + CONTEXT_LIST + ")")
                            .zeebeResultVariable("resultContext"))
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(testName.getMethodName()).create();

    // then
    assertResultContext(processInstanceKey);
  }

  @Test
  public void shouldConvertProcessVariableInScriptTask() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(testName.getMethodName())
                .startEvent()
                .scriptTask(
                    "script",
                    task ->
                        task.zeebeExpression("context(contextList)")
                            .zeebeResultVariable("resultContext"))
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(testName.getMethodName())
            .withVariable("contextList", CONTEXT_ENTRIES)
            .create();

    // then
    assertResultContext(processInstanceKey);
  }

  @Test
  public void shouldConvertProcessVariableInIntermediateEventOutputMapping() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(testName.getMethodName())
                .startEvent()
                .intermediateThrowEvent(
                    "event",
                    event -> event.zeebeOutputExpression("context(contextList)", "resultContext"))
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(testName.getMethodName())
            .withVariable("contextList", CONTEXT_ENTRIES)
            .create();

    // then
    assertResultContext(processInstanceKey);
  }

  @Test
  public void shouldConvertProcessVariableInStartEventOutputMapping() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(testName.getMethodName())
                .startEvent()
                .zeebeOutputExpression("context(contextList)", "resultContext")
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(testName.getMethodName())
            .withVariable("contextList", CONTEXT_ENTRIES)
            .create();

    // then
    assertResultContext(processInstanceKey);
  }

  @Test
  public void shouldConvertEntriesFromPreviousOutputMapping() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(testName.getMethodName())
                .startEvent()
                .zeebeOutputExpression(CONTEXT_LIST, "contextList")
                .zeebeOutputExpression("context(contextList)", "resultContext")
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(testName.getMethodName()).create();

    // then
    assertResultContext(processInstanceKey);
  }

  private static void assertResultContext(final long processInstanceKey) {
    Assertions.assertThat(
            RecordingExporter.variableRecords(VariableIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("resultContext")
                .getFirst()
                .getValue())
        .hasValue("{\"a\":1,\"b\":2}");
  }
}
