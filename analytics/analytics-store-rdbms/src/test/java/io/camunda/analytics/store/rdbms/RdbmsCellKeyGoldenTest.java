/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dimension.DimensionColumn;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionKeySelector;
import io.camunda.analytics.dimension.DimensionSchema;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Golden-value pin of the rendered {@code cell_key} (ADR 0008, invariant 1) — the durable serving
 * identity every upsert is keyed by.
 *
 * <p>THE EXPECTED STRINGS BELOW ARE A FROZEN CONTRACT — do not regenerate them to make a failing
 * build green. Rows keyed by these strings already exist in serving stores; the byte-backed {@code
 * DimensionKey} refactor (lazy per-column decode at the serving edge) must render byte-identical
 * {@code cell_key}s or every re-written cell forks instead of overwriting.
 *
 * <p>Rendered format: each dimension value via {@code String.valueOf} (null → a single space), each
 * followed by the U+0001 separator, then {@code '|' + windowStart + '|' + windowSize}. Keys are
 * built the production way — {@link DimensionKeySelector} over {@link Fact}s — so the corpus
 * exercises the exact selector → key → render path Stage 1/2 uses.
 */
final class RdbmsCellKeyGoldenTest {

  private static final long MINUTE = 60_000L;

  private static final DimensionSchema FULL_GRAIN =
      DimensionSchema.of(
          new DimensionColumn("region", DimensionType.STRING),
          new DimensionColumn("bpmnProcessId", DimensionType.STRING),
          new DimensionColumn("processDefinitionKey", DimensionType.LONG),
          new DimensionColumn("version", DimensionType.INT),
          new DimensionColumn("completed", DimensionType.BOOLEAN));

  private static final DimensionSchema NAME_GRAIN =
      DimensionSchema.of(new DimensionColumn("name", DimensionType.STRING));

  private static final DimensionSchema TEXT_GRAIN =
      DimensionSchema.of(new DimensionColumn("note", DimensionType.TEXT));

  private static final DimensionSchema VAR_GRAIN =
      DimensionSchema.of(
          new DimensionColumn("var.region", DimensionType.STRING),
          new DimensionColumn("var.customer", DimensionType.STRING));

  @Test
  void shouldRenderTheFrozenCellKeyForAMultiColumnGrain() {
    // given a key built the production way from a fact carrying every dimension type
    final DimensionKey key =
        selectorKey(
            FULL_GRAIN,
            Map.of(
                "region",
                "EU",
                "bpmnProcessId",
                "invoice",
                "processDefinitionKey",
                1234L,
                "version",
                3,
                "completed",
                true));

    // then — frozen literal
    assertThat(RdbmsNames.cellKey(key, 0L, MINUTE))
        .isEqualTo("EU\u0001invoice\u00011234\u00013\u0001true\u0001|0|60000");
  }

  @Test
  void shouldRenderEveryNullBucketAsASingleSpace() {
    // given a fact with no fields at all — every dimension is the unknown bucket
    final DimensionKey key = selectorKey(FULL_GRAIN, Map.of());

    // then — null renders as one space per column, frozen
    assertThat(RdbmsNames.cellKey(key, MINUTE, MINUTE))
        .isEqualTo(" \u0001 \u0001 \u0001 \u0001 \u0001|60000|60000");
  }

  @Test
  void shouldRenderBoundaryNumericsInPlainDecimal() {
    // given the numeric extremes
    final DimensionKey min =
        selectorKey(
            FULL_GRAIN,
            Map.of(
                "region",
                "US",
                "bpmnProcessId",
                "order",
                "processDefinitionKey",
                Long.MIN_VALUE,
                "version",
                Integer.MIN_VALUE,
                "completed",
                false));
    final DimensionKey max =
        selectorKey(
            FULL_GRAIN,
            Map.of(
                "region",
                "US",
                "bpmnProcessId",
                "order",
                "processDefinitionKey",
                Long.MAX_VALUE,
                "version",
                Integer.MAX_VALUE,
                "completed",
                true));

    // then — frozen literals
    assertThat(RdbmsNames.cellKey(min, 120_000L, MINUTE))
        .isEqualTo(
            "US\u0001order\u0001-9223372036854775808\u0001-2147483648\u0001false\u0001"
                + "|120000|60000");
    assertThat(RdbmsNames.cellKey(max, 120_000L, MINUTE))
        .isEqualTo(
            "US\u0001order\u00019223372036854775807\u00012147483647\u0001true\u0001"
                + "|120000|60000");
  }

