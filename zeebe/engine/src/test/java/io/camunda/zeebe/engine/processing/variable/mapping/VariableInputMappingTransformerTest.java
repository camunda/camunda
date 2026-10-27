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
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.VariableMappingTransformer;
import io.camunda.zeebe.engine.processing.variable.InputMappingResultBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeMapping;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.util.Either;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

final class VariableInputMappingTransformerTest {

  private final VariableMappingTransformer transformer = new VariableMappingTransformer();
  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));

  static Object[][] parameters() {
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
      // a nested target shadows the outer scope's "a" only for the key it defines; the mapping that
      // reads "a" gets that key layered over the outer scope's value, not instead of it
      {
        List.of(mapping("x", "a.b"), mapping("a", "c")),
        Map.of("x", asMsgPack("1"), "a", asMsgPack("{'z':99}")),
        "{'a':{'b':1}, 'c':{'b':1, 'z':99}}"
      },
      // narrowing an object onto its own name is an identity copy for the fields it names, not a
      // last-one-wins race between them — issue #59646
      {
        List.of(mapping("x.a", "x.a"), mapping("x.b", "x.b")),
        Map.of("x", asMsgPack("{'a':1, 'b':2}")),
        "{'x':{'a':1, 'b':2}}"
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
      {List.of(mapping("a.b.c", "x")), Map.of("a", asMsgPack("{'b':{'c':42}}")), "{'x':42}"},
      // source path through a type mismatch: "a" is a scalar, ".b" cannot be resolved
      // on it - the mapping still evaluates to null instead of failing
      {List.of(mapping("a.b", "x")), Map.of("a", asMsgPack("5")), "{'x':null}"},
      // explicit null source value is merged as-is, not treated as absent
      {List.of(mapping("x", "y")), Map.of("x", asMsgPack("null")), "{'y':null}"},
      // an accumulated null still shadows the outer scope, unlike a never-mapped name
      {
        List.of(mapping("null", "x"), mapping("x", "y")),
        Map.of("x", asMsgPack("5")),
        "{'x':null, 'y':null}"
      },
      // missing source in a multi-mapping only nulls that one target
      {
        List.of(mapping("x", "a"), mapping("missing", "b")),
        Map.of("x", asMsgPack("1")),
        "{'a':1, 'b':null}"
      },
      // back-references between input mappings resolve to the earlier context entry, shadowing
      // a same-named outer-scope variable
      {
        List.of(mapping("1", "y"), mapping("y", "z")),
        Map.of("y", asMsgPack("10")),
        "{'y':1, 'z':1}"
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
      // list projection: source path over a list of contexts returns a list
      {
        List.of(mapping("orders.id", "ids")),
        Map.of("orders", asMsgPack("[{'id':1}, {'id':2}]")),
        "{'ids':[1,2]}"
      },
      // FEEL list indices are 1-based; negative indices count from the end
      {
        List.of(mapping("orders[1].id", "first"), mapping("orders[-1].id", "last")),
        Map.of("orders", asMsgPack("[{'id':1}, {'id':2}]")),
        "{'first':1, 'last':2}"
      },
      {
        List.of(mapping("orders[0].id", "first"), mapping("orders[-1].id", "last")),
        Map.of("orders", asMsgPack("[{'id':1}, {'id':2}]")),
        "{'first':null, 'last':2}"
      },
      // malformed targets: a. is valid (trailing empty segment dropped by String.split),
      // a..b and `a.b` produce unparseable FEEL context keys and throw — see unparseableTargets()
      {List.of(mapping("x", "a.")), Map.of("x", asMsgPack("1")), "{'a':1}"},
      // reserved-word/space targets are also deploy-time rejected, but would work fine here since
      // input targets are plain path segments, never FEEL identifiers
      {List.of(mapping("x", "for")), Map.of("x", asMsgPack("1")), "{'for':1}"},
      {List.of(mapping("x", "my var")), Map.of("x", asMsgPack("1")), "{'my var':1}"},
      // evaluate mappings in order
      {
        List.of(mapping("x", "a"), mapping("a + 1", "b")),
        Map.of("x", asMsgPack("1")),
        "{'a':1, 'b':2}"
      },
      // a later entry's source cannot see an as-yet-undeclared later target - it falls back to
      // whatever that name resolves to in the outer scope instead
      {
        List.of(mapping("z", "a"), mapping("1", "z")), Map.of("z", asMsgPack("9")), "{'a':9, 'z':1}"
      },
      // circular references between two entries: the second resolves the first's fresh context
      // entry, so both end up holding the pre-mapping value of the entry declared second
      {
        List.of(mapping("b", "a"), mapping("a", "b")),
        Map.of("a", asMsgPack("1"), "b", asMsgPack("2")),
        "{'a':2, 'b':2}"
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
      // same overlap, opposite declaration order: now "a.b" is the one silently dropped instead
      {
        List.of(mapping("y", "a.b"), mapping("x", "a")),
        Map.of("x", asMsgPack("1"), "y", asMsgPack("2")),
        "{'a':1}"
      },
    };
  }

  @ParameterizedTest(name = "with {0} to {2}")
  @MethodSource("parameters")
  void shouldApplyMappings(
      final List<ZeebeMapping> mappings,
      final Map<String, DirectBuffer> variables,
      final String expectedOutput) {
    MsgPackUtil.assertEquality(evaluate(mappings, variables, expressionLanguage), expectedOutput);
  }

  static Object[][] unparseableTargets() {
    return new Object[][] {
      // a..b splits to ["a","","b"] — empty segment is not a valid FEEL identifier
      {mapping("x", "a..b")},
      // `a.b` splits to ["`a","b`"] — backtick-wrapped segments are not valid FEEL identifiers
      {mapping("x", "`a.b`")},
    };
  }

  @ParameterizedTest(name = "target ''{0}''")
  @MethodSource("unparseableTargets")
  void shouldThrowWhenTargetCreatesUnparseableCombinedExpression(final ZeebeMapping mapping) {
    assertThatThrownBy(
            () -> transformer.transformInputMappings(List.of(mapping), expressionLanguage))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Failed to build variable mapping expression:");
  }

  @Test
  void shouldPreserveDeclarationOrderAcrossRegroupedTargets() {
    // c is declared BETWEEN the two "a.*" entries, so the user reasonably expects a.d to
    // see c's just-assigned value
    final var mappings = List.of(mapping("1", "a.b"), mapping("x", "c"), mapping("c", "a.d"));
    MsgPackUtil.assertEquality(
        evaluate(mappings, Map.of("x", asMsgPack("1")), expressionLanguage),
        "{'a':{'b':1, 'd':1}, 'c':1}");
  }

  @Test
  void shouldEvaluateNowDeterministicallyPerClock() {
    // given: now() is deterministic per clock instant, not a fixed/static value
    final var mappings = List.of(mapping("now()", "a"));
    final var firstClockLanguage =
        ExpressionLanguageFactory.createExpressionLanguage(
            new ZeebeFeelEngineClock(InstantSource.fixed(Instant.parse("2024-01-01T00:00:00Z"))));
    final var secondClockLanguage =
        ExpressionLanguageFactory.createExpressionLanguage(
            new ZeebeFeelEngineClock(InstantSource.fixed(Instant.parse("2025-06-15T12:30:00Z"))));

    // when
    final var firstResult = evaluate(mappings, Map.of(), firstClockLanguage);
    final var firstResultRepeated = evaluate(mappings, Map.of(), firstClockLanguage);
    final var secondResult = evaluate(mappings, Map.of(), secondClockLanguage);

    // then: the same fixed clock instant always evaluates to the same value ...
    assertThat(firstResult).isEqualTo(firstResultRepeated);
    // ... but a different clock instant evaluates to a different value
    assertThat(firstResult).isNotEqualTo(secondResult);
  }

  private DirectBuffer evaluate(
      final List<ZeebeMapping> mappings,
      final Map<String, DirectBuffer> variables,
      final ExpressionLanguage language) {
    final var inputMappings = transformer.transformInputMappings(mappings, language);
    final var ep =
        new ExpressionProcessor(
            language,
            name -> Either.left(variables.get(name)),
            EngineConfiguration.DEFAULT_EXPRESSION_EVALUATION_TIMEOUT);
    final var resultBuilder = new InputMappingResultBuilder(variables::get);
    for (final var mapping : inputMappings.mappings()) {
      final var buffer =
          ep.prependContext(name -> Either.left(resultBuilder.get(name)))
              .evaluateVariableMappingExpression(mapping.source(), -1L, "");
      if (buffer.isLeft()) {
        throw new IllegalStateException("Evaluation failed: " + buffer.getLeft().getMessage());
      }
      resultBuilder.put(mapping.targetPath(), buffer.get());
    }
    return resultBuilder.toDocument();
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
