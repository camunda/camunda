/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionContext;
import io.camunda.zeebe.db.impl.jdbc.JdbcTransactionParticipant;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.ibatis.session.ExecutorType;
import org.jspecify.annotations.Nullable;

/**
 * The engine state of a partition as the state classes see it during a transaction: the committed
 * rows with the transaction's staged writes applied. There is one session per {@link
 * JdbcTransactionContext}, shared by all state classes, so a commit writes all staged rows with a
 * single flush.
 *
 * <p>Like a RocksDB column family, every operation ensures an open transaction: it joins the
 * current one of the context, or else runs in a new one that commits when the operation is done.
 * {@link SQLException}s are translated like the ones of a commit.
 *
 * <p>Each row, identified by its {@link EntityType} and key, has at most one staged write; repeated
 * writes collapse:
 *
 * <ul>
 *   <li>insert, then update: one insert with the latest row (it isn't in the database yet)
 *   <li>insert, then delete: nothing is written
 *   <li>update, then delete: one delete
 *   <li>delete, then insert: one update. This assumes the deleted row exists in the database; if it
 *       doesn't, the flush fails like any update of a missing row.
 *   <li>insert of a staged insert/update, or update of a staged delete: {@link
 *       IllegalStateException}
 * </ul>
 *
 * <p>The session doesn't check the database on writes: an insert of an existing row or an update of
 * a missing one fails on flush. Committed rows are not cached; reads of unstaged keys always go to
 * the database. Not thread-safe, like the context it belongs to.
 *
 * <p>The flush sends all staged writes through one MyBatis batch session and a single {@code
 * flushStatements()}. MyBatis' batch executor reuses a JDBC statement while consecutive calls use
 * the same SQL, so the session writes one entity type after the other, each with all its deletes,
 * then all its inserts, then all its updates: one JDBC batch per statement instead of one per row.
 * Every key appears in at most one of them, so the order doesn't matter otherwise.
 */
final class EntitySession implements JdbcTransactionParticipant {

  private final JdbcTransactionContext transactionContext;
  private final int partitionId;
  private final Map<EntityType<?, ?>, Map<Object, Staged>> staged = new LinkedHashMap<>();

  private EntitySession(final JdbcTransactionContext transactionContext, final int partitionId) {
    this.transactionContext = transactionContext;
    this.partitionId = partitionId;
  }

  /** The session of the context, created and registered as participant on first use. */
  static EntitySession of(final JdbcTransactionContext transactionContext, final int partitionId) {
    final var session =
        transactionContext.participant(
            EntitySession.class, () -> new EntitySession(transactionContext, partitionId));
    if (session.partitionId != partitionId) {
      throw new IllegalArgumentException(
          "Expected the transaction context to belong to partition %d, but it belongs to %d"
              .formatted(partitionId, session.partitionId));
    }
    return session;
  }

  /** Returns the staged row, or null if its deletion is staged, or else the committed one. */
  <K, E> @Nullable E get(final EntityType<K, E> type, final K key) {
    return transactionContext.call(
        () -> {
          final var write = staged(type).get(key);
          if (write != null) {
            return type.cast(write.row);
          }
          return EngineStateMyBatis.call(
              transactionContext.connection(),
              ExecutorType.SIMPLE,
              session -> session.<E>selectOne(type.select, keyParameter(key)));
        });
  }

  <K, E> void insert(final EntityType<K, E> type, final E row) {
    transactionContext.call(
        () -> {
          final K key = type.keyOf(row);
          final var writes = staged(type);
          final var write = writes.get(key);
          if (write != null && write.operation != Operation.DELETE) {
            throw new IllegalStateException(
                "Expected to insert %s with key %s, but it already exists".formatted(type, key));
          }
          // a staged delete means the row exists in the database: re-inserting it overwrites it
          writes.put(key, new Staged(write == null ? Operation.INSERT : Operation.UPDATE, row));
          return null;
        });
  }

  <K, E> void update(final EntityType<K, E> type, final E row) {
    transactionContext.call(
        () -> {
          final K key = type.keyOf(row);
          final var writes = staged(type);
          final var write = writes.get(key);
          if (write != null && write.operation == Operation.DELETE) {
            throw new IllegalStateException(
                "Expected to update %s with key %s, but it was removed".formatted(type, key));
          }
          writes.put(key, new Staged(write == null ? Operation.UPDATE : write.operation, row));
          return null;
        });
  }

  /** Stages the deletion of the row with the given key; deleting a missing row is no error. */
  <K, E> void delete(final EntityType<K, E> type, final K key) {
    transactionContext.call(
        () -> {
          final var writes = staged(type);
          final var write = writes.get(key);
          if (write != null && write.operation == Operation.INSERT) {
            writes.remove(key);
          } else {
            writes.put(key, new Staged(Operation.DELETE, null));
          }
          return null;
        });
  }

  /**
   * Runs a query on the committed rows, for reads that bypass the staged writes: counts, existence
   * checks and the database part of queries that the caller merges with them through {@link #query}
   * or {@link #queryKeys}.
   */
  <M, T> T read(final Class<M> mapperType, final MapperCall<M, T> call) {
    return Objects.requireNonNull(
        transactionContext.call(
            () ->
                EngineStateMyBatis.call(
                    transactionContext.connection(),
                    ExecutorType.SIMPLE,
                    session -> call.apply(session.getMapper(mapperType)))));
  }

