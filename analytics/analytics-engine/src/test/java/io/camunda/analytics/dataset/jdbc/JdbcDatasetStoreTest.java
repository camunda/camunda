/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.dataset.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.dataset.CompiledDataset;
import io.camunda.analytics.dataset.DatasetCompiler;
import io.camunda.analytics.dataset.DatasetDeclaration;
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterRegistry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

final class JdbcDatasetStoreTest {

  private static final long WINDOW = 60_000L;

  private final JdbcDataSource dataSource = h2();
  private final CompiledDataset dataset =
      new DatasetCompiler(
              MeterCatalog.withDefaults(), new MeterRegistry(new InMemoryMeterIdStore()))
          .compile(
              1L,
              DatasetDeclaration.builder("pi-duration", FactType.PROCESS_INSTANCE)
                  .dimension("var.region", DimensionType.STRING)
                  .dimension("processDefinitionKey", DimensionType.LONG)
                  .meter(Meter.of("duration", MeterCatalog.EXECUTION_TIME_SUMMARY, "durationMs"))
                  .meter(Meter.of("count", MeterCatalog.COUNT))
                  .window(WINDOW)
                  .build());
  private final JdbcDatasetStore store = new JdbcDatasetStore(dataSource);

  private static JdbcDataSource h2() {
    final JdbcDataSource ds = new JdbcDataSource();
    ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
    return ds;
  }

  @Test
  void shouldWriteMultipleMetersIntoOneCellAndOverwriteIdempotently() {
    // given a provisioned cube table
    store.ensure(dataset);
    final DimensionKey cell = DimensionKey.of(dataset.grain(), "EU", 100L);

    // when two meters are upserted for the same cell
    store.upsert(dataset, cell, 0L, WINDOW, "duration", new byte[] {1, 2, 3});
    store.upsert(dataset, cell, 0L, WINDOW, "count", new byte[] {9});

    // then the row carries both meters' accumulators
    final byte[][] row = readRow("EU", 100L, 0L);
    assertThat(row[0]).containsExactly(1, 2, 3); // duration
    assertThat(row[1]).containsExactly(9); // count

    // when the duration meter is re-written (a re-emit/replay)
    store.upsert(dataset, cell, 0L, WINDOW, "duration", new byte[] {7, 7});

    // then only its column changes; count is untouched (idempotent per-meter overwrite)
    final byte[][] updated = readRow("EU", 100L, 0L);
    assertThat(updated[0]).containsExactly(7, 7);
    assertThat(updated[1]).containsExactly(9);
  }

  @Test
  void shouldKeepTiersAndTheUnknownBucketAsSeparateRows() {
    // given
    store.ensure(dataset);
    final DimensionKey euCell = DimensionKey.of(dataset.grain(), "EU", 100L);
    final DimensionKey unknownRegion = DimensionKey.of(dataset.grain(), null, 100L);

    // when the same grain is written at two window starts, plus a null-region ("unknown") cell
    store.upsert(dataset, euCell, 0L, WINDOW, "count", new byte[] {1});
    store.upsert(dataset, euCell, WINDOW, WINDOW, "count", new byte[] {2});
    store.upsert(dataset, unknownRegion, 0L, WINDOW, "count", new byte[] {3});

    // then all three are distinct rows (two windows + the unknown-region bucket)
    assertThat(countRows()).isEqualTo(3);
    assertThat(readRow("EU", 100L, 0L)[1]).containsExactly(1);
    assertThat(readRow("EU", 100L, WINDOW)[1]).containsExactly(2);
  }

  private byte[][] readRow(final String region, final long defKey, final long windowStart) {
    final String sql =
        "SELECT duration, count FROM dataset_1 WHERE var_region = ? AND processDefinitionKey = ? "
            + "AND window_start = ?";
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, region);
      statement.setLong(2, defKey);
      statement.setLong(3, windowStart);
      try (ResultSet rs = statement.executeQuery()) {
        assertThat(rs.next()).isTrue();
        return new byte[][] {rs.getBytes(1), rs.getBytes(2)};
      }
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private int countRows() {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT COUNT(*) FROM dataset_1");
        ResultSet rs = statement.executeQuery()) {
      rs.next();
      return rs.getInt(1);
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
