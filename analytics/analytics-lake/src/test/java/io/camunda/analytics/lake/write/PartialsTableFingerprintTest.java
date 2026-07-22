/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.write;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.lake.LakeConfig;
import io.camunda.analytics.lake.metrics.CompiledEntityMetrics;
import io.camunda.analytics.lake.metrics.EntityMetrics;
import io.camunda.analytics.lake.sink.ColumnType;
import io.camunda.analytics.lake.sink.TableSchema;
import io.camunda.analytics.lake.sink.algebra.Algebras;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.apache.iceberg.Table;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PartialsTableFingerprintTest {

  private static final TableSchema RAW =
      new TableSchema(
          "activities",
          List.of(
              new TableSchema.Column("process_id", ColumnType.STRING_DICT, 1, false, 0, false),
              new TableSchema.Column(
                  "ended_at",
                  ColumnType.LONG,
                  2,
                  true,
                  -1,
                  false,
                  TableSchema.LogicalType.TIMESTAMPTZ),
              new TableSchema.Column(
                  "started_at",
                  ColumnType.LONG,
                  3,
                  false,
                  1,
                  true,
                  TableSchema.LogicalType.TIMESTAMPTZ),
              new TableSchema.Column("duration_ms", ColumnType.LONG, 4, true, -1, false)));

  @TempDir private Path warehouse;
  private IcebergLakeWriter writer;

  @BeforeEach
  void setUp() {
    writer =
        new IcebergLakeWriter(
            new LakeConfig(
                "http://localhost:0",
                "test-topic",
                "test-group",
                warehouse.resolve("warehouse"),
                warehouse.resolve("state"),
                1,
                2000L,
                0L,
                0L,
                0,
                null));
  }

  @AfterEach
  void tearDown() {
    writer.close();
  }

  @Test
  void shouldCreatePartialsTablesDayPartitionedOnTheWindowSlotWithTheFingerprintStamped() {
    // given
    final CompiledEntityMetrics compiled = declare(3);

    // when
    final Table metrics =
        writer.partialsTableOrCreate(compiled.metricsSchema(), compiled.fingerprint());

    // then
    assertThat(metrics.properties())
        .containsEntry(IcebergLakeWriter.FINGERPRINT_PROPERTY, compiled.fingerprint());
    assertThat(metrics.spec().fields()).hasSize(1);
    assertThat(metrics.spec().fields().get(0).transform().toString()).isEqualTo("day");
    assertThat(metrics.schema().findField("window_start").type())
        .isEqualTo(Types.TimestampType.withZone());
  }

  @Test
  void shouldLoadAnExistingPartialsTableUnderTheSameFingerprint() {
    // given
    final CompiledEntityMetrics compiled = declare(3);
    final Table created =
        writer.partialsTableOrCreate(compiled.metricsSchema(), compiled.fingerprint());

    // when
    final Table loaded =
        writer.partialsTableOrCreate(compiled.metricsSchema(), compiled.fingerprint());

    // then
    assertThat(loaded.name()).isEqualTo(created.name());
  }

  @Test
  void shouldRefuseToLoadAPartialsTableUnderAChangedDeclaration() {
    // given -- a table created at histogram scale 3, then a declaration bumped to scale 4
    final CompiledEntityMetrics original = declare(3);
    writer.partialsTableOrCreate(original.histSchema(), original.fingerprint());
    final CompiledEntityMetrics changed = declare(4);
    assertThat(changed.fingerprint()).isNotEqualTo(original.fingerprint());

    // when / then -- folding scale-4 bins into scale-3 rows would silently corrupt percentiles
    assertThatThrownBy(
            () -> writer.partialsTableOrCreate(changed.histSchema(), changed.fingerprint()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(original.fingerprint())
        .hasMessageContaining(changed.fingerprint())
        .hasMessageContaining("refusing");
  }

  private static CompiledEntityMetrics declare(final int histogramScale) {
    return EntityMetrics.declare("activities", RAW)
        .dims("process_id")
        .window(Duration.ofMinutes(1), "ended_at")
        .measure("duration_ms", Algebras.scalarStats(), Algebras.expHistogram(histogramScale))
        .build();
  }
}
