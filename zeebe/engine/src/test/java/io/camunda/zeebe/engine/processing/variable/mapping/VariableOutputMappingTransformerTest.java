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

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.el.ResultType;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.VariableMappingTransformer;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeMapping;
import io.camunda.zeebe.test.util.MsgPackUtil;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public final class VariableOutputMappingTransformerTest {

  private final VariableMappingTransformer transformer = new VariableMappingTransformer();
  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));

  public static Object[][] parametersSuccessfulEvaluationToObject() {
    return new Object[][] {
      // no mappings
      {List.of(), Map.of(), "{}"},
      // direct mapping
      {List.of(mapping("x", "x")), Map.of("x", asMsgPack("1")), "{'x':1}"},
      {List.of(mapping("x", "a")), Map.of("x", asMsgPack("1")), "{'a':1}"},
      {List.of(mapping("_x", "_b")), Map.of("_x", asMsgPack("1")), "{'_b':1}"},
      {
        List.of(mapping("x", "a"), mapping("y", "b")),
        Map.of("x", asMsgPack("1"), "y", asMsgPack("2")),
        "{'a':1, 'b':2}"
      },
      {List.of(mapping("x", "a")), Map.of("x", asMsgPack("{'y':1}")), "{'a':{'y':1}}"},
      // nested target
      {List.of(mapping("x", "a.b")), Map.of("x", asMsgPack("1")), "{'a':{'b':1}}"},
      {
        List.of(mapping("x", "a.b"), mapping("y", "a.c")),
        Map.of("x", asMsgPack("1"), "y", asMsgPack("2")),
        "{'a':{'b':1, 'c':2}}"
      },
      {List.of(mapping("x", "a.b.c")), Map.of("x", asMsgPack("1")), "{'a':{'b':{'c':1}}}"},
      {
        List.of(mapping("x", "a.b")),
        Map.of("x", asMsgPack("1"), "a", asMsgPack("{}")),
        "{'a':{'b':1}}"
      },
      // nested source
      {List.of(mapping("x.y", "a")), Map.of("x", asMsgPack("{'y':1}")), "{'a':1}"},
      {
        List.of(mapping("x.y", "a"), mapping("x.z", "b")),
        Map.of("x", asMsgPack("{'y':1, 'z':2}")),
        "{'a':1, 'b':2}"
      },
      {
        List.of(mapping("x.y", "a.b"), mapping("x.z", "a.c")),
        Map.of("x", asMsgPack("{'y':1, 'z':2}")),
        "{'a': {'b':1, 'c':2}}"
      },
      // override variable
      {
        List.of(mapping("x", "a")),
        Map.of("x", asMsgPack("1"), "a", asMsgPack("{'b':2}")),
        "{'a':1}"
      },
      // merge target with variable
      {
        List.of(mapping("x", "a.b")),
        Map.of("x", asMsgPack("1"), "a", asMsgPack("{'c':2}")),
        "{'a':{'b':1,'c':2}}"
      },
      {
        List.of(mapping("x", "a.b.c")),
        Map.of("x", asMsgPack("1"), "a", asMsgPack("{'b':{'d':2}, 'e':3}")),
        "{'a':{'b':{'c':1, 'd':2}, 'e':3}}"
      },
      // sibling preservation at BOTH nesting levels at once
      {
        List.of(mapping("x", "a.b.new")),
        Map.of("x", asMsgPack("9"), "a", asMsgPack("{'b':{'keep':1}, 'top':2}")),
        "{'a':{'b':{'keep':1, 'new':9}, 'top':2}}"
      },
      // a null merge base takes the "replace", not "merge", branch
      {List.of(mapping("1", "a.b")), Map.of("a", asMsgPack("null")), "{'a':{'b':1}}"},
      // override nested property
      {
        List.of(mapping("x", "a.b")),
        Map.of("x", asMsgPack("1"), "a", asMsgPack("{'b':2}")),
        "{'a':{'b':1}}"
      },
      {
        List.of(mapping("x", "a.b"), mapping("x", "a.c")),
        Map.of("x", asMsgPack("1"), "a", asMsgPack("{'d':2}")),
        "{'a':{'b':1, 'c':1, 'd':2}}"
      },
      // evaluate mappings in order
      {
        List.of(mapping("x", "a"), mapping("a + 1", "b")),
        Map.of("x", asMsgPack("1")),
        "{'a':1, 'b':2}"
      },
      // override previous mapping
      {
        List.of(mapping("x", "a"), mapping("y", "a")),
        Map.of("x", asMsgPack("1"), "y", asMsgPack("2")),
        "{'a':2}"
      },
      {
        List.of(mapping("x", "a"), mapping("y", "a.b")),
        Map.of("x", asMsgPack("1"), "y", asMsgPack("2")),
        "{'a':{'b':2}}"
      },
      // same overlap, opposite declaration order: now "a.b" is dropped instead
      {
        List.of(mapping("y", "a.b"), mapping("x", "a")),
        Map.of("x", asMsgPack("1"), "y", asMsgPack("2")),
        "{'a':1}"
      },
      // source FEEL expression
      {List.of(mapping("1", "a")), Map.of(), "{'a':1}"},
      {List.of(mapping("\"foo\"", "a")), Map.of(), "{'a':'foo'}"},
      {List.of(mapping("[1,2,3]", "a")), Map.of(), "{'a':[1,2,3]}"},
      {List.of(mapping("x + y", "a")), Map.of("x", asMsgPack("1"), "y", asMsgPack("2")), "{'a':3}"},
      {
        List.of(mapping("{x:x, y:y}", "a")),
        Map.of("x", asMsgPack("1"), "y", asMsgPack("2")),
        "{'a':{'x':1, 'y':2}}"
      },
      {
        List.of(mapping("append(x, y)", "a")),
        Map.of("x", asMsgPack("[1,2]"), "y", asMsgPack("3")),
        "{'a':[1,2,3]}"
      },
    };
  }

  public static Object[][] parametersEvaluationToFailure() {
    return new Object[][] {
      {
        List.of(mapping("assert(x, x != null)", "a.b")),
        Map.of(),
        """
        Assertion failure on evaluate the expression \
        '{a:if (a != null) then context merge(a,{b: assert(x, x != null)}) else {b: assert(x, x != null)}}': \
        The condition is not fulfilled"""
      }, // #9543
    };
  }

  @ParameterizedTest(name = "{index}: with {0} to {2}")
  @MethodSource("parametersSuccessfulEvaluationToObject")
  public void shouldEvaluateToObject(
      final List<ZeebeMapping> mappings,
      final Map<String, DirectBuffer> variables,
      final String expectedOutput) {
    // given
    final var expression = transformer.transformOutputMappings(mappings, expressionLanguage);

    assertThat(expression.isValid())
        .describedAs("Expected valid expression: %s", expression.getFailureMessage())
        .isTrue();

    // when
    final var result = expressionLanguage.evaluateExpression(expression, variables::get);

    // then
    assertThat(result.getType()).isEqualTo(ResultType.OBJECT);

    MsgPackUtil.assertEquality(result.toBuffer(), expectedOutput);
  }

  @ParameterizedTest(name = "{index}: mapping {0} fails with: {2}")
  @MethodSource("parametersEvaluationToFailure")
  public void shouldEvaluateToFailure(
      final List<ZeebeMapping> mappings,
      final Map<String, DirectBuffer> variables,
      final String failureMessage) {
    // given
    final var expression = transformer.transformOutputMappings(mappings, expressionLanguage);

    assertThat(expression.isValid())
        .describedAs("Expected valid expression: %s", expression.getFailureMessage())
        .isTrue();

    // when
    final var result = expressionLanguage.evaluateExpression(expression, variables::get);

    // then
    assertThat(result.isFailure()).isTrue();

    Assertions.assertThat(result.getFailureMessage()).isEqualTo(failureMessage);
  }

  @Test
  @Disabled(
      "Output-mapping target-regrouping still breaks declaration order - #58801 fixed "
          + "this for input mappings only. Tracked under #56387 (mapping ordering). — once fixed, move into "
          + "parametersSuccessfulEvaluationToObject.")
  void shouldPreserveDeclarationOrderAcrossRegroupedTargets() {
    // given: c is declared BETWEEN the two "a.*" entries, so the user reasonably expects a.d to
    // see c's just-assigned value
    final var mappings = List.of(mapping("1", "a.b"), mapping("x", "c"), mapping("c", "a.d"));
    final Map<String, DirectBuffer> variables = Map.of("x", asMsgPack("1"));

    final var expression = transformer.transformOutputMappings(mappings, expressionLanguage);
    assertThat(expression.isValid())
        .describedAs("Expected valid expression: %s", expression.getFailureMessage())
        .isTrue();

    // when
    final var result =
        expressionLanguage.evaluateExpression(expression, name -> Either.left(variables.get(name)));

    // then
    assertThat(result.getType()).isEqualTo(ResultType.OBJECT);
    MsgPackUtil.assertEquality(result.toBuffer(), "{'a':{'b':1, 'd':1}, 'c':1}");
  }

  @Test
  @Disabled(
      "context merge() on a scalar merge base silently nulls instead of replacing, unlike the "
          + "null-merge-base case. Tracked under #XXXX — once fixed, move into "
          + "parametersSuccessfulEvaluationToObject.")
  void shouldMergeOntoNonContextValueInsteadOfNulling() {
    final var mappings = List.of(mapping("1", "a.b"));
    final Map<String, DirectBuffer> variables = Map.of("a", asMsgPack("5"));

    final var expression = transformer.transformOutputMappings(mappings, expressionLanguage);
    assertThat(expression.isValid())
        .describedAs("Expected valid expression: %s", expression.getFailureMessage())
        .isTrue();

    // when
    final var result =
        expressionLanguage.evaluateExpression(expression, name -> Either.left(variables.get(name)));

    // then
    assertThat(result.getType()).isEqualTo(ResultType.OBJECT);
    MsgPackUtil.assertEquality(result.toBuffer(), "{'a':{'b':1}}");
  }

  @Test
  @Disabled(
      "context merge() with the current scope leaks an untouched sibling ('p') into the merge "
          + "target and a later back-reference to it, instead of building a mapping-only result "
          + "(input mappings already do this post-#58801). Tracked under "
          + "https://github.com/camunda/camunda/issues/35251 — once fixed, move into "
          + "parametersSuccessfulEvaluationToObject.")
  void shouldNotLeakUntouchedSiblingIntoMergeTargetOrBackReference() {
    final var mappings = List.of(mapping("1", "a.b"), mapping("a", "d"));
    final Map<String, DirectBuffer> variables = Map.of("a", asMsgPack("{'p':0}"));

    final var expression = transformer.transformOutputMappings(mappings, expressionLanguage);
    assertThat(expression.isValid())
        .describedAs("Expected valid expression: %s", expression.getFailureMessage())
        .isTrue();

    // when
    final var result =
        expressionLanguage.evaluateExpression(expression, name -> Either.left(variables.get(name)));

    // then
    assertThat(result.getType()).isEqualTo(ResultType.OBJECT);
    MsgPackUtil.assertEquality(result.toBuffer(), "{'a':{'b':1}, 'd':{'b':1}}");
  }

  @Test
  @Disabled(
      "Same leak as shouldNotLeakUntouchedSiblingIntoMergeTargetOrBackReference, opposite "
          + "mapping order: the back-reference runs before 'a' is ever written, so it correctly "
          + "keeps 'p'; only the later merge target should drop it. Tracked under "
          + "https://github.com/camunda/camunda/issues/35251 — once fixed, move into "
          + "parametersSuccessfulEvaluationToObject.")
  void shouldNotLeakUntouchedSiblingIntoMergeTargetOppositeOrder() {
    final var mappings = List.of(mapping("a", "d"), mapping("1", "a.b"));
    final Map<String, DirectBuffer> variables = Map.of("a", asMsgPack("{'p':0}"));

    final var expression = transformer.transformOutputMappings(mappings, expressionLanguage);
    assertThat(expression.isValid())
        .describedAs("Expected valid expression: %s", expression.getFailureMessage())
        .isTrue();

    // when
    final var result =
        expressionLanguage.evaluateExpression(expression, name -> Either.left(variables.get(name)));

    // then
    assertThat(result.getType()).isEqualTo(ResultType.OBJECT);
    MsgPackUtil.assertEquality(result.toBuffer(), "{'a':{'b':1}, 'd':{'p':0}}");
  }

  private static ZeebeMapping mapping(final String source, final String target) {
    return new ZeebeMapping() {
      @Override
      public String getSource() {
        return "= " + source;
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