  @Test
  void shouldRenderUnicodeAndEmptyStringsVerbatim() {
    // given unicode (2-, 3-, 4-byte UTF-8 incl. an emoji) and the empty string
    final DimensionKey unicode = selectorKey(NAME_GRAIN, Map.of("name", "Ω-café-日本-🚀"));
    final DimensionKey empty = selectorKey(NAME_GRAIN, Map.of("name", ""));

    // then — verbatim, frozen; note "" renders as nothing before the separator, unlike null's " "
    assertThat(RdbmsNames.cellKey(unicode, 0L, 3_600_000L))
        .isEqualTo("Ω-café-日本-🚀\u0001|0|3600000");
    assertThat(RdbmsNames.cellKey(empty, 0L, 3_600_000L)).isEqualTo("\u0001|0|3600000");
  }

  @Test
  void shouldRenderATextDimensionLikeAString() {
    // given a TEXT-typed dimension (large character payload)
    final DimensionKey key = selectorKey(TEXT_GRAIN, Map.of("note", "a text payload"));

    // then — TEXT renders exactly like STRING, frozen
    assertThat(RdbmsNames.cellKey(key, 0L, MINUTE)).isEqualTo("a text payload\u0001|0|60000");
  }

  @Test
  void shouldRenderVariableDimensionsResolvedLazilyOffTheFact() {
    // given var.* dimensions resolved through the fact's lazy variable enrichment, with one
    // variable missing from the visible snapshot (→ unknown bucket)
    final Fact fact =
        Fact.builder(FactType.PROCESS_INSTANCE)
            .variables(() -> Map.of("region", "EU-west"))
            .build();
    final DimensionKey key = new DimensionKeySelector(VAR_GRAIN).getKey(fact);

    // then — frozen literal: the resolved variable value verbatim, the missing one as " "
    assertThat(RdbmsNames.cellKey(key, MINUTE, MINUTE))
        .isEqualTo("EU-west\u0001 \u0001|60000|60000");
  }

  /**
   * The rendering is type-blind ({@code String.valueOf}): a STRING dimension holding {@code "1234"}
   * and a LONG dimension holding {@code 1234L} render the same {@code cell_key}. This is a QUIRK of
   * the current implementation, pinned here because the refactor must reproduce it — cells written
   * under colliding keys today must keep overwriting the same rows.
   */
  @Test
  void shouldCollideAcrossTypesBecauseRenderingIsTypeBlind() {
    // given the same digits as a STRING and as a LONG (different grains, same arity)
    final DimensionSchema stringGrain =
        DimensionSchema.of(new DimensionColumn("d", DimensionType.STRING));
    final DimensionSchema longGrain =
        DimensionSchema.of(new DimensionColumn("d", DimensionType.LONG));

    // then both render to the identical cell_key
    assertThat(RdbmsNames.cellKey(DimensionKey.of(stringGrain, "1234"), 0L, MINUTE))
        .isEqualTo(RdbmsNames.cellKey(DimensionKey.of(longGrain, 1234L), 0L, MINUTE));
  }

  /**
   * A literal single-space STRING value renders identically to the null bucket — another pinned
   * QUIRK of the current renderer the refactor must not "fix" silently.
   */
  @Test
  void shouldCollideBetweenNullBucketAndLiteralSingleSpace() {
    assertThat(RdbmsNames.cellKey(DimensionKey.of(NAME_GRAIN, " "), 0L, MINUTE))
        .isEqualTo(RdbmsNames.cellKey(DimensionKey.of(NAME_GRAIN, (Object) null), 0L, MINUTE));
  }

  /**
   * A dimension value containing the U+0001 separator makes column boundaries ambiguous: two
   * different two-column keys render the same {@code cell_key}. Pinned QUIRK — the refactor's
   * serving-edge render must keep producing the same (colliding) strings.
   */
  @Test
  void shouldCollideWhenAValueContainsTheSeparator() {
    // given two logically different keys whose values embed the separator
    final DimensionSchema twoStrings =
        DimensionSchema.of(
            new DimensionColumn("a", DimensionType.STRING),
            new DimensionColumn("b", DimensionType.STRING));
    final DimensionKey left = DimensionKey.of(twoStrings, "x\u0001y", "z");
    final DimensionKey right = DimensionKey.of(twoStrings, "x", "y\u0001z");

    // then the rendered cell_keys are identical (both logically distinct keys share one row)
    assertThat(left).isNotEqualTo(right);
    assertThat(RdbmsNames.cellKey(left, 0L, MINUTE))
        .isEqualTo(RdbmsNames.cellKey(right, 0L, MINUTE));
  }

  /** Builds the key the production way: a {@link Fact} read by the {@link DimensionKeySelector}. */
  private static DimensionKey selectorKey(
      final DimensionSchema grain, final Map<String, Object> fields) {
    final Fact.Builder builder = Fact.builder(FactType.PROCESS_INSTANCE);
    fields.forEach(builder::field);
    return new DimensionKeySelector(grain).getKey(builder.build());
  }
}
