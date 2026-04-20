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
import io.camunda.zeebe.msgpack.MsgPackUtil;
import io.camunda.zeebe.msgpack.spec.MsgPackWriter;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public final class VariableOutputMappingTransformerTest {

  private final VariableMappingTransformer transformer = new VariableMappingTransformer();
  private final ExpressionLanguage expressionLanguage =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));

  public static Object[][] parametersSuccessfulEvaluationToObject() {
    return new Object[][] {
      // {mappings, localVars, parentScopeVars, expectedOutput}
      // no mappings
      {List.of(), Map.of(), Map.of(), "{}"},
      // direct mapping
      {List.of(mapping("x", "x")), Map.of("x", asMsgPack("1")), Map.of(), "{'x':1}"},
      {List.of(mapping("x", "a")), Map.of("x", asMsgPack("1")), Map.of(), "{'a':1}"},
      {List.of(mapping("_x", "_b")), Map.of("_x", asMsgPack("1")), Map.of(), "{'_b':1}"},
      {
        List.of(mapping("x", "a"), mapping("y", "b")),
        Map.of("x", asMsgPack("1"), "y", asMsgPack("2")),
        Map.of(),
        "{'a':1, 'b':2}"
      },
      {List.of(mapping("x", "a")), Map.of("x", asMsgPack("{'y':1}")), Map.of(), "{'a':{'y':1}}"},
      // nested target
      {List.of(mapping("x", "a.b")), Map.of("x", asMsgPack("1")), Map.of(), "{'a':{'b':1}}"},
      {
        List.of(mapping("x", "a.b"), mapping("y", "a.c")),
        Map.of("x", asMsgPack("1"), "y", asMsgPack("2")),
        Map.of(),
        "{'a':{'b':1, 'c':2}}"
      },
      {
        List.of(mapping("x", "a.b.c")), Map.of("x", asMsgPack("1")), Map.of(), "{'a':{'b':{'c':1}}}"
      },
      {
        List.of(mapping("x", "a.b")),
        Map.of("x", asMsgPack("1")),
        Map.of("a", asMsgPack("{}")),
        "{'a':{'b':1}}"
      },
      // nested source
      {List.of(mapping("x.y", "a")), Map.of("x", asMsgPack("{'y':1}")), Map.of(), "{'a':1}"},
      {
        List.of(mapping("x.y", "a"), mapping("x.z", "b")),
        Map.of("x", asMsgPack("{'y':1, 'z':2}")),
        Map.of(),
        "{'a':1, 'b':2}"
      },
      {
        List.of(mapping("x.y", "a.b"), mapping("x.z", "a.c")),
        Map.of("x", asMsgPack("{'y':1, 'z':2}")),
        Map.of(),
        "{'a': {'b':1, 'c':2}}"
      },
      {
        List.of(mapping("x.y.z", "a.b.c")),
        Map.of("x", asMsgPack("{'y':{'z':2}, 'w':3}")),
        Map.of(),
        "{'a': {'b':{'c':2}}}"
      },
      // override variable in parent scope
      {
        List.of(mapping("x", "a")),
        Map.of("x", asMsgPack("1")),
        Map.of("a", asMsgPack("{'b':2}")),
        "{'a':1}"
      },
      // merge target with parent scope variable
      {
        List.of(mapping("x", "a.b")),
        Map.of("x", asMsgPack("1")),
        Map.of("a", asMsgPack("{'c':2}")),
        "{'a':{'b':1,'c':2}}"
      },
      {
        List.of(mapping("x", "a.b.c")),
        Map.of("x", asMsgPack("1")),
        Map.of("a", asMsgPack("{'b':{'d':2}, 'e':3}")),
        "{'a':{'b':{'c':1, 'd':2}, 'e':3}}"
      },
      // override nested property in parent scope
      {
        List.of(mapping("x", "a.b")),
        Map.of("x", asMsgPack("1")),
        Map.of("a", asMsgPack("{'b':2}")),
        "{'a':{'b':1}}"
      },
      {
        List.of(mapping("x", "a.b"), mapping("x", "a.c")),
        Map.of("x", asMsgPack("1")),
        Map.of("a", asMsgPack("{'d':2}")),
        "{'a':{'b':1, 'c':1, 'd':2}}"
      },
      {
        List.of(mapping("x.y.z", "a.b.c")),
        Map.of("x", asMsgPack("{'y':{'z':4}, 'w':4}")),
        Map.of("a", asMsgPack("{'b':{'c':3}, 'w':5}")),
        "{'a': {'b':{'c':4}, 'w':5}}"
      },
      // evaluate mappings in order
      {
        List.of(mapping("x", "a"), mapping("a + 1", "b")),
        Map.of("x", asMsgPack("1")),
        Map.of(),
        "{'a':1, 'b':2}"
      },
      // override previous mapping
      {
        List.of(mapping("x", "a"), mapping("y", "a")),
        Map.of("x", asMsgPack("1"), "y", asMsgPack("2")),
        Map.of(),
        "{'a':2}"
      },
      {
        List.of(mapping("x", "a"), mapping("y", "a.b")),
        Map.of("x", asMsgPack("1"), "y", asMsgPack("2")),
        Map.of(),
        "{'a':{'b':2}}"
      },
      // source FEEL expression
      {List.of(mapping("1", "a")), Map.of(), Map.of(), "{'a':1}"},
      {List.of(mapping("\"foo\"", "a")), Map.of(), Map.of(), "{'a':'foo'}"},
      {List.of(mapping("[1,2,3]", "a")), Map.of(), Map.of(), "{'a':[1,2,3]}"},
      {
        List.of(mapping("x + y", "a")),
        Map.of("x", asMsgPack("1"), "y", asMsgPack("2")),
        Map.of(),
        "{'a':3}"
      },
      {
        List.of(mapping("{x:x, y:y}", "a")),
        Map.of("x", asMsgPack("1"), "y", asMsgPack("2")),
        Map.of(),
        "{'a':{'x':1, 'y':2}}"
      },
      {
        List.of(mapping("append(x, y)", "a")),
        Map.of("x", asMsgPack("[1,2]"), "y", asMsgPack("3")),
        Map.of(),
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
        '{b: assert(x, x != null),_camunda_output_context:context put({},["a","b"],b)}._camunda_output_context': \
        The condition is not fulfilled"""
      }, // #9543
    };
  }

  @ParameterizedTest(name = "{index}: with {0} to {3}")
  @MethodSource("parametersSuccessfulEvaluationToObject")
  public void shouldEvaluateToObject(
      final List<ZeebeMapping> mappings,
      final Map<String, DirectBuffer> localVars,
      final Map<String, DirectBuffer> parentScopeVars,
      final String expectedOutput) {
    // given
    final var expression = transformer.transformOutputMappings(mappings, expressionLanguage);

    assertThat(expression.isValid())
        .describedAs("Expected valid expression: %s", expression.getFailureMessage())
        .isTrue();

    // when — mirror runtime: evaluate expression against local scope, then merge with parent scope
    final var result = expressionLanguage.evaluateExpression(expression, localVars::get);

    // then
    assertThat(result.getType()).isEqualTo(ResultType.OBJECT);

    final DirectBuffer localScopeResult = result.toBuffer();
    final DirectBuffer parentScopeDocument = variablesAsDocument(parentScopeVars);
    final DirectBuffer merged =
        MsgPackUtil.mergeMsgPackDocuments(parentScopeDocument, localScopeResult);

    io.camunda.zeebe.test.util.MsgPackUtil.assertEquality(merged, expectedOutput);
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

  private static DirectBuffer variablesAsDocument(final Map<String, DirectBuffer> variables) {
    final var buffer = new ExpandableArrayBuffer();
    final var writer = new MsgPackWriter();
    writer.wrap(buffer, 0);
    writer.writeMapHeader(variables.size());
    for (final var entry : variables.entrySet()) {
      writer.writeString(BufferUtil.wrapString(entry.getKey()));
      writer.writeRaw(entry.getValue());
    }
    return new UnsafeBuffer(buffer, 0, writer.getOffset());
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
