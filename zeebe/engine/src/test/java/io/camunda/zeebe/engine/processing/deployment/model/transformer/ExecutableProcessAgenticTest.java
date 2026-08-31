/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link ExecutableProcess#isAgentic()}, the deploy-time-detected flag that lets {@code
 * AgentInstanceBehavior} skip its per-instance {@code AgentInstanceState} lookup for processes that
 * can never have an agent instance in the first place.
 */
class ExecutableProcessAgenticTest {

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private final BpmnTransformer transformer = new BpmnTransformer(expressionLanguage);

  @Test
  void shouldNotBeAgenticWithoutAnyAgentMarkedElement() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("plain-task", t -> t.zeebeJobType("plain-task-job"))
            .endEvent()
            .done();

    // when
    final ExecutableProcess process = transform(model);

    // then
    assertThat(process.isAgentic())
        .describedAs("A process with no agent-marked element must not be flagged agentic")
        .isFalse();
  }

  @Test
  void shouldBeAgenticWithAgentMarkedServiceTask() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "agent-task", t -> t.zeebeJobType("agent-task-job").zeebeAiAgentTaskDefinition())
            .endEvent()
            .done();

    // when
    final ExecutableProcess process = transform(model);

    // then
    assertThat(process.isAgentic())
        .describedAs("A process with an agent-marked service task must be flagged agentic")
        .isTrue();
  }

  @Test
  void shouldBeAgenticWithAgentMarkedAdHocSubProcess() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .adHocSubProcess(
                "agent-sub-process",
                ahsp -> {
                  ahsp.zeebeAiAgentSubProcessDefinition();
                  ahsp.task("inner");
                  ahsp.zeebeJobType("agent-sub-process-job");
                })
            .endEvent()
            .done();

    // when
    final ExecutableProcess process = transform(model);

    // then
    assertThat(process.isAgentic())
        .describedAs("A process with an agent-marked ad-hoc sub-process must be flagged agentic")
        .isTrue();
  }

  @Test
  void shouldBeAgenticWithAgentMarkedElementNestedInsideSubProcess() {
    // given - the agent-marked service task is nested inside a plain embedded sub-process, not a
    // direct child of the root process
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .subProcess(
                "sub-process",
                sub ->
                    sub.embeddedSubProcess()
                        .startEvent()
                        .serviceTask(
                            "agent-task",
                            t -> t.zeebeJobType("agent-task-job").zeebeAiAgentTaskDefinition())
                        .endEvent())
            .endEvent()
            .done();

    // when
    final ExecutableProcess process = transform(model);

    // then
    assertThat(process.isAgentic())
        .describedAs(
            "A root process must be flagged agentic even when its only agent-marked element is"
                + " nested inside a sub-process, since ExecutableProcess.flowElements is flat"
                + " regardless of nesting depth")
        .isTrue();
  }

  @Test
  void shouldOnlyFlagTheMarkedProcessAmongSiblingsSharingOneResource() {
    // given - a single BPMN resource with two independent, executable <process> elements: only
    // the first carries an agent marker
    final BpmnModelInstance model =
        Bpmn.readModelFromStream(
            getClass()
                .getClassLoader()
                .getResourceAsStream("processes/agent-definition-multi-process-mixed.bpmn"));

    // when
    final List<ExecutableProcess> processes = transformer.transformDefinitions(model);
    final ExecutableProcess markedProcess =
        processes.stream()
            .filter(p -> "process-multi-mixed-marked".equals(BufferUtil.bufferAsString(p.getId())))
            .findFirst()
            .orElseThrow();
    final ExecutableProcess plainProcess =
        processes.stream()
            .filter(p -> "process-multi-mixed-plain".equals(BufferUtil.bufferAsString(p.getId())))
            .findFirst()
            .orElseThrow();

    // then
    assertThat(markedProcess.isAgentic())
        .describedAs(
            "The process containing the agent-marked element must be flagged agentic, correctly"
                + " resolved among several processes sharing one resource")
        .isTrue();
    assertThat(plainProcess.isAgentic())
        .describedAs(
            "The sibling process without any agent-marked element must not be flagged agentic,"
                + " proving the flag isn't leaked across sibling processes")
        .isFalse();
  }

  private ExecutableProcess transform(final BpmnModelInstance model) {
    return transformer.transformDefinitions(model).getFirst();
  }
}