  /**
   * Merges the result of a database query with the staged writes, as if the staged writes were
   * already committed: a staged write of a key replaces the committed row, so it hides a row that
   * no longer matches or was deleted, and staged rows matching the query are added.
   *
   * @param dbResults the committed rows matching the query
   * @param stagedMatches the query's condition, applied to the staged rows
   * @param order the order of the result
   */
  <K, E> List<E> query(
      final EntityType<K, E> type,
      final Collection<? extends E> dbResults,
      final Predicate<? super E> stagedMatches,
      final Comparator<? super E> order) {
    final var writes = staged(type);
    final List<E> result = new ArrayList<>();
    for (final E row : dbResults) {
      if (!writes.containsKey(type.keyOf(row))) {
        result.add(row);
      }
    }
    result.addAll(stagedMatching(type, stagedMatches));
    result.sort(order);
    return result;
  }

  /** Like {@link #query}, for queries that only select the keys of the matching rows. */
  <K, E> List<K> queryKeys(
      final EntityType<K, E> type,
      final Collection<? extends K> dbKeys,
      final Predicate<? super E> stagedMatches,
      final Comparator<? super K> order) {
    final var writes = staged(type);
    final List<K> result = new ArrayList<>();
    for (final K key : dbKeys) {
      if (!writes.containsKey(key)) {
        result.add(key);
      }
    }
    for (final E row : stagedMatching(type, stagedMatches)) {
      result.add(type.keyOf(row));
    }
    result.sort(order);
    return result;
  }

  /** The staged (not deleted) rows of the type matching the given condition. */
  <K, E> List<E> stagedMatching(final EntityType<K, E> type, final Predicate<? super E> condition) {
    final List<E> result = new ArrayList<>();
    for (final var write : staged(type).values()) {
      if (write.row != null) {
        final E row = type.cast(write.row);
        if (condition.test(row)) {
          result.add(row);
        }
      }
    }
    return result;
  }

  /**
   * The keys of all staged writes of the type, including deletions. A query can exclude these keys
   * from the committed rows and add the {@link #stagedMatching staged matches} itself, e.g. to
   * count without loading the rows.
   */
  @SuppressWarnings("unchecked")
  <K, E> Set<K> stagedKeys(final EntityType<K, E> type) {
    return Collections.unmodifiableSet((Set<K>) staged(type).keySet());
  }

  /**
   * Writes all staged rows. Doesn't clear them, so a failed commit can write them again; {@link
   * #reset()} does.
   */
  @Override
  public void flush(final Connection connection) throws SQLException {
    if (staged.values().stream().allMatch(Map::isEmpty)) {
      return;
    }
    EngineStateMyBatis.call(
        connection,
        ExecutorType.BATCH,
        session -> {
          // the updates' parameters, to find their update counts among the batch results
          final Map<Object, Update> updates = new IdentityHashMap<>();
          for (final var entry : staged.entrySet()) {
            final var type = entry.getKey();
            for (final var write : entry.getValue().entrySet()) {
              if (write.getValue().operation == Operation.DELETE) {
                session.delete(type.delete, keyParameter(write.getKey()));
              }
            }
            for (final var write : entry.getValue().values()) {
              if (write.operation == Operation.INSERT) {
                session.insert(type.insert, rowParameter(write.row));
              }
            }
            for (final var write : entry.getValue().values()) {
              if (write.operation == Operation.UPDATE) {
                final var parameter = rowParameter(write.row);
                updates.put(parameter, new Update(type, Objects.requireNonNull(write.row)));
                session.update(type.update, parameter);
              }
            }
          }

          for (final var result : session.flushStatements()) {
            final var parameters = result.getParameterObjects();
            final var counts = result.getUpdateCounts();
            for (int i = 0; i < counts.length && i < parameters.size(); i++) {
              final var update = updates.get(parameters.get(i));
              if (update != null) {
                verifyUpdated(update.type, update.row, counts[i]);
              }
            }
          }
          return null;
        });
  }

  /** Drops all staged writes. Called after a successful commit and after a rollback. */
  @Override
  public void reset() {
    staged.values().forEach(Map::clear);
  }

  private Map<String, Object> keyParameter(final Object key) {
    return Map.of("partitionId", partitionId, "key", key);
  }

  private Map<String, Object> rowParameter(final @Nullable Object row) {
    return Map.of("partitionId", partitionId, "row", Objects.requireNonNull(row));
  }

  private Map<Object, Staged> staged(final EntityType<?, ?> type) {
    return staged.computeIfAbsent(type, ignored -> new LinkedHashMap<>());
  }

  /**
   * Checks the number of rows an update affected, as reported by the driver.
   *
   * @throws SQLException if the row doesn't exist or more than one row was updated
   */
  private static <K, E> void verifyUpdated(
      final EntityType<K, E> type, final Object row, final int updateCount) throws SQLException {
    final K key = type.keyOf(type.cast(row));
    if (updateCount == 0) {
      throw new SQLException(
          "Expected to update %s with key %s, but it does not exist".formatted(type, key));
    }
    if (updateCount != Statement.SUCCESS_NO_INFO && updateCount != 1) {
      throw new SQLException(
          "Expected to update one %s with key %s, but updated %d"
              .formatted(type, key, updateCount));
    }
  }

  @FunctionalInterface
  interface MapperCall<M, T> {
    T apply(M mapper);
  }

  private enum Operation {
    INSERT,
    UPDATE,
    DELETE
  }

  /** A staged write of a row; the row is null for a delete. */
  private record Staged(Operation operation, @Nullable Object row) {}

  private record Update(EntityType<?, ?> type, Object row) {}
}
