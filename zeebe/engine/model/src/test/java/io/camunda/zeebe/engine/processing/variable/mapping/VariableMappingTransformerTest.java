/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.VariableMappingTransformer;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeMapping;
import io.camunda.zeebe.util.Either;
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
    final var inputMappings =
        transformer.transformInputMappings(
            List.of(mapping("x", "a"), mapping("_x", "b.c")), expressionLanguage);

    // then
    assertThat(inputMappings.mappings()).hasSize(2);

    final var first = inputMappings.mappings().get(0);
    assertThat(first.source().isValid())
        .describedAs("Expected valid expression: %s", first.source().getFailureMessage())
        .isTrue();
    assertThat(first.targetPath()).containsExactly("a");

    final var second = inputMappings.mappings().get(1);
    assertThat(second.source().isValid())
        .describedAs("Expected valid expression: %s", second.source().getFailureMessage())
        .isTrue();
    assertThat(second.targetPath()).containsExactly("b", "c");
  }

  @Test
  public void shouldPreserveStaticSourceValueAsString() {
    // given static sources containing special characters, quotes, or number/boolean/null shapes
    final var rawSources =
        List.of(
            "Hello\tWorld",
            "Hello\nWorld",
            "Hello\rWorld",
            "\"My Name is \"Zeebe\", nice to meet you\"",
            "My Name is &#34;Zeebe&#34;, nice to meet you");
    final var inputMappings =
        transformer.transformInputMappings(
            List.of(
                mapping(rawSources.get(0), "tab"),
                mapping(rawSources.get(1), "newline"),
                mapping(rawSources.get(2), "carriageReturn"),
                mapping(rawSources.get(3), "doubleQuotes"),
                mapping(rawSources.get(4), "encodedQuotes")),
            expressionLanguage);

    // then each static input source is treated as a string, preserving its characters unescaped
    assertThat(inputMappings.mappings()).hasSize(5);
    for (int i = 0; i < rawSources.size(); i++) {
      final var result =
          expressionLanguage.evaluateExpression(
              inputMappings.mappings().get(i).source(), name -> Either.left(null));
      assertThat(result.getString())
          .describedAs("static source should evaluate to the raw string, unescaped")
          .isEqualTo(rawSources.get(i));
    }
  }

  @Test
  public void shouldHandleNullSource() {
    // given
    final var mappings = List.of(mapping(null, "a"));

    // when
    final var inputMappings = transformer.transformInputMappings(mappings, expressionLanguage);

    // then
    assertThat(inputMappings.mappings()).hasSize(1);
    final var mapping = inputMappings.mappings().get(0);
    assertThat(mapping.source().isValid())
        .describedAs("Expected valid expression: %s", mapping.source().getFailureMessage())
        .isTrue();
    assertThat(mapping.targetPath()).containsExactly("a");
    assertThat(mapping.source().getExpression()).isEqualTo("null");
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
