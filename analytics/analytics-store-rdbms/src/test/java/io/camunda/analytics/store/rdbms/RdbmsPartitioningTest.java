/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Layer C: the generated Postgres partitioning DDL. Drives the pure, dialect-aware DDL string
 * methods off a {@code POSTGRESQL} schema manager (no live database) so the partitioned-parent
 * shape, monthly child boundaries, conflict target, and retention drop are all assertable in a unit
 * test. The live-Postgres round trip remains a Testcontainers gate (ADR 0004 open question).
 */
final class RdbmsPartitioningTest {

  private static final long MINUTE = 60_000L;

  // 2026-07-15T12:00:00Z — a window_start inside July 2026 (UTC).
  private static final long JULY_WINDOW_START = 1_784_116_800_000L;
  // 2026-07-01T00:00:00Z and 2026-08-01T00:00:00Z, the monthly partition bounds.
  private static final long JULY_START = 1_782_864_000_000L;
  private static final long AUGUST_START = 1_785_542_400_000L;

  private final RdbmsDatasetSchemaManager postgres =
      new RdbmsDatasetSchemaManager(null, RdbmsDialect.POSTGRESQL);
  private final RdbmsDatasetSchemaManager h2 = new RdbmsDatasetSchemaManager(null, RdbmsDialect.H2);
  private final CompiledDataset cube = cube();

  private static CompiledDataset cube() {
    return new DatasetCompiler(
            MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()))
        .compile(
            1L,
            DatasetDeclaration.builder("pi-count", FactType.PROCESS_INSTANCE)
                .dimension("bpmnProcessId", DimensionType.STRING)
                .meter(Meter.of("count", MeterCatalog.COUNT))
                .window(MINUTE)
                .build());
  }

  @Test
  void shouldCreatePostgresParentAsPartitionedTableWithCompositePrimaryKey() {
    // when the parent DDL is generated for the partitioning dialect
    final String ddl = postgres.parentDdl(cube);

    // then it is a table partitioned by the time column with the partition key in a composite PK
    assertThat(ddl)
        .startsWith("CREATE TABLE IF NOT EXISTS dataset_1 (cell_key VARCHAR(4000), ")
        .contains("window_start BIGINT NOT NULL")
        .contains("PRIMARY KEY (cell_key, window_start)")
        .endsWith("PARTITION BY RANGE (window_start)")
        // the cell_key is NOT the sole primary key on a partitioned table
        .doesNotContain("cell_key VARCHAR(4000) PRIMARY KEY");
  }

  @Test
  void shouldKeepH2ParentAsPlainTableWithSingleColumnPrimaryKey() {
    // when the parent DDL is generated for H2 (no declarative partitioning)
    final String ddl = h2.parentDdl(cube);

    // then it is unchanged from Layer A/B: a plain table keyed on cell_key alone, no partitioning
    assertThat(ddl)
        .startsWith("CREATE TABLE IF NOT EXISTS dataset_1 (cell_key VARCHAR(4000) PRIMARY KEY, ")
        .doesNotContain("PARTITION BY")
        .doesNotContain("PRIMARY KEY (cell_key, window_start)");
  }

  @Test
  void shouldCreateMonthlyChildPartitionWithUtcEpochMsBoundaries() {
    // when the child partition DDL is generated for a window inside July 2026
    final String ddl = postgres.childPartitionDdl(cube, JULY_WINDOW_START);

    // then it is a PARTITION OF the parent named dataset_<id>_<yyyy>_<mm> over [monthStart, next)
    assertThat(ddl)
        .isEqualTo(
            "CREATE TABLE IF NOT EXISTS dataset_1_2026_07 PARTITION OF dataset_1 FOR VALUES FROM ("
                + JULY_START
                + ") TO ("
                + AUGUST_START
                + ")");
  }

  @Test
  void shouldDropASingleMonthlyPartitionByName() {
    // when the retention drop DDL is generated for July 2026
    final String ddl = postgres.dropPartitionDdl(cube, YearMonth.of(2026, 7));

    // then it is an instant metadata drop of that partition
    assertThat(ddl).isEqualTo("DROP TABLE IF EXISTS dataset_1_2026_07");
  }

  @Test
  void shouldDeriveTheUtcMonthFromAWindowStart() {
    // then a window_start maps to its UTC calendar month, driving the child name + boundaries
    assertThat(RdbmsDatasetSchemaManager.monthOf(JULY_WINDOW_START))
        .isEqualTo(YearMonth.of(2026, 7));
    assertThat(RdbmsDatasetSchemaManager.monthOf(JULY_START)).isEqualTo(YearMonth.of(2026, 7));
    assertThat(RdbmsDatasetSchemaManager.monthOf(AUGUST_START - 1))
        .isEqualTo(YearMonth.of(2026, 7));
    assertThat(RdbmsDatasetSchemaManager.monthOf(AUGUST_START)).isEqualTo(YearMonth.of(2026, 8));
  }

  @Test
  void shouldUseCompositeConflictTargetForAPartitionedCubeUpsert() {
    // given the composite conflict target the writer derives for a partitioned (Postgres) cube
    final String conflictTarget = "cell_key, window_start";

    // when the fenced upsert SQL is built for the partitioning dialect
    final String sql =
        RdbmsDatasetWriter.fencedUpsertSql(
            RdbmsDialect.POSTGRESQL,
            "dataset_1",
            List.of(
                "cell_key", "window_start", "window_size", "\"count_\"", "ver_epoch", "ver_offset"),
            List.of("VARCHAR", "BIGINT", "BIGINT", "BIGINT", "BIGINT", "BIGINT"),
            "cell_key",
            conflictTarget,
            List.of("\"count_\"", "ver_epoch", "ver_offset"));

    // then the ON CONFLICT clause targets the composite primary key, guarded by the write fence
    assertThat(sql)
        .contains(
            "ON CONFLICT (cell_key, window_start) DO UPDATE SET \"count_\" = EXCLUDED.\"count_\"")
        .contains("WHERE EXCLUDED.ver_epoch > dataset_1.ver_epoch");
  }

  @Test
  void shouldMatchOnSingleKeyColumnForH2Upsert() {
    // when the fenced upsert SQL is built for H2
    final String sql =
        RdbmsDatasetWriter.fencedUpsertSql(
            RdbmsDialect.H2,
            "dataset_1",
            List.of(
                "cell_key", "window_start", "window_size", "\"count_\"", "ver_epoch", "ver_offset"),
            List.of("VARCHAR", "BIGINT", "BIGINT", "BIGINT", "BIGINT", "BIGINT"),
            "cell_key",
            "cell_key",
            List.of("\"count_\"", "ver_epoch", "ver_offset"));

    // then it merges on cell_key alone, updating only when the write passes the version fence
    assertThat(sql)
        .startsWith("MERGE INTO dataset_1 USING")
        .contains("ON dataset_1.cell_key = src.cell_key")
        .contains("WHEN MATCHED AND (src.ver_epoch > dataset_1.ver_epoch")
        .contains("WHEN NOT MATCHED THEN INSERT");
  }

  @Test
  void shouldSupportPartitioningOnlyOnPostgres() {
    assertThat(RdbmsDialect.POSTGRESQL.supportsPartitioning()).isTrue();
    assertThat(RdbmsDialect.H2.supportsPartitioning()).isFalse();
  }
}
