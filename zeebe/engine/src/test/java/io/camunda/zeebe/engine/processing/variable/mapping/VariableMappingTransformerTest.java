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
import static org.assertj.core.api.Assertions.fail;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.el.ResultType;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.VariableMappingTransformer;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeMapping;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.util.Either;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
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
            "Failed to build variable mapping expression: failed to parse expression '{a:x?,_camunda_input_context:context put({},\"a\",a)}._camunda_input_context'");
  }

  @Test
  public void failToTransformWithInvalidTargetExpression() {
    // given
    final var mappings = List.of(mapping("=x", "a,"));

    // when
    assertThatThrownBy(() -> transformer.transformInputMappings(mappings, expressionLanguage))
        .hasMessageStartingWith(
            "Failed to build variable mapping expression: failed to parse expression '{a,:x,_camunda_input_context:context put({},\"a,\",a,)}._camunda_input_context'");
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
            "{tab:\"Hello\tWorld\",_camunda_input_context:context put({},\"tab\",tab),"
                + "newline:\"Hello\nWorld\",_camunda_input_context:context put(_camunda_input_context,\"newline\",newline),"
                + "carriageReturn:\"Hello\rWorld\",_camunda_input_context:context put(_camunda_input_context,\"carriageReturn\",carriageReturn),"
                + "doubleQoutes:\"\\\"My Name is \\\"Zeebe\\\", nice to meet you\\\"\",_camunda_input_context:context put(_camunda_input_context,\"doubleQoutes\",doubleQoutes),"
                + "encodedQuotes:\"My Name is &#34;Zeebe&#34;, nice to meet you\",_camunda_input_context:context put(_camunda_input_context,\"encodedQuotes\",encodedQuotes)}._camunda_input_context");
  }

  @Test
  public void shouldUseLocallyReconstructedValueForNestedPathExpressionInResultContextUpdate() {
    // given — external a.b = "external"; mapping overrides a.b = "overridden"
    final var mappings =
        List.of(
            mapping("=a.b", "preRef"),
            mapping("=\"overridden\"", "a.b"),
            mapping("=a.b", "postRef")); // proves which a.b was used for the result context update
    final var expression = transformer.transformInputMappings(mappings, expressionLanguage);

    final var context = Map.of("a", Map.of("b", "external"));

    // when
    final var result = expressionLanguage.evaluateExpression(expression, buildContext(context));

    // then — both a.b and ref must be "overridden", not "external"
    assertThat(result.isFailure())
        .describedAs("Expected valid result: %s", result.getFailureMessage())
        .isFalse();
    MsgPackUtil.assertEquality(
        result.toBuffer(), "{'preRef':'external','a':{'b':'overridden'},'postRef':'overridden'}");
  }

  @Test
  public void shouldResolveNestedTargetPathInSubsequentInputSourceExpression() {
    // given — "a" is mapped first, then nested "a.b" is set, then "c" uses "a.b" as source
    final var mappings =
        List.of(
            mapping("=\"placeholder\"", "a"),
            mapping("={\"key\":\"value\"}", "a.b"),
            mapping("=1", "a.c"),
            mapping("=a.b", "c"));
    final var expression = transformer.transformInputMappings(mappings, expressionLanguage);

    // when
    final var result = expressionLanguage.evaluateExpression(expression, name -> Either.left(null));

    // then
    assertThat(result.isFailure())
        .describedAs("Expected valid result: %s", result.getFailureMessage())
        .isFalse();
    assertThat(result.getType()).isEqualTo(ResultType.OBJECT);
    MsgPackUtil.assertEquality(
        result.toBuffer(), "{'a':{'b':{'key':'value'},'c':1},'c':{'key':'value'}}");
  }

  @Test
  public void shouldOverrideVariablesWithSameFullNameButNotPartialMatches() {
    // given
    final var mappings =
        List.of(
            mapping("=\"non-nested\"", "var2"),
            mapping("=\"overridden\"", "global.var2"), // should override global nested var
            mapping("=\"new-value\"", "global.var3"), // should add nested value to global var
            mapping(
                "=global.var1", "refGlobal1"), // should reference global var from external context
            mapping("=global.var2", "refGlobal2"), // backref to nested var
            mapping("=global.var3", "refGlobal3"), // backref to nested var
            mapping("=var2", "refNonNested")); // backref to non-nested var
    final var expression = transformer.transformInputMappings(mappings, expressionLanguage);

    final var context =
        Map.of(
            "global",
            Map.of(
                "var1", "value1",
                "var2", "value2"));

    // when
    final var result = expressionLanguage.evaluateExpression(expression, buildContext(context));

    // then
    assertThat(result.isFailure())
        .describedAs("Expected valid result: %s", result.getFailureMessage())
        .isFalse();
    assertThat(result.getType()).isEqualTo(ResultType.OBJECT);
    MsgPackUtil.assertEquality(
        result.toBuffer(),
        """
  {
    'global': {
      'var2': 'overridden',
      'var3': 'new-value'
    },
    'var2': 'non-nested',
    'refGlobal1': 'value1',
    'refGlobal2': 'overridden',
    'refGlobal3': 'new-value',
    'refNonNested': 'non-nested'
  }""");
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

  private EvaluationContext buildContext(final Map<?, ?> context) {
    return name -> {
      final Object value = context.get(name);
      final DirectBuffer encodedValue;
      if (value instanceof final Map nestedContext) {
        encodedValue = asMsgPack((Map<String, Object>) nestedContext);
      } else if (value == null) {
        encodedValue = null;
      } else if (value instanceof final String str) {
        encodedValue = asMsgPack(str);
      } else {
        fail("Unsupported value type in context: " + value.getClass());
        encodedValue = null;
      }
      return Either.left(encodedValue);
    };
  }
}
