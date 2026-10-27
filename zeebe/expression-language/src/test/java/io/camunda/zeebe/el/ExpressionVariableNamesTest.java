/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.impl.FeelExpressionLanguage;
import io.camunda.zeebe.el.util.TestFeelEngineClock;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Expression#getVariableNames()} covering various FEEL expression patterns. These
 * tests verify the integration with the feel-scala library's variable extraction capabilities.
 *
 * @see <a
 *     href="https://github.com/camunda/feel-scala/blob/main/src/test/scala/org/camunda/feel/api/ExpressionVariableExtractorTest.scala">feel-scala
 *     ExpressionVariableExtractorTest</a>
 */
public class ExpressionVariableNamesTest {

  private final ExpressionLanguage expressionLanguage =
      new FeelExpressionLanguage(new TestFeelEngineClock());

  @Nested
  class StaticAndInvalidExpressions {

    @Test
    public void shouldReturnEmptySetForStaticExpression() {
      // static expressions (no leading '=') have no variable references
      final var expression = expressionLanguage.parseExpression("hello");
      assertThat(expression.getVariableNames()).isEmpty();
    }

    @Test
    public void shouldReturnEmptySetForStaticNumberExpression() {
      final var expression = expressionLanguage.parseExpression("42");
      assertThat(expression.getVariableNames()).isEmpty();
    }

    @Test
    public void shouldReturnEmptySetForInvalidExpression() {
      // invalid FEEL expression - starts with '=' but has invalid syntax
      final var expression = expressionLanguage.parseExpression("=if(");
      assertThat(expression.isValid()).isFalse();
      assertThat(expression.getVariableNames()).isEmpty();
    }
  }

  @Nested
  class LiteralExpressions {

    @Test
    public void shouldReturnEmptySetForLiteralWithNoVariables() {
      final var expression = expressionLanguage.parseExpression("=1 + 2");
      assertThat(expression.getVariableNames()).isEmpty();
    }

    @Test
    public void shouldReturnEmptySetForStringLiteral() {
      final var expression = expressionLanguage.parseExpression("=\"hello\"");
      assertThat(expression.getVariableNames()).isEmpty();
    }

    @Test
    public void shouldReturnEmptySetForBooleanLiteral() {
      final var expression = expressionLanguage.parseExpression("=true");
      assertThat(expression.getVariableNames()).isEmpty();
    }
  }

  @Nested
  class SimpleVariableReferences {

    @Test
    public void shouldReturnSingleVariable() {
      final var expression = expressionLanguage.parseExpression("=a");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a"));
    }

    @Test
    public void shouldReturnMultipleVariables() {
      final var expression = expressionLanguage.parseExpression("=a < b");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a", "b"));
    }

    @Test
    public void shouldReturnUniqueVariableNames() {
      final var expression = expressionLanguage.parseExpression("=a + a + b");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a", "b"));
    }
  }

  @Nested
  class PathExpressions {

    @Test
    public void shouldReturnTopLevelNamesForNestedVariables() {
      // nested paths like a.b should only yield the root variable name
      final var expression = expressionLanguage.parseExpression("=a.b < c.d");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a", "c"));
    }

    @Test
    public void shouldReturnRootVariableForSinglePath() {
      final var expression = expressionLanguage.parseExpression("=x.y");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("x"));
    }

