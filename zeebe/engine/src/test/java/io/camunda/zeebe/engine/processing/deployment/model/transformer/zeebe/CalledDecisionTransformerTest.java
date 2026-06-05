/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableBusinessRuleTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.BusinessRuleTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import java.time.InstantSource;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CalledDecisionTransformerTest {

  private static final String TASK_ID = "business-rule-task";

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private final BpmnTransformer transformer = new BpmnTransformer(expressionLanguage);

  private BpmnModelInstance processWithBusinessRuleTask(
      final Consumer<BusinessRuleTaskBuilder> modifier) {
    return Bpmn.createExecutableProcess().startEvent().businessRuleTask(TASK_ID, modifier).done();
  }

  private ExecutableBusinessRuleTask transform(final BpmnModelInstance model) {
    final List<ExecutableProcess> processes = transformer.transformDefinitions(model);
    return processes.get(0).getElementById(TASK_ID, ExecutableBusinessRuleTask.class);
  }

  @Test
  void shouldParseStaticVersionTagAsExpression() {
    // given
    final var model =
        processWithBusinessRuleTask(
            t ->
                t.zeebeCalledDecisionId("myDecision")
                    .zeebeBindingType(ZeebeBindingType.versionTag)
                    .zeebeVersionTag("v1.0")
                    .zeebeResultVariable("result"));

    // when
    final var element = transform(model);

    // then
    assertThat(element.getVersionTag()).isNotNull();
    assertThat(element.getVersionTag().isStatic()).isTrue();
    assertThat(element.getVersionTag().isValid()).isTrue();
    assertThat(element.getVersionTag().getExpression()).isEqualTo("v1.0");
  }

  @Test
  void shouldParseVersionTagExpressionAsFeelExpression() {
    // given
    final var model =
        processWithBusinessRuleTask(
            t ->
                t.zeebeCalledDecisionId("myDecision")
                    .zeebeBindingType(ZeebeBindingType.versionTag)
                    .zeebeVersionTag("=versionTagVariable")
                    .zeebeResultVariable("result"));

    // when
    final var element = transform(model);

    // then
    assertThat(element.getVersionTag()).isNotNull();
    assertThat(element.getVersionTag().isStatic()).isFalse();
    assertThat(element.getVersionTag().isValid()).isTrue();
    assertThat(element.getVersionTag().getExpression()).isEqualTo("versionTagVariable");
  }

  @Test
  void shouldSetNullVersionTagWhenNoCalledDecisionExtension() {
    // given
    final var model = processWithBusinessRuleTask(t -> {});

    // when
    final var element = transform(model);

    // then
    assertThat(element.getVersionTag()).isNull();
  }
}
