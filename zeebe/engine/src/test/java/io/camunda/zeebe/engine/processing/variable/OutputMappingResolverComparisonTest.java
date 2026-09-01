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

/**
 * Runs output-mapping scenarios through both {@link CombinedOutputMappingResolver} and {@link
 * OrderedOutputMappingResolver} side by side, documenting where they agree and where they diverge.
 *
 * <p>Shallow by design: variable propagation ({@code propagateVariables}) is not yet part of the
 * resolver contract, so these tests cover only the document returned by {@link
 * MappingResolver#resolve}, not scope application or variable-record emission.
 *
 * <p>Key semantic differences between the two resolvers:
 *
 * <ul>
 *   <li>ORDERED evaluates each mapping in turn; later mappings see earlier results via {@link
 *       OutputMappingResultBuilder}'s accumulated context (declaration-order accumulation).
 *   <li>COMBINED evaluates a single pre-built FEEL context literal; sources are resolved against
 *       the outer (job) scope only — no declaration-order accumulation between mappings. However,
 *       FEEL's own context-literal semantics apply: within the expression, duplicate keys take the
 *       last value, and sibling keys in the same context object are visible to each other via
 *       FEEL's name resolution (e.g. {@code {x:1, y:x}} resolves {@code y=1}).
 *   <li>Both resolvers preserve existing sibling keys at nested target paths. ORDERED uses {@link
 *       OutputMappingResultBuilder} scope seeding; COMBINED uses {@code context merge()} in the
 *       pre-built expression, which is the pre-#59087 behavior.
 * </ul>
 */
class OutputMappingResolverComparisonTest {

  private static final ExpressionLanguage EXPRESSION_LANGUAGE =
      ExpressionLanguageFactory.createExpressionLanguage(
          new ZeebeFeelEngineClock(InstantSource.system()));
  private static final Duration DEFAULT_TIMEOUT =
      io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_EXPRESSION_EVALUATION_TIMEOUT;

  private static final OrderedOutputMappingResolver ORDERED = new OrderedOutputMappingResolver();
  private static final CombinedOutputMappingResolver COMBINED = new CombinedOutputMappingResolver();

  // ── Both resolvers agree ──────────────────────────────────────────────────

  @Nested
  @DisplayName("Non-overlapping targets — both resolvers produce the same document")
  class NonOverlappingTargets {

    @Test
    @DisplayName("simple targets with distinct names resolve identically")
    void shouldProduceSameResultForDistinctTargets() {
      final var results =
          resolve(List.of(mapping("=x", "a"), mapping("=y", "b")), Map.of("x", 1, "y", 2));

      assertSame(results, "{'a':1,'b':2}");
    }
  }

  // ── Resolvers diverge ─────────────────────────────────────────────────────

  @Nested
  @DisplayName("Duplicate target with intermediate read — resolvers diverge")
  class DuplicateTargetWithIntermediateRead {

    @Test
    @DisplayName(
        "ORDERED: y reads x=1 (written by mapping 1) before mapping 3 overwrites x with 2;"
            + " COMBINED: duplicate x is resolved to last value at expression build time, y sees x=2")
    void shouldDifferOnIntermediateValueVisibility() {
      // Mappings: [1→x, x→y, 2→x]. No job variables — sources are literals or self-references.
      final var results =
          resolve(List.of(mapping("=1", "x"), mapping("=x", "y"), mapping("=2", "x")), Map.of());

      assertDiffers(results, "{'x':2,'y':1}", "{'x':2,'y':2}");
    }
  }

  @Nested
  @DisplayName("Nested target scope seeding — both resolvers agree")
  class NestedTargetScopeSeeding {

    @Test
    @DisplayName(
        "Both resolvers preserve existing sibling keys at nested target paths:"
            + " ORDERED uses OutputMappingResultBuilder; COMBINED uses context merge() in the"
            + " pre-built FEEL expression, which is the pre-#59087 behavior")
    void shouldPreserveSiblingKeysForNestedTarget() {
      // Job variable provides x=42. Existing element scope has a={b:old, c:kept}.
      // Mapping [x→a.b]: ORDERED seeds from scope via OutputMappingResultBuilder, COMBINED
      // evaluates {a: if (a != null) then context merge(a, {b:x}) else {b:x}} — both preserve c.
      final var scope = Map.<String, Object>of("a", Map.of("b", "old", "c", "kept"));
      final var results = resolve(List.of(mapping("=x", "a.b")), Map.of("x", 42), scope);

      assertSame(results, "{'a':{'b':42,'c':'kept'}}");
    }
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private static ResolverResults resolve(
      final List<ZeebeMapping> zeebeMappings, final Map<String, Object> jobVars) {
    return resolve(zeebeMappings, jobVars, Map.of());
  }

  /**
   * Resolves the given output mappings through both resolvers.
   *
   * @param zeebeMappings raw mapping declarations (source expression string, target path string)
   * @param jobVars job-completion variables visible as the outer scope during evaluation
   * @param elementScope existing element-scope variables (used by ORDERED for nested-target
   *     seeding)
   */
  private static ResolverResults resolve(
      final List<ZeebeMapping> zeebeMappings,
      final Map<String, Object> jobVars,
      final Map<String, Object> elementScope) {
    final var outputMappings =
        new VariableMappingTransformer()
            .transformOutputMappings(zeebeMappings, EXPRESSION_LANGUAGE);
    final var processor = buildProcessor(jobVars, elementScope);
    return new ResolverResults(
        ORDERED.resolve(outputMappings, processor), COMBINED.resolve(outputMappings, processor));
  }

  private static MappingExpressionProcessor buildProcessor(
      final Map<String, Object> jobVars, final Map<String, Object> elementScope) {
    // Job variables are the primary (outermost) scope; element scope is layered on top for
    // ORDERED's scope-seeding lookup. Both are encoded as MsgPack DirectBuffers.
    final var encodedJob = encode(jobVars);
    final var encodedElement = encode(elementScope);
    final ScopedEvaluationContext ctx =
        name -> {
          // check element scope first (nearest), then job vars
          final var fromElement = encodedElement.get(name);
          if (fromElement != null) {
            return Either.left(fromElement);
          }
          return Either.left(encodedJob.get(name));
        };
    final var context =
        new MappingContext(BufferUtil.wrapString("test-element"), -1L, -1L, -1L, "");
    return new MappingExpressionProcessor(
        new ExpressionProcessor(EXPRESSION_LANGUAGE, ctx, DEFAULT_TIMEOUT), context);
  }

  private static Map<String, DirectBuffer> encode(final Map<String, Object> vars) {
    return vars.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                e -> new UnsafeBuffer(MsgPackConverter.convertToMsgPack(e.getValue()))));
  }

  private static void assertSame(final ResolverResults r, final String expected) {
    assertThat(r.ordered()).isRight();
    assertThat(r.combined()).isRight();
    MsgPackUtil.assertEquality(r.ordered().get(), expected);
    MsgPackUtil.assertEquality(r.combined().get(), expected);
  }

  private static void assertDiffers(
      final ResolverResults r, final String expectedOrdered, final String expectedCombined) {
    assertThat(r.ordered()).isRight();
    assertThat(r.combined()).isRight();
    MsgPackUtil.assertEquality(r.ordered().get(), expectedOrdered);
    MsgPackUtil.assertEquality(r.combined().get(), expectedCombined);
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
        return source + " → " + target;
      }
    };
  }

  // InnerTypeLast: record placed after all methods
  record ResolverResults(
      Either<Failure, DirectBuffer> ordered, Either<Failure, DirectBuffer> combined) {}
}