    @Test
    public void shouldReturnRootVariableForDeeplyNestedPath() {
      final var expression = expressionLanguage.parseExpression("=x.y.z > 10");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("x"));
    }
  }

  @Nested
  class ArithmeticExpressions {

    @Test
    public void shouldExtractVariablesFromAddition() {
      final var expression = expressionLanguage.parseExpression("=a + b");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a", "b"));
    }

    @Test
    public void shouldExtractVariablesFromSubtraction() {
      final var expression = expressionLanguage.parseExpression("=a - b");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a", "b"));
    }

    @Test
    public void shouldExtractVariablesFromMultiplication() {
      final var expression = expressionLanguage.parseExpression("=a * b");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a", "b"));
    }

    @Test
    public void shouldExtractVariablesFromDivision() {
      final var expression = expressionLanguage.parseExpression("=a / b");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a", "b"));
    }

    @Test
    public void shouldExtractVariablesFromExponentiation() {
      final var expression = expressionLanguage.parseExpression("=a ** b");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a", "b"));
    }

    @Test
    public void shouldExtractVariablesFromNegation() {
      final var expression = expressionLanguage.parseExpression("=- a");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a"));
    }
  }

  @Nested
  class LogicalExpressions {

    @Test
    public void shouldExtractVariablesFromDisjunction() {
      final var expression = expressionLanguage.parseExpression("=a or b");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a", "b"));
    }

    @Test
    public void shouldExtractVariablesFromConjunction() {
      final var expression = expressionLanguage.parseExpression("=a and b");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a", "b"));
    }

    @Test
    public void shouldExtractVariablesFromComparison() {
      final var expression = expressionLanguage.parseExpression("=a < b");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a", "b"));
    }
  }

  @Nested
  class ConditionalExpressions {

    @Test
    public void shouldExtractVariablesFromIfThenElse() {
      final var expression = expressionLanguage.parseExpression("=if a < 5 then b else c");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a", "b", "c"));
    }

    @Test
    public void shouldExtractVariablesFromInstanceOf() {
      final var expression = expressionLanguage.parseExpression("=a instance of string");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a"));
    }
  }

  @Nested
  class ListExpressions {

    @Test
    public void shouldExtractVariablesFromList() {
      final var expression = expressionLanguage.parseExpression("=[a, b]");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a", "b"));
    }

    @Test
    public void shouldExtractVariablesFromListFilter() {
      final var expression = expressionLanguage.parseExpression("=[1, 2][item < a]");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a"));
    }
  }

  @Nested
  class ContextExpressions {

    @Test
    public void shouldExtractVariablesFromContextValues() {
      // context keys are not variables; only values are
      final var expression = expressionLanguage.parseExpression("={a: c, b: d}");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("c", "d"));
    }

    @Test
    public void shouldNotIncludeNestedContextEntryReferences() {
      // 'a' defined in the context entry can be referenced in subsequent entries
      // so it should not be treated as an external variable
      final var expression = expressionLanguage.parseExpression("={a: c, b: a + d}");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("c", "d"));
    }

    @Test
    public void shouldNotIncludeNestedContextEntryReferencesLevel2() {
      final var expression = expressionLanguage.parseExpression("={a: c, b: {d: a + e}}");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("c", "e"));
    }

    @Test
    public void shouldNotIncludeContextEntriesInListFilter() {
      final var expression = expressionLanguage.parseExpression("=[{a: b}][a < c]");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("b", "c"));
    }

    @Test
    public void shouldExtractVariablesFromPathExpressionOnContext() {
      final var expression = expressionLanguage.parseExpression("={a: b}.a");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("b"));
    }
  }

  @Nested
  class RangeExpressions {

    @Test
    public void shouldExtractVariablesFromRange() {
      final var expression = expressionLanguage.parseExpression("=[a..b)");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a", "b"));
    }

    @Test
    public void shouldExtractVariablesFromInExpression() {
      final var expression = expressionLanguage.parseExpression("=a in (0..b)");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a", "b"));
    }
  }

  @Nested
  class IterationExpressions {

    @Test
    public void shouldExtractVariablesFromSomeExpression() {
      // 'x' is the iteration variable bound in the expression, not an external variable
      final var expression = expressionLanguage.parseExpression("=some x in [a] satisfies x < b");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a", "b"));
    }

    @Test
    public void shouldExtractVariablesFromEveryExpression() {
      final var expression = expressionLanguage.parseExpression("=every x in [a] satisfies x < b");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a", "b"));
    }

    @Test
    public void shouldExtractVariablesFromForExpression() {
      final var expression = expressionLanguage.parseExpression("=for x in [a] return x + b");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a", "b"));
    }
  }

  @Nested
  class FunctionExpressions {

    @Test
    public void shouldExtractVariablesFromFunctionInvocationWithPositionalArgs() {
      final var expression = expressionLanguage.parseExpression("=ceiling(a)");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a"));
    }

    @Test
    public void shouldExtractVariablesFromFunctionInvocationWithNamedArgs() {
      final var expression = expressionLanguage.parseExpression("=ceiling(n: a)");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("a"));
    }

    @Test
    public void shouldExtractVariablesFromFunctionDefinition() {
      // 'a' is a function parameter, so it's not an external variable; only 'b' is
      final var expression = expressionLanguage.parseExpression("=function(a) a + b");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("b"));
    }
  }

  @Nested
  class ConditionalEventPatterns {

    @Test
    public void shouldExtractVariablesFromSimpleCondition() {
      final var expression = expressionLanguage.parseExpression("=x > 10");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("x"));
    }

    @Test
    public void shouldExtractVariablesFromCompoundCondition() {
      final var expression = expressionLanguage.parseExpression("=x > 10 and y < 5");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("x", "y"));
    }

    @Test
    public void shouldExtractVariablesFromConditionWithNestedPath() {
      final var expression = expressionLanguage.parseExpression("=x.y > 10");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("x"));
    }

    @Test
    public void shouldExtractVariablesFromComplexCondition() {
      final var expression = expressionLanguage.parseExpression("=x > 10 or y < 5 or z = 0");
      assertThat(expression.getVariableNames()).isEqualTo(Set.of("x", "y", "z"));
    }
  }
}
