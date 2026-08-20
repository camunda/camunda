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
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCallActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.CallActivityBuilder;
import java.time.InstantSource;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CallActivityTransformerTest {

  private static final String CALL_ACTIVITY_ID = "call-activity";

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private final BpmnTransformer transformer = new BpmnTransformer(expressionLanguage);

  private BpmnModelInstance processWithCallActivity(final Consumer<CallActivityBuilder> modifier) {
    return Bpmn.createExecutableProcess()
        .startEvent()
        .callActivity(CALL_ACTIVITY_ID, modifier)
        .done();
  }

  private ExecutableCallActivity transform(final BpmnModelInstance model) {
    final List<ExecutableProcess> processes = transformer.transformDefinitions(model);
    return processes.getFirst().getElementById(CALL_ACTIVITY_ID, ExecutableCallActivity.class);
  }

  @Test
  void shouldInheritBusinessIdWhenAttributeIsAbsent() {
    // given - no businessId attribute
    final var model = processWithCallActivity(c -> c.zeebeProcessId("child"));

    // when
    final var element = transform(model);

    // then - null configuration signals "inherit the parent's Business ID"
    assertThat(element.getCalledElementBusinessId()).isNull();
  }

  @Test
  void shouldSetEmptyBusinessIdAsEmptyStaticExpression() {
    // given - an explicitly empty businessId attribute
    final var model = processWithCallActivity(c -> c.zeebeProcessId("child").zeebeBusinessId(""));

    // when
    final var element = transform(model);

    // then - present but empty: a static expression with no value, distinct from "inherit"
    assertThat(element.getCalledElementBusinessId()).isNotNull();
    assertThat(element.getCalledElementBusinessId().isStatic()).isTrue();
    assertThat(element.getCalledElementBusinessId().isValid()).isTrue();
    assertThat(element.getCalledElementBusinessId().getExpression()).isEmpty();
  }

  @Test
  void shouldSetLiteralBusinessIdAsStaticExpression() {
    // given - a literal businessId
    final var model =
        processWithCallActivity(c -> c.zeebeProcessId("child").zeebeBusinessId("order-123"));

    // when
    final var element = transform(model);

    // then
    assertThat(element.getCalledElementBusinessId()).isNotNull();
    assertThat(element.getCalledElementBusinessId().isStatic()).isTrue();
    assertThat(element.getCalledElementBusinessId().isValid()).isTrue();
    assertThat(element.getCalledElementBusinessId().getExpression()).isEqualTo("order-123");
  }

  @Test
  void shouldParseBusinessIdFeelExpression() {
    // given - a FEEL expression businessId
    final var model =
        processWithCallActivity(c -> c.zeebeProcessId("child").zeebeBusinessId("=orderId"));

    // when
    final var element = transform(model);

    // then - non-static, valid, with the '=' prefix stripped
    assertThat(element.getCalledElementBusinessId()).isNotNull();
    assertThat(element.getCalledElementBusinessId().isStatic()).isFalse();
    assertThat(element.getCalledElementBusinessId().isValid()).isTrue();
    assertThat(element.getCalledElementBusinessId().getExpression()).isEqualTo("orderId");
  }
}
