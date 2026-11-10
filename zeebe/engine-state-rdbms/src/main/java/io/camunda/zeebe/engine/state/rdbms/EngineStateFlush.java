/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.impl.jdbc.EntityCache;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionParticipant;
import io.camunda.zeebe.engine.state.rdbms.MyBatisEntityPersister.Writer;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.ExecutorType;

/**
 * Writes the staged rows of all caches of a transaction context on commit, through one MyBatis
 * batch session and a single {@code flushStatements()}.
 *
 * <p>MyBatis' batch executor reuses a JDBC statement while consecutive calls use the same SQL and
 * starts a new JDBC batch when it changes. So the caches are written one after the other, and each
 * emits all its deletes, then all its inserts, then all its updates: one JDBC batch per statement
 * instead of one per call.
 *
 * <p>The flush returns the batches' update counts. Updates are the only statements whose count
 * matters (an update of a missing row must fail the commit), so they are found among the results by
 * their parameter object, which is the very row instance that was staged.
 */
final class EngineStateFlush implements JdbcTransactionParticipant {

  /** The name of the {@code @Param} that carries the row of an insert or update. */
  private static final String ROW_PARAMETER = "row";

  private final List<Table<?, ?>> tables = new ArrayList<>();

  <K, E> void add(
      final EntityCache<K, E> cache,
      final int partitionId,
      final Writer<E> inserter,
      final Writer<E> updater,
      final Writer<K> deleter) {
    tables.add(new Table<>(cache, partitionId, inserter, updater, deleter));
  }

  @Override
  public void flush(final Connection connection) throws SQLException {
    if (tables.stream().noneMatch(table -> table.cache.hasStagedWrites())) {
      return;
    }

    try (final var session = EngineStateMyBatis.openSession(connection, ExecutorType.BATCH)) {
      final var mapper = session.getMapper(EngineStateMapper.class);
      final Map<Object, Table<?, ?>> updatedRows = new IdentityHashMap<>();
      for (final var table : tables) {
        table.write(mapper, updatedRows);
      }

      for (final var result : session.flushStatements()) {
        final var parameters = result.getParameterObjects();
        final var counts = result.getUpdateCounts();
        for (int i = 0; i < counts.length && i < parameters.size(); i++) {
          if (parameters.get(i) instanceof final Map<?, ?> parameter
              && parameter.containsKey(ROW_PARAMETER)) {
            final var row = parameter.get(ROW_PARAMETER);
            final var table = updatedRows.get(row);
            if (table != null) {
              table.verifyUpdated(row, counts[i]);
            }
          }
        }
      }
    } catch (final PersistenceException e) {
      throw EngineStateMyBatis.unwrap(e);
    }
  }

  @Override
  public void reset() {
    for (final var table : tables) {
      table.cache.reset();
    }
  }

  private record Table<K, E>(
      EntityCache<K, E> cache,
      int partitionId,
      Writer<E> inserter,
      Writer<E> updater,
      Writer<K> deleter) {

    void write(final EngineStateMapper mapper, final Map<Object, Table<?, ?>> updatedRows) {
      cache.writeStaged(
          new EntityCache.StagedWriter<>() {
            @Override
            public void delete(final K key) {
              deleter.write(mapper, partitionId, key);
            }

            @Override
            public void insert(final E entity) {
              inserter.write(mapper, partitionId, entity);
            }

            @Override
            public void update(final E entity) {
              updater.write(mapper, partitionId, entity);
              updatedRows.put(entity, Table.this);
            }
          });
    }

    @SuppressWarnings("unchecked")
    void verifyUpdated(final Object row, final int updateCount) throws SQLException {
      cache.verifyUpdated((E) row, updateCount);
    }
  }
}
