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
import io.camunda.analytics.dimension.DimensionKey;
import io.camunda.analytics.dimension.DimensionType;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.meter.CompositeAccumulatorValue;
import io.camunda.analytics.meter.InMemoryMeterIdStore;
import io.camunda.analytics.meter.Meter;
import io.camunda.analytics.meter.MeterCatalog;
import io.camunda.analytics.meter.MeterIdRegistry;
import io.camunda.analytics.serving.spi.WriteVersion;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The write-fence contract every serving backend must honor: an upsert is applied only when its
 * {@link WriteVersion} is at-or-above the row's stored version — a higher epoch always wins (a
 * later owner supersedes a fenced zombie regardless of offsets), an equal version overwrites (a
 * deterministic replay), and a stale write leaves the row untouched and is counted, not errored.
 */
final class RdbmsWriteFenceTest {

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
  void shouldFenceAStaleWriteAndAcceptEqualAndNewerOnes() {
    // given a cell written by the current owner at (epoch 5, offset 100)
    store.schemaManager().ensure(dataset);
    final RdbmsDatasetWriter writer = (RdbmsDatasetWriter) store.writer();
    writer.upsertCell(dataset, key(), 0L, MINUTE, count(10L), new WriteVersion(5, 100));
    writer.flush();

    // when a fenced zombie writes a stale total — older epoch, even at a higher offset
    // (offsets are not comparable across owners; the epoch decides)
    writer.upsertCell(dataset, key(), 0L, MINUTE, count(3L), new WriteVersion(4, 999));
    writer.flush();

    // then the row is untouched and the rejection was counted, not errored
    assertThat(servedCount()).isEqualTo(10L);
    assertThat(writer.fencedWrites()).isEqualTo(1);

    // when a deterministic replay re-writes the identical cut (equal version)
    writer.upsertCell(dataset, key(), 0L, MINUTE, count(10L), new WriteVersion(5, 100));
    writer.flush();

    // then it applied idempotently (no new fenced write)
    assertThat(servedCount()).isEqualTo(10L);
    assertThat(writer.fencedWrites()).isEqualTo(1);

    // when the same owner's next cut writes a newer total
    writer.upsertCell(dataset, key(), 0L, MINUTE, count(12L), new WriteVersion(5, 200));
    writer.flush();

    // then it superseded the old value
    assertThat(servedCount()).isEqualTo(12L);

    // and a later owner (higher epoch, lower offset — a fresh recovery) supersedes again
    writer.upsertCell(dataset, key(), 0L, MINUTE, count(11L), new WriteVersion(6, 50));
    writer.flush();
    assertThat(servedCount()).isEqualTo(11L);
    assertThat(writer.fencedWrites()).isEqualTo(1);
  }

  private DimensionKey key() {
    return DimensionKey.of(dataset.grain(), "orders");
  }

  private byte[] count(final long value) {
    return new CompositeAccumulatorValue(dataset.meterBounds()).toBytes(new Object[] {value});
  }

  private long servedCount() {
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT \"count_\" FROM dataset_1");
        ResultSet rs = statement.executeQuery()) {
      assertThat(rs.next()).isTrue();
      final long count = rs.getLong(1);
      assertThat(rs.next()).isFalse();
      return count;
    } catch (final Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
