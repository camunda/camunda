/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.TableSchema;
import io.camunda.analytics.lake.sink.algebra.Algebras;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The per-variant metrics declaration {@code LakePocApp} builds — {@code
 * dims("process_id","variant_hash")} against the raw {@code instances} schema — is a second,
 * ordinary {@link EntityMetrics} declaration, not special machinery of its own; these tests pin
 * down that it validates, dimensions, and fingerprints exactly like any other declaration once the
 * raw schema carries the (nullable, {@code STRING_DICT}) {@code variant_hash} column added by
 * schema v3 (see {@code IcebergLakeWriter#INSTANCE_SCHEMA}'s own javadoc).
 */
class InstanceVariantMetricsDeclarationTest {

  /**
   * Mirrors the real {@code instances} raw schema's relevant columns, including {@code
   * variant_hash} (schema v3, nullable).
   */
  private static TableSchema instancesLikeRawSchema() {
    return new TableSchema(
        "instances",
        List.of(
            new TableSchema.Column("process_id", ColumnType.STRING_DICT, 1, false, 0, false),
            new TableSchema.Column(
                "ended_at",
                ColumnType.LONG,
                2,
                false,
                -1,
                true,
                TableSchema.LogicalType.TIMESTAMPTZ),
            new TableSchema.Column("duration_ms", ColumnType.LONG, 3, false, -1, false),
            new TableSchema.Column("variant_hash", ColumnType.STRING_DICT, 4, true, -1, false)));
  }

  @Test
  void shouldDeclareAndCompileAgainstTheNullableVariantHashColumn() {
    // given/when
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("instance_variants", instancesLikeRawSchema())
            .dims("process_id", "variant_hash")
            .window(Duration.ofMinutes(1), "ended_at")
            .measure("duration_ms", Algebras.scalarStats(), Algebras.expHistogram(3))
            .build();

    // then
    assertThat(compiled.dims()).containsExactly("process_id", "variant_hash");
    assertThat(compiled.entityName()).isEqualTo("instance_variants");
  }

  @Test
  void shouldIncludeVariantHashAsADimensionColumnInTheGeneratedMetricsSchema() {
    // given
    final CompiledEntityMetrics compiled =
        EntityMetrics.declare("instance_variants", instancesLikeRawSchema())
            .dims("process_id", "variant_hash")
            .window(Duration.ofMinutes(1), "ended_at")
            .measure("duration_ms", Algebras.scalarStats(), Algebras.expHistogram(3))
            .build();

    // when
    final TableSchema metricsSchema = compiled.metricsSchema();

    // then
    assertThat(metricsSchema.table()).isEqualTo("instance_variants_metrics");
    assertThat(metricsSchema.columns())
        .extracting(TableSchema.Column::name)
        .contains("process_id", "variant_hash");
  }

  @Test
  void shouldProduceAStableFingerprintThatChangesWithDimensions() {
    // given: the same declaration built twice
    final CompiledEntityMetrics first =
        EntityMetrics.declare("instance_variants", instancesLikeRawSchema())
            .dims("process_id", "variant_hash")
            .window(Duration.ofMinutes(1), "ended_at")
            .measure("duration_ms", Algebras.scalarStats(), Algebras.expHistogram(3))
            .build();
    final CompiledEntityMetrics second =
        EntityMetrics.declare("instance_variants", instancesLikeRawSchema())
            .dims("process_id", "variant_hash")
            .window(Duration.ofMinutes(1), "ended_at")
            .measure("duration_ms", Algebras.scalarStats(), Algebras.expHistogram(3))
            .build();

    // and a declaration over just "process_id" (matching the instance-level "instances" entity's
    // own dims) -- a materially different declaration
    final CompiledEntityMetrics differentDims =
        EntityMetrics.declare("instances", instancesLikeRawSchema())
            .dims("process_id")
            .window(Duration.ofMinutes(1), "ended_at")
            .measure("duration_ms", Algebras.scalarStats(), Algebras.expHistogram(3))
            .build();

    // then: identical declarations fingerprint identically; a different one does not
    assertThat(first.fingerprint()).isEqualTo(second.fingerprint());
    assertThat(first.fingerprint()).isNotEqualTo(differentDims.fingerprint());
  }
}
