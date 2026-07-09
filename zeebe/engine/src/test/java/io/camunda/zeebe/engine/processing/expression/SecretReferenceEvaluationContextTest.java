/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.el.ResultType;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import java.time.InstantSource;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SecretReferenceEvaluationContextTest {

  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));

  /**
   * Mimics the engine's evaluation context: process variables plus a {@code
   * camunda.vars.env.<name>} cluster-variable namespace, so that no-shadowing can be verified.
   */
  private final ScopedEvaluationContext delegate =
      CombinedEvaluationContext.withContexts(
          new InMemoryVariableEvaluationContext(Map.of("orderId", "order-123")),
          NamespacedEvaluationContext.create()
              .register(
                  "camunda",
                  NamespacedEvaluationContext.create()
                      .register(
                          "vars",
                          NamespacedEvaluationContext.create()
                              .register(
                                  "env",
                                  new InMemoryVariableEvaluationContext(
                                      Map.of("REGION", "eu-1"))))));

  private final EvaluationContext secretAware = new SecretReferenceEvaluationContext(delegate);

  @Test
  void shouldEvaluateSecretReferenceExpressionToItsOwnStringLiteral() {
    // when
    final var result = evaluate("=camunda.secrets.externalSystemToken");

    // then
    assertThat(result.getType()).isEqualTo(ResultType.STRING);
    assertThat(result.getString()).isEqualTo("camunda.secrets.externalSystemToken");
  }

  @Test
  void shouldEvaluateSecretReferenceInsideConcatenation() {
    // when
    final var result = evaluate("=\"Bearer \" + camunda.secrets.token");

    // then
    assertThat(result.getType()).isEqualTo(ResultType.STRING);
    assertThat(result.getString()).isEqualTo("Bearer camunda.secrets.token");
  }

  @Test
  void shouldResolveEachSecretNameToItsOwnReference() {
    // then
    assertThat(evaluate("=camunda.secrets.a").getString()).isEqualTo("camunda.secrets.a");
    assertThat(evaluate("=camunda.secrets.b").getString()).isEqualTo("camunda.secrets.b");
  }

  @Test
  void shouldNotShadowClusterVariables() {
    // when
    final var result = evaluate("=camunda.vars.env.REGION");

    // then
    assertThat(result.getType()).isEqualTo(ResultType.STRING);
    assertThat(result.getString()).isEqualTo("eu-1");
  }

  @Test
  void shouldResolveSecretAndClusterVariableInSameExpression() {
    // when
    final var result = evaluate("=camunda.vars.env.REGION + \"/\" + camunda.secrets.token");

    // then
    assertThat(result.getString()).isEqualTo("eu-1/camunda.secrets.token");
  }

  @Test
  void shouldPassThroughProcessVariables() {
    // then
    assertThat(evaluate("=orderId").getString()).isEqualTo("order-123");
  }

  @Test
  void shouldReturnNullForUnknownClusterVariable() {
    // when
    final var result = evaluate("=camunda.vars.env.DOES_NOT_EXIST");

    // then
    assertThat(result.getType()).isEqualTo(ResultType.NULL);
  }

  private EvaluationResult evaluate(final String expression) {
    return expressionLanguage.evaluateExpression(
        expressionLanguage.parseExpression(expression), secretAware);
  }
}
