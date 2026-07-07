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
import io.camunda.analytics.dataset.CompiledMeter;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.BoundMeter;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the serving row a cube upsert produces (ADR 0008, invariant 1, "the serving rows are
 * byte-identical"): the physical column identifiers and the values bound into them, selected back
 * over raw JDBC so no read-path mapping can mask a write-path change. The byte-backed {@code
 * DimensionKey} refactor changes how the writer obtains the dimension values (lazy per-column
 * decode instead of materialized objects) but must leave this row exactly as it is.
 */
final class RdbmsServingRowShapeTest {

  private static final long MINUTE = 60_000L;

  private final JdbcDataSource dataSource = h2();
  private final RdbmsDatasetStore store = new RdbmsDatasetStore(dataSource);
  private final CompiledDataset dataset =
      new DatasetCompiler(
              MeterCatalog.withDefaults(), new MeterIdRegistry(new InMemoryMeterIdStore()))
          .compile(
              1L,
              DatasetDeclaration.builder("pi-count", FactType.PROCESS_INSTANCE)
                  .dimension("bpmnProcessId", DimensionType.STRING)
                  .dimension("version", DimensionType.INT)
                  .meter(Meter.of("count", MeterCatalog.COUNT))
                  .window(MINUTE)
                  .build());

  private static JdbcDataSource h2() {
    final JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    return ds;
  }

  @AfterEach
  void tearDown() {
    store.close();
  }

  @Test
  void shouldWriteTheFrozenRowShapeForACubeCell() throws Exception {
    // given a provisioned cube and one written cell (dimensions incl. a null bucket next to it)
    store.schemaManager().ensure(dataset);
    final DimensionKey key = DimensionKey.of(dataset.grain(), "orders", 3);
    final DimensionKey nullKey = DimensionKey.of(dataset.grain(), null, null);
    store.writer().upsertCell(dataset, key, 0L, MINUTE, "count", count(5L));
    store.writer().upsertCell(dataset, nullKey, 0L, MINUTE, "count", count(2L));
    store.writer().flush();

    // when the rows are read back over raw JDBC by their FROZEN physical identifiers: the table
    // is dataset_<cubeId>, dims are "<name>_" (declared name + underscore), the additive COUNT
    // meter is its single native column "count_", plus cell_key / window_start / window_size
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement statement =
            connection.prepareStatement(
                "SELECT cell_key, \"bpmnProcessId_\", \"version_\", window_start, window_size, "
                    + "\"count_\" FROM dataset_1 ORDER BY cell_key");
        final ResultSet rows = statement.executeQuery()) {

      // then — the null-bucket row: dims are SQL NULL, but the cell_key renders them as " "
      assertThat(rows.next()).isTrue();
      assertThat(rows.getString(1)).isEqualTo(" \u0001 \u0001|0|60000");
      assertThat(rows.getString(2)).isNull();
      rows.getInt(3);
      assertThat(rows.wasNull()).isTrue();
      assertThat(rows.getLong(4)).isEqualTo(0L);
      assertThat(rows.getLong(5)).isEqualTo(MINUTE);
      assertThat(rows.getLong(6)).isEqualTo(2L);

      // then — the value row: dimension values bound typed, cell_key rendered from the same key
      assertThat(rows.next()).isTrue();
      assertThat(rows.getString(1)).isEqualTo("orders\u00013\u0001|0|60000");
      assertThat(rows.getString(1)).isEqualTo(RdbmsNames.cellKey(key, 0L, MINUTE));
      assertThat(rows.getString(2)).isEqualTo("orders");
      assertThat(rows.getInt(3)).isEqualTo(3);
      assertThat(rows.getLong(4)).isEqualTo(0L);
      assertThat(rows.getLong(5)).isEqualTo(MINUTE);
      assertThat(rows.getLong(6)).isEqualTo(5L);

      assertThat(rows.next()).isFalse();
    }
  }

  @Test
  void shouldOverwriteNotForkWhenTheSameCellIsRewritten() throws Exception {
    // given the same logical cell written twice (the replay/idempotence contract cell_key exists
    // for)
    store.schemaManager().ensure(dataset);
    final DimensionKey key = DimensionKey.of(dataset.grain(), "orders", 3);
    store.writer().upsertCell(dataset, key, 0L, MINUTE, "count", count(5L));
    store.writer().upsertCell(dataset, key, 0L, MINUTE, "count", count(7L));
    store.writer().flush();

    // when counted over raw JDBC
    try (final Connection connection = dataSource.getConnection();
        final PreparedStatement statement =
            connection.prepareStatement("SELECT COUNT(*), MAX(\"count_\") FROM dataset_1");
        final ResultSet rows = statement.executeQuery()) {

      // then exactly one row remains, holding the later value
      assertThat(rows.next()).isTrue();
      assertThat(rows.getLong(1)).isEqualTo(1L);
      assertThat(rows.getLong(2)).isEqualTo(7L);
    }
  }

  @SuppressWarnings("unchecked")
  private byte[] count(final long value) {
    final CompiledMeter meter = dataset.meters().get(0);
    return ((BoundMeter<Object, Object>) meter.bound()).accumulatorCodec().toBytes(value);
  }
}
