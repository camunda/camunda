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

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.deployment.model.element.OutputMapping;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.VariableMappingTransformer;
import io.camunda.zeebe.engine.processing.variable.MappingResultBuilder;
import io.camunda.zeebe.engine.processing.variable.MsgPackPath;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeMapping;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.util.Either;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.agrona.DirectBuffer;
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
      // `context merge` on a non-context value silently nulls the variable instead of failing
      {List.of(mapping("1", "a.b")), Map.of("a", asMsgPack("5")), "{'a':null}"},
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
      // declaration order preserved across regrouped nested targets (#11789 / #56387): an entry
      // declared BETWEEN two entries of the same parent target sees the in-between assignment
      {
        List.of(mapping("1", "a.b"), mapping("x", "c"), mapping("c", "a.d")),
        Map.of("x", asMsgPack("1")),
        "{'a':{'b':1, 'd':1}, 'c':1}"
      },
      {
        List.of(
            mapping("\"some text\"", "nested.property"),
            mapping("\"abc\"", "notNested"),
            mapping("notNested", "nested.nested.property"),
            mapping("notNested", "notNestedAssigned")),
        Map.of(),
        "{'nested':{'property':'some text', 'nested':{'property':'abc'}}, "
            + "'notNested':'abc', 'notNestedAssigned':'abc'}"
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
        ' assert(x, x != null)': The condition is not fulfilled"""
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
    final var outputMappings = transformer.transformOutputMappings(mappings, expressionLanguage);
    outputMappings.forEach(
        mapping ->
            assertThat(mapping.source().isValid())
                .describedAs("Expected valid expression: %s", mapping.source().getFailureMessage())
                .isTrue());

    // when
    final var document = evaluateOutputMappings(outputMappings, variables, variables);

    // then
    MsgPackUtil.assertEquality(document, expectedOutput);
  }

  @ParameterizedTest(name = "{index}: mapping {0} fails with: {2}")
  @MethodSource("parametersEvaluationToFailure")
  public void shouldEvaluateToFailure(
      final List<ZeebeMapping> mappings,
      final Map<String, DirectBuffer> variables,
      final String failureMessage) {
    // given
    final var outputMappings = transformer.transformOutputMappings(mappings, expressionLanguage);

    // when: evaluate one by one, stopping at the first failure (mirrors runtime fail-fast)
    final var resultBuilder = new MappingResultBuilder(resolver(variables), resolver(variables));
    EvaluationResult failure = null;
    for (final var mapping : outputMappings) {
      final EvaluationContext context =
          name -> {
            final var accumulated = resultBuilder.getVariable(name);
            return Either.left(accumulated != null ? accumulated : variables.get(name));
          };
      final var result = expressionLanguage.evaluateExpression(mapping.source(), context);
      if (result.isFailure()) {
        failure = result;
        break;
      }
      resultBuilder.put(mapping.targetPath(), result.toBuffer());
    }

    // then
    assertThat(failure).isNotNull();
    assertThat(failure.getFailureMessage()).isEqualTo(failureMessage);
  }

  @Test
  void shouldNotLeakUntouchedSiblingIntoMergeTargetOppositeOrder() {
    // given: 'a' exists only on the element, with an untouched sibling 'p'
    final var mappings = List.of(mapping("a", "d"), mapping("1", "a.b"));
    final Map<String, DirectBuffer> scopeChain = Map.of("a", asMsgPack("{'p':0}"));
    final var outputMappings = transformer.transformOutputMappings(mappings, expressionLanguage);

    // when
    final var document = evaluateOutputMappings(outputMappings, scopeChain, Map.of());

    // then: the back-reference ran before 'a' was written, so it reads the scope chain
    MsgPackUtil.assertEquality(document, "{'a':{'b':1}, 'd':{'p':0}}");
  }

  @Test
  void shouldNotLeakUntouchedSiblingIntoMergeTargetOrBackReference() {
    // given: same as the sibling case, but the nested write happens first
    final var mappings = List.of(mapping("1", "a.b"), mapping("a", "d"));
    final Map<String, DirectBuffer> scopeChain = Map.of("a", asMsgPack("{'p':0}"));
    final var outputMappings = transformer.transformOutputMappings(mappings, expressionLanguage);

    // when
    final var document = evaluateOutputMappings(outputMappings, scopeChain, Map.of());

    // then: 'a' propagates only the mapped path, but the later back-reference still reads the
    // element's own view of 'a' - the write does not hide branches nobody mapped
    MsgPackUtil.assertEquality(document, "{'a':{'b':1}, 'd':{'p':0,'b':1}}");
  }

  @Test
  void shouldEvaluateAgainstScopeChainButEmitOnlyMappedPaths() {
    // given: 'data' is visible to the element with three branches; only two are mapped, and the
    // second mapping reads a branch the first one did not write
    final var mappings =
        List.of(mapping("data.wanted1", "data.wanted1"), mapping("data.wanted2", "data.wanted2"));
    final Map<String, DirectBuffer> scopeChain =
        Map.of("data", asMsgPack("{'wanted1':'A','wanted2':'B','unmapped':'C'}"));
    final var outputMappings = transformer.transformOutputMappings(mappings, expressionLanguage);

    // when
    final var document = evaluateOutputMappings(outputMappings, scopeChain, Map.of());

    // then: both mapped branches survive, the unmapped one is not propagated
    MsgPackUtil.assertEquality(document, "{'data':{'wanted1':'A','wanted2':'B'}}");
  }

  @Test
  void shouldResolveBackReferenceToAWrittenNestedPath() {
    // given
    final var mappings = List.of(mapping("1", "a.b"), mapping("a.b", "foo"));
    final var outputMappings = transformer.transformOutputMappings(mappings, expressionLanguage);

    // when
    final var document = evaluateOutputMappings(outputMappings, Map.of(), Map.of());

    // then
    MsgPackUtil.assertEquality(document, "{'a':{'b':1}, 'foo':1}");
  }

  @Test
  void shouldMergeWrittenNestedPathIntoTheMergeTargetValue() {
    // given: the merge target already holds 'a' with its own sibling 'c', which is therefore also
    // visible to the element by walking up the scope chain
    final var mappings = List.of(mapping("1", "a.b"), mapping("a.b", "foo"));
    final Map<String, DirectBuffer> variables = Map.of("a", asMsgPack("{'c':2}"));
    final var outputMappings = transformer.transformOutputMappings(mappings, expressionLanguage);

    // when
    final var document = evaluateOutputMappings(outputMappings, variables, variables);

    // then: 'c' survives because it belongs to the merge target
    MsgPackUtil.assertEquality(document, "{'a':{'b':1,'c':2}, 'foo':1}");
  }

  @Test
  void shouldResolveBackReferenceToAnUnwrittenBranchFromTheScopeChain() {
    // given: 'a.c' exists only on the element and is never a mapping target
    final var mappings = List.of(mapping("1", "a.b"), mapping("a.c", "foo"));
    final Map<String, DirectBuffer> scopeChain = Map.of("a", asMsgPack("{'c':9}"));
    final var outputMappings = transformer.transformOutputMappings(mappings, expressionLanguage);

    // when
    final var document = evaluateOutputMappings(outputMappings, scopeChain, Map.of());

    // then: the back-reference reads 'c' even though the earlier write did not include it, and 'c'
    // still does not propagate
    MsgPackUtil.assertEquality(document, "{'a':{'b':1}, 'foo':9}");
  }

  @Test
  void shouldSeedEvaluationAndMergeTargetIndependently() {
    // given: 'p' and 'q' are element-local, 'c' belongs to the merge target (and is therefore
    // visible on the scope chain too)
    final var mappings = List.of(mapping("1", "a.b"), mapping("a", "d"));
    final Map<String, DirectBuffer> scopeChain = Map.of("a", asMsgPack("{'p':0,'q':1,'c':2}"));
    final Map<String, DirectBuffer> mergeTarget = Map.of("a", asMsgPack("{'c':2}"));
    final var outputMappings = transformer.transformOutputMappings(mappings, expressionLanguage);

    // when
    final var document = evaluateOutputMappings(outputMappings, scopeChain, mergeTarget);

    // then
    MsgPackUtil.assertEquality(document, "{'a':{'c':2,'b':1}, 'd':{'p':0,'q':1,'c':2,'b':1}}");
  }

  // evaluates output mappings one by one in modeling order, mirroring
  // BpmnVariableMappingBehavior.applyOutputMappings; a parent-only variable belongs in BOTH
  // scopeChain and mergeTarget, since it's visible both to the element and to the merge target -
  // only element-local variables appear in scopeChain alone
  private DirectBuffer evaluateOutputMappings(
      final List<OutputMapping> outputMappings,
      final Map<String, DirectBuffer> scopeChain,
      final Map<String, DirectBuffer> mergeTarget) {
    final var resultBuilder = new MappingResultBuilder(resolver(scopeChain), resolver(mergeTarget));
    for (final var mapping : outputMappings) {
      final EvaluationContext context =
          name -> {
            final var accumulated = resultBuilder.getVariable(name);
            return Either.left(accumulated != null ? accumulated : scopeChain.get(name));
          };
      final var result = expressionLanguage.evaluateExpression(mapping.source(), context);
      assertThat(result.isFailure())
          .describedAs("Expected successful evaluation: %s", result.getFailureMessage())
          .isFalse();
      resultBuilder.put(mapping.targetPath(), result.toBuffer());
    }
    return resultBuilder.toDocument();
  }

  private static Function<List<String>, DirectBuffer> resolver(
      final Map<String, DirectBuffer> variables) {
    return path ->
        Optional.ofNullable(variables.get(path.getFirst()))
            .map(rootValue -> MsgPackPath.navigate(rootValue, path, 1))
            .orElse(null);
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
