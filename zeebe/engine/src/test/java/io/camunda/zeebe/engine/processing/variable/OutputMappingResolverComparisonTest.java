/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static io.camunda.zeebe.test.util.asserts.EitherAssert.assertThat;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.VariableMappingTransformer;
import io.camunda.zeebe.engine.processing.expression.ScopedEvaluationContext;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeMapping;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.test.util.MsgPackUtil;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OutputMappingResolverComparisonTest {

  private static final ExpressionLanguage EXPRESSION_LANGUAGE =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private static final Duration DEFAULT_TIMEOUT =
      io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_EXPRESSION_EVALUATION_TIMEOUT;

  private static final OrderedOutputMappingResolver ORDERED = new OrderedOutputMappingResolver();
  private static final CombinedOutputMappingResolver COMBINED = new CombinedOutputMappingResolver();

  @Nested
  @DisplayName("Non-overlapping targets")
  class NonOverlappingTargets {
    @Test
    void shouldProduceSameResultForDistinctTargets() {
      final var r =
          Helpers.resolve(
              List.of(Helpers.mapping("=x", "a"), Helpers.mapping("=y", "b")),
              Map.of("x", 1, "y", 2));
      Helpers.assertSame(r, "{'a':1,'b':2}");
    }
  }

  @Nested
  @DisplayName("Duplicate target with intermediate read")
  class DuplicateTargetWithIntermediateRead {
    @Test
    void shouldDifferOnIntermediateValueVisibility() {
      final var r =
          Helpers.resolve(
              List.of(
                  Helpers.mapping("=1", "x"),
                  Helpers.mapping("=x", "y"),
                  Helpers.mapping("=2", "x")),
              Map.of());
      Helpers.assertDiffers(r, "{'x':2,'y':1}", "{'x':2,'y':2}");
    }
  }

  @Nested
  @DisplayName("Nested target scope seeding")
  class NestedTargetScopeSeeding {
    @Test
    void shouldPreserveSiblingKeysForNestedTarget() {
      final var scope = Map.<String, Object>of("a", Map.of("b", "old", "c", "kept"));
      final var r = Helpers.resolve(List.of(Helpers.mapping("=x", "a.b")), Map.of("x", 42), scope);
      Helpers.assertSame(r, "{'a':{'b':42,'c':'kept'}}");
    }
  }

  static final class Helpers {

    static ResolverResults resolve(final List<ZeebeMapping> m, final Map<String, Object> jobVars) {
      return resolve(m, jobVars, Map.of());
    }

    static ResolverResults resolve(
        final List<ZeebeMapping> m,
        final Map<String, Object> jobVars,
        final Map<String, Object> elementScope) {
      final var outputMappings =
          new VariableMappingTransformer().transformOutputMappings(m, EXPRESSION_LANGUAGE);
      final var processor = buildProcessor(jobVars, elementScope);
      return new ResolverResults(
          ORDERED.resolve(outputMappings, processor), COMBINED.resolve(outputMappings, processor));
    }

    private static MappingExpressionProcessor buildProcessor(
        final Map<String, Object> jobVars, final Map<String, Object> elementScope) {
      final var ej = encode(jobVars);
      final var ee = encode(elementScope);
      final ScopedEvaluationContext ctx = name -> Either.left(ee.getOrDefault(name, ej.get(name)));
      final var ctx2 = new MappingContext(BufferUtil.wrapString("t"), -1L, -1L, -1L, "");
      return new MappingExpressionProcessor(
          new ExpressionProcessor(EXPRESSION_LANGUAGE, ctx, DEFAULT_TIMEOUT), ctx2);
    }

    private static Map<String, DirectBuffer> encode(final Map<String, Object> vars) {
      return vars.entrySet().stream()
          .collect(
              Collectors.toMap(
                  Map.Entry::getKey,
                  e -> new UnsafeBuffer(MsgPackConverter.convertToMsgPack(e.getValue()))));
    }

    static void assertSame(final ResolverResults r, final String expected) {
      assertThat(r.ordered()).isRight();
      assertThat(r.combined()).isRight();
      MsgPackUtil.assertEquality(r.ordered().get(), expected);
      MsgPackUtil.assertEquality(r.combined().get(), expected);
    }

    static void assertDiffers(
        final ResolverResults r, final String expectedOrdered, final String expectedCombined) {
      assertThat(r.ordered()).isRight();
      assertThat(r.combined()).isRight();
      MsgPackUtil.assertEquality(r.ordered().get(), expectedOrdered);
      MsgPackUtil.assertEquality(r.combined().get(), expectedCombined);
    }

    static ZeebeMapping mapping(final String source, final String target) {
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

  record ResolverResults(
      Either<Failure, DirectBuffer> ordered, Either<Failure, DirectBuffer> combined) {}
}
