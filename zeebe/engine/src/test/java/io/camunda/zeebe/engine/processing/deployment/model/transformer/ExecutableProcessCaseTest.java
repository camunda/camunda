/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ProcessBuilder;
import java.time.InstantSource;
import org.junit.jupiter.api.Test;

/**
 * Covers the case definition ({@link ExecutableProcess#getCaseDefinition()}) that the process
 * transformer reads from the {@code zeebe:case} marker and the top-level {@code zeebe:stage}
 * markers.
 */
class ExecutableProcessCaseTest {

  private static final String PROCESS_ID = "process";
  private static final String TITLE_VARIABLE = "loanTitle";

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private final BpmnTransformer transformer = new BpmnTransformer(expressionLanguage);

  @Test
  void shouldNotBeCaseWithoutCaseMarker() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().endEvent().done();

    // when
    final ExecutableProcess process = transform(model);

    // then
    assertThat(process.isCase()).isFalse();
    assertThat(process.getCaseDefinition()).isNull();
  }

  @Test
  void shouldReadTitleVariable() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeCase(TITLE_VARIABLE)
            .startEvent()
            .endEvent()
            .done();

    // when
    final ExecutableProcess process = transform(model);

    // then
    assertThat(process.isCase()).isTrue();
    assertThat(process.getCaseDefinition().titleVariable()).isEqualTo(wrapString(TITLE_VARIABLE));
  }

  @Test
  void shouldReadProcessName() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .name("Loan application")
            .zeebeCase(TITLE_VARIABLE)
            .startEvent()
            .endEvent()
            .done();

    // when
    final ExecutableProcess process = transform(model);

    // then
    assertThat(process.getName()).isEqualTo(wrapString("Loan application"));
  }

  @Test
  void shouldUseEmptyNameForUnnamedProcess() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeCase(TITLE_VARIABLE)
            .startEvent()
            .endEvent()
            .done();

    // when
    final ExecutableProcess process = transform(model);

    // then
    assertThat(process.getName().capacity()).isZero();
  }

  @Test
  void shouldReadTopLevelStagesInDocumentOrder() {
    // given
    final ProcessBuilder processBuilder =
        Bpmn.createExecutableProcess(PROCESS_ID).zeebeCase(TITLE_VARIABLE);
    // the elements are created in this order, which is the document order of the model
    final var flow =
        processBuilder
            .startEvent()
            .subProcess(
                "a",
                stage -> stage.name("A").zeebeStage().embeddedSubProcess().startEvent().endEvent())
            .subProcess(
                "plain",
                subProcess ->
                    subProcess.name("Plain").embeddedSubProcess().startEvent().endEvent());
    processBuilder.eventSubProcess(
        "b", stage -> stage.name("B").zeebeStage().startEvent().signal("stage-signal").endEvent());
    final BpmnModelInstance model =
        flow.adHocSubProcess(
                "c",
                stage -> {
                  stage.name("C");
                  stage.zeebeStage();
                  stage.task("inner");
                })
            .endEvent()
            .done();

    // when
    final ExecutableProcess process = transform(model);

    // then
    assertThat(process.getCaseDefinition().stages())
        .extracting(stage -> stage.elementId(), stage -> stage.name())
        .containsExactly(tuple("a", "A"), tuple("b", "B"), tuple("c", "C"));
  }

  @Test
  void shouldUseEmptyNameForUnnamedStage() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeCase(TITLE_VARIABLE)
            .startEvent()
            .subProcess(
                "unnamed",
                stage -> {
                  // the builder defaults the name of a sub-process to its id
                  stage.name(null);
                  stage.zeebeStage().embeddedSubProcess().startEvent().endEvent();
                })
            .endEvent()
            .done();

    // when
    final ExecutableProcess process = transform(model);

    // then
    assertThat(process.getCaseDefinition().stages())
        .extracting(stage -> stage.elementId(), stage -> stage.name())
        .containsExactly(tuple("unnamed", ""));
  }

  @Test
  void shouldHaveNoStagesWithoutStageMarkers() {
    // given
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .zeebeCase(TITLE_VARIABLE)
            .startEvent()
            .subProcess(
                "plain", subProcess -> subProcess.embeddedSubProcess().startEvent().endEvent())
            .endEvent()
            .done();

    // when
    final ExecutableProcess process = transform(model);

    // then
    assertThat(process.getCaseDefinition().stages()).isEmpty();
  }

  private ExecutableProcess transform(final BpmnModelInstance model) {
    return transformer.transformDefinitions(model).getFirst();
  }
}
