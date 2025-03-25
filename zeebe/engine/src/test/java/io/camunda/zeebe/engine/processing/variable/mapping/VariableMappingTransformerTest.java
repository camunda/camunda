/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable.mapping;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.el.ResultType;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.VariableMappingTransformer;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeMapping;
import java.time.InstantSource;
import java.util.List;
import org.junit.Test;

public final class VariableMappingTransformerTest {

  private final VariableMappingTransformer transformer = new VariableMappingTransformer();
  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));

  @Test
  public void shouldCreateValidExpression() {
    // when
    final var expression =
        transformer.transformInputMappings(
            List.of(mapping("x", "a"), mapping("_x", "b")), expressionLanguage);

    // then
    assertThat(expression.isValid())
        .describedAs("Expected valid expression: %s", expression.getFailureMessage())
        .isTrue();
  }

  @Test
  public void shouldEvaluateToObject() {
    // given
    final var expression =
        transformer.transformInputMappings(List.of(mapping("x", "a")), expressionLanguage);

    // when
    final var result = expressionLanguage.evaluateExpression(expression, name -> asMsgPack("1"));

    // then
    assertThat(result.isFailure())
        .describedAs("Expected valid result: %s", expression.getFailureMessage())
        .isFalse();
    assertThat(result.getType()).isEqualTo(ResultType.OBJECT);
  }

  @Test
  public void failToTransformWithInvalidSourceExpression() {
    // when
    final var mappings = List.of(mapping("=x?", "a"));

    // when
    assertThatThrownBy(() -> transformer.transformInputMappings(mappings, expressionLanguage))
        .hasMessageStartingWith(
            "Failed to build variable mapping expression: failed to parse expression '{a:x?}'");
  }

  @Test
  public void failToTransformWithInvalidTargetExpression() {
    // given
    final var mappings = List.of(mapping("=x", "a,"));

    // when
    assertThatThrownBy(() -> transformer.transformInputMappings(mappings, expressionLanguage))
        .hasMessageStartingWith(
            "Failed to build variable mapping expression: failed to parse expression '{a,:x}'");
  }

  @Test
  public void shouldEvaluateWithNotExistingVariable() {
    // given
    final var mappings = List.of(mapping("=x", "a"));
    final var expression = transformer.transformInputMappings(mappings, expressionLanguage);

    // when
    final var result = expressionLanguage.evaluateExpression(expression, name -> null);

    // then
    assertThat(result.isFailure())
        .describedAs("Expected valid result: %s", expression.getFailureMessage())
        .isFalse();

    assertThat(result.getType())
        .describedAs("Expected to replace a non-existing variable with `null`")
        .isEqualTo(ResultType.OBJECT);
  }

  @Test
  public void shouldNotEscapeCharactersInStaticExpression() {
    // when
    final var expression =
        transformer.transformInputMappings(
            List.of(
                mapping("Hello\tWorld", "tab"),
                mapping("Hello\nWorld", "newline"),
                mapping("Hello\rWorld", "carriageReturn"),
                mapping("\"My Name is \"Zeebe\", nice to meet you\"", "doubleQoutes"),
                mapping("My Name is &#34;Zeebe&#34;, nice to meet you", "encodedQuotes")),
            expressionLanguage);

    // then
    assertThat(expression.isValid())
        .describedAs("Expected valid expression: %s", expression.getFailureMessage())
        .isTrue();
    assertThat(expression.getExpression())
        .isEqualTo(
            "{tab:\"Hello\tWorld\",newline:\"Hello\nWorld\",carriageReturn:\"Hello\rWorld\",doubleQoutes:\"\\\"My Name is \\\"Zeebe\\\", nice to meet you\\\"\",encodedQuotes:\"My Name is &#34;Zeebe&#34;, nice to meet you\"}");
  }

  @Test
  public void shouldHandleNullSource() {
    // given
    final var mappings = List.of(mapping(null, "a"));
    final var expression = transformer.transformInputMappings(mappings, expressionLanguage);

    // when
    final var result = expressionLanguage.evaluateExpression(expression, name -> null);

    // then
    assertThat(expression.isValid())
        .describedAs("Expected valid expression: %s", expression.getFailureMessage())
        .isTrue();
    assertThat(result.getExpression()).isEqualTo("{a:null}");
  }

  private static ZeebeMapping mapping(final String source, final String target) {
    return new ZeebeMapping() {
      @Override
      public String getSource() {
        return source;
      }

      @Override
      public String getTarget() {
        return target;
      }

      @Override
      public String toString() {
        return source + " -> " + target;
      }
    };
  }
}
