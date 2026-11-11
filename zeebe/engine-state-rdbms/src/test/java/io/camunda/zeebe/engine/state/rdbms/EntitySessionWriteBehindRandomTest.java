/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.db.writebehind.WriteBehindConfiguration;
import io.camunda.zeebe.db.writebehind.WriteBehindStore;
import io.camunda.zeebe.engine.state.rdbms.PartitionStateMapper.PartitionStateRow;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Applies random writes, commits, rollbacks and reads to a write-behind session that flushes all
 * the time, and compares every read with a model of the committed state. The reads come from the
 * database, the store and the unflushed rows, so a flush that happens between two operations
 * changes where a row comes from but never what the session returns.
 */
final class EntitySessionWriteBehindRandomTest {

  private static final EntityType<String, PartitionStateRow> ROW =
      new EntityType<>(
          PartitionStateMapper.class, "PartitionState", "random row", PartitionStateRow::key);

  private static final Comparator<PartitionStateRow> BY_KEY =
      Comparator.comparing(PartitionStateRow::key);
  private static final int KEYS = 12;

  private static EngineStateDatabase database;

  @BeforeAll
  static void startDatabase() throws Exception {
    database = EngineStateDatabase.start();
  }

  @AfterAll
  static void stopDatabase() {
    database.close();
  }

  @Test
  void shouldReturnTheCommittedStateWhileFlushing() throws Exception {
    for (long seed = 1; seed <= 6; seed++) {
      run(seed, Duration.ZERO);
    }
  }

  @Test
  void shouldReturnTheCommittedStateWhileFlushingWithADelay() throws Exception {
    for (long seed = 11; seed <= 14; seed++) {
      run(seed, Duration.ofMillis(5));
    }
  }

  private void run(final long seed, final Duration flushDelay) throws Exception {
    final var random = new Random(seed);
    final int partitionId = database.nextPartitionId();
    final var store =
        new WriteBehindStore<>(
            new JdbcBackend(partitionId, database.sessionFactory()),
            WriteBehindConfiguration.defaults()
                .withKeyIndexColumnFamilies(Set.of())
                .withFlushDelay(flushDelay));
    final var context = new JdbcTransactionContext();
    final var session =
        new EntitySession(context, partitionId, database.sessionFactory(), store, null);
    context.register(session);
    // the committed state, and the state of the transaction that is open
    final var committed = new TreeMap<String, PartitionStateRow>();
    var visible = new TreeMap<String, PartitionStateRow>();
    var transaction = context.getCurrentTransaction();
    try {
      for (int step = 0; step < 400; step++) {
        final String key = "k" + (char) ('a' + random.nextInt(KEYS));
        final String description = "seed %d step %d".formatted(seed, step);
        switch (random.nextInt(8)) {
          case 0, 1 -> {
            final var row = new PartitionStateRow(key, "v" + step);
            if (visible.containsKey(key)) {
              session.update(ROW, row);
            } else {
              session.insert(ROW, row);
            }
            visible.put(key, row);
          }
          case 2 -> {
            session.delete(ROW, key);
            visible.remove(key);
          }
          case 3 -> {
            transaction.commit();
            committed.clear();
            committed.putAll(visible);
            transaction = context.getCurrentTransaction();
          }
          case 4 -> {
            transaction.rollback();
            visible = new TreeMap<>(committed);
            transaction = context.getCurrentTransaction();
          }
          case 5 -> {
            assertThat(session.get(ROW, key)).describedAs(description).isEqualTo(visible.get(key));
          }
          default -> {
            // the session's first read takes the snapshot of the unflushed rows, before the SQL
            session.read(PartitionStateMapper.class, mapper -> 0);
            final var rows =
                session.query(ROW, committedInDatabase(partitionId), r -> true, BY_KEY);
            assertThat(rows).describedAs(description).containsExactlyElementsOf(visible.values());
          }
        }
      }
    } finally {
      context.getCurrentTransaction().rollback();
      store.close();
    }
    // after a flush, the database holds the committed state
    assertThat(committedInDatabase(partitionId))
        .describedAs("seed %d: rows after the final flush", seed)
        .containsExactlyElementsOf(committed.values());
  }

  private List<PartitionStateRow> committedInDatabase(final int partitionId) throws SQLException {
    final List<PartitionStateRow> rows = new ArrayList<>();
    try (final var connection = database.dataSource().getConnection();
        final var statement =
            connection.prepareStatement(
                "SELECT state_key, value FROM CAM_RU_PARTITION_STATE"
                    + " WHERE partition_id = ? ORDER BY state_key")) {
      statement.setInt(1, partitionId);
      try (final var result = statement.executeQuery()) {
        while (result.next()) {
          rows.add(new PartitionStateRow(result.getString(1), result.getString(2)));
        }
      }
    }
    return rows;
  }
}
