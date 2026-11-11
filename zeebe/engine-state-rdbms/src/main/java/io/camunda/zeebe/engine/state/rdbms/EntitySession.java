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
import io.camunda.zeebe.engine.state.rdbms.PartitionStateMapper.PartitionStateRow;
import io.camunda.zeebe.protocol.Protocol;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jspecify.annotations.Nullable;

/**
 * The engine state of a partition as the state classes see it during a transaction: the committed
 * rows with the transaction's staged writes applied. There is one session per partition, created by
 * {@code RdbmsStateStores} and shared by all state classes of its {@link JdbcTransactionContext},
 * so a commit writes all staged rows with a single flush.
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
 *   <li>delete of a row the session knows is missing, e.g. from an earlier read: nothing is written
 *   <li>update, then delete: one delete
 *   <li>delete, then insert: one update. This assumes the deleted row exists in the database; if it
 *       doesn't, the flush fails like any update of a missing row.
 *   <li>insert of a staged insert/update, or update of a staged delete: {@link
 *       IllegalStateException}
 * </ul>
 *
 * <p>An {@link #upsert} becomes an insert or update if the session knows whether the row exists,
 * and keeps a staged insert or update; otherwise the flush deletes and inserts the row.
 *
 * <p>The session doesn't check the database on writes: an insert of an existing row or an update of
 * a missing one fails on flush. Reads of unstaged keys go to the database once per transaction, see
 * {@link #get}. Not thread-safe, like the context it belongs to.
 *
 * <p>Like the RDBMS exporter's flush, a transaction uses one MyBatis batch session, which owns the
 * database transaction: it is opened on the first database access, borrows a connection from the
 * pool, serves all reads and the flush, and is committed or rolled back and closed when the
 * transaction ends, returning the connection to the pool. A transaction that never accesses the
 * database borrows no connection. The pool sets the isolation level, read committed, once per
 * connection, not the session per transaction. When a commit attempt fails, the session is rolled
 * back and closed, and the retry opens a new one on a connection of the pool, which evicts broken
 * ones. The session's executor sends pending batched statements before a query, but statements are
 * only batched by the flush. Reusing prepared statements across transactions is left to the JDBC
 * driver.
 *
 * <p>The flush sends all staged writes through that session and a single {@code flushStatements()}.
 * MyBatis' batch executor reuses a JDBC statement while consecutive calls use the same SQL, so the
 * session writes one entity type after the other, each with all its deletes, then all its inserts,
 * then all its updates: one JDBC batch per statement instead of one per row. Every key appears in
 * at most one of them, so the order doesn't matter otherwise.
 */
final class EntitySession implements JdbcTransactionParticipant {

  /** The partition state row of the position {@link #markAsProcessed} writes. */
  static final String LAST_PROCESSED_POSITION_KEY = "last-processed-position";

  private static final Object ABSENT = new Object();

  private static final long NO_GENERATED_KEY = Long.MAX_VALUE;

  private final JdbcTransactionContext transactionContext;
  private final int partitionId;
  private final SqlSessionFactory sessionFactory;
  private final Map<EntityType<?, ?>, Map<Object, Staged>> staged = new LinkedHashMap<>();

  /**
   * The committed rows loaded in this transaction by {@link #get}, {@link #getAll}, {@link
   * #queryGroups} and full-row {@link #query}, per type and key; {@link #ABSENT} stands for a row
   * that doesn't exist. Rows are immutable and the partition has a single writer, so a loaded row
   * stays valid for the transaction; staged writes take precedence over it.
   */
  private final Map<EntityType<?, ?>, Map<Object, Object>> loaded = new HashMap<>();

  /**
   * The groups whose committed rows {@link #queryGroups} loaded in this transaction, per type, with
   * the keys of these rows: a key of such a group without a loaded row has no committed row.
   */
  private final Map<EntityType<?, ?>, Map<Object, List<Object>>> loadedGroups = new HashMap<>();

  /**
   * The first key the engine generated in this transaction, or {@link #NO_GENERATED_KEY}. Keys grow
   * monotonically, so the transaction created this key and all higher ones of the partition.
   */
  private long firstGeneratedKey = NO_GENERATED_KEY;

  /** The session of the current transaction, null until its first database access. */
  private @Nullable SqlSession session;

  private final Map<Class<?>, Object> mappers = new HashMap<>();

  /**
   * Doesn't register itself: the creator registers the session as participant of the context, once.
   */
  EntitySession(
      final JdbcTransactionContext transactionContext,
      final int partitionId,
      final SqlSessionFactory sessionFactory) {
    this.transactionContext = transactionContext;
    this.partitionId = partitionId;
    this.sessionFactory = sessionFactory;
  }

  int partitionId() {
    return partitionId;
  }

  /** Called with every key the engine generates, see {@link #isGeneratedInTransaction}. */
  void keyGenerated(final long key) {
    if (firstGeneratedKey == NO_GENERATED_KEY) {
      firstGeneratedKey = key;
    }
  }

  /**
   * Whether the engine generated the key in this transaction: then the database has no row that
   * belongs to it, as the transaction's rows aren't written before the commit. Such reads need no
   * query. Replay generates no keys, so it always reads the database.
   */
  boolean isGeneratedInTransaction(final long key) {
    return key >= firstGeneratedKey && Protocol.decodePartitionId(key) == partitionId;
  }

  /**
   * Returns the staged row, or null if its deletion is staged, or else the committed one. A
   * committed row is read once per transaction: the result, including a missing row, is remembered.
   */
  <K, E> @Nullable E get(final EntityType<K, E> type, final K key) {
    return transactionContext.call(
        () -> {
          final var write = staged(type).get(key);
          if (write != null) {
            return type.cast(write.row);
          }
          final var cached = loaded(type);
          final Object known = cached.get(key);
          if (known != null) {
            return known == ABSENT ? null : type.cast(known);
          }
          if (isKnownAbsent(type, key)) {
            return null;
          }
          final E row = unwrapped(() -> session().<E>selectOne(type.select, keyParameter(key)));
          cached.put(key, row == null ? ABSENT : row);
          return row;
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

  /**
   * Stages the row, inserting it or overwriting the existing one, without reading the database. See
   * the class documentation for how it is written.
   */
  <K, E> void upsert(final EntityType<K, E> type, final E row) {
    transactionContext.call(
        () -> {
          final K key = type.keyOf(row);
          final var writes = staged(type);
          final var write = writes.get(key);
          final Operation operation;
          if (write != null && write.operation != Operation.DELETE) {
            operation = write.operation;
          } else {
            // a staged delete doesn't tell whether the row exists in the database
            Object committed = loaded(type).get(key);
            if (committed == null && isKnownAbsent(type, key)) {
              committed = ABSENT;
            }
            if (committed == null) {
              operation = Operation.UPSERT;
            } else {
              operation = committed == ABSENT ? Operation.INSERT : Operation.UPDATE;
            }
          }
          writes.put(key, new Staged(operation, row));
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

  /**
   * Stages the deletion of the row with the given key; deleting a missing row is no error. If the
   * session knows that the row doesn't exist, e.g. from an earlier read, nothing is staged.
   */
  <K, E> void delete(final EntityType<K, E> type, final K key) {
    transactionContext.call(
        () -> {
          final var writes = staged(type);
          final var write = writes.get(key);
          if (write != null && write.operation == Operation.INSERT) {
            writes.remove(key);
          } else if (write == null && isCommittedAbsent(type, key)) {
            return null;
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
        transactionContext.call(() -> unwrapped(() -> call.apply(mapper(mapperType)))));
  }

  /**
   * Runs several operations in one transaction: the current one, or else a new one that commits
   * when the operation is done. Outside of a transaction, e.g. on the context of the scheduled
   * tasks, the operations then share the loaded rows and one connection, instead of each running in
   * its own transaction.
   */
  <T> @Nullable T inTransaction(final JdbcTransactionContext.JdbcOperation<T> operation) {
    return transactionContext.call(operation);
  }

  /**
   * Like {@link #get} for several keys: the rows of the keys that the session doesn't know yet are
   * read with one query, and remembered like {@link #get} does, including the missing ones.
   *
   * @param query selects the committed rows of the given keys; it may return rows of other keys
   * @return the current rows of the keys, in their order; null for a key without row
   */
  <K, E, M> List<@Nullable E> getAll(
      final EntityType<K, E> type,
      final List<K> keys,
      final Class<M> mapperType,
      final BatchQuery<M, K, E> query) {
    return Objects.requireNonNull(
        transactionContext.call(
            () -> {
              final List<K> unknown = new ArrayList<>();
              for (final K key : keys) {
                if (!isKnown(type, key)) {
                  unknown.add(key);
                }
              }
              if (!unknown.isEmpty()) {
                final var rows = unwrapped(() -> query.select(mapper(mapperType), unknown));
                final var cached = loaded(type);
                for (final E row : rows) {
                  cached.putIfAbsent(type.keyOf(row), row);
                }
                for (final K key : unknown) {
                  cached.putIfAbsent(key, ABSENT);
                }
              }
              final List<@Nullable E> result = new ArrayList<>(keys.size());
              for (final K key : keys) {
                result.add(knownRow(type, key));
              }
              return result;
            }));
  }

  /**
   * The current rows of the given groups of a type that {@link EntityType groups its rows}: the
   * committed rows merged with the staged writes. The committed rows of the groups that weren't
   * loaded in this transaction yet are read with one query; the groups are then remembered as
   * loaded, so later reads of their keys need no query, also for keys without row.
   *
   * @param query selects all committed rows of the given groups
   * @return the rows, in no particular order
   */
  <K, E, G, M> List<E> queryGroups(
      final EntityType<K, E> type,
      final Collection<G> groups,
      final Class<M> mapperType,
      final BatchQuery<M, G, E> query) {
    return Objects.requireNonNull(
        transactionContext.call(
            () -> {
              final var keysByGroup = loadedGroups(type);
              final var cached = loaded(type);
              final Set<Object> requested = new LinkedHashSet<>(groups);
              final List<G> unknown = new ArrayList<>();
              for (final G group : groups) {
                if (keysByGroup.containsKey(group) || unknown.contains(group)) {
                  continue;
                }
                if (isNewGroup(group)) {
                  keysByGroup.put(group, new ArrayList<>());
                } else {
                  unknown.add(group);
                }
              }
              if (!unknown.isEmpty()) {
                final var rows = unwrapped(() -> query.select(mapper(mapperType), unknown));
                unknown.forEach(group -> keysByGroup.put(group, new ArrayList<>()));
                for (final E row : rows) {
                  final K key = type.keyOf(row);
                  final Object group = type.groupOf(key);
                  final var keysOfGroup = unknown.contains(group) ? keysByGroup.get(group) : null;
                  if (keysOfGroup != null && cached.putIfAbsent(key, row) != ABSENT) {
                    keysOfGroup.add(key);
                  }
                }
              }

              final var writes = staged(type);
              final List<E> result = new ArrayList<>();
              for (final Object group : requested) {
                for (final Object key : keysByGroup.get(group)) {
                  if (!writes.containsKey(key)) {
                    result.add(type.cast(cached.get(key)));
                  }
                }
              }
              for (final var write : writes.entrySet()) {
                final var row = write.getValue().row;
                if (row != null && requested.contains(type.groupOf(type.castKey(write.getKey())))) {
                  result.add(type.cast(row));
                }
              }
              return result;
            }));
  }

  /**
   * The keys of the current rows of a group, like {@link #queryGroups} but without loading the
   * rows: the committed keys are those of the loaded group, or else the ones the query selects.
   */
  <K, E, G, M> List<K> queryGroupKeys(
      final EntityType<K, E> type,
      final G group,
      final Class<M> mapperType,
      final MapperCall<M, Collection<K>> query) {
    return Objects.requireNonNull(
        transactionContext.call(
            () -> {
              final var loadedKeys = loadedGroups(type).get(group);
              final Collection<?> committed;
              if (loadedKeys != null) {
                committed = loadedKeys;
              } else if (isNewGroup(group)) {
                committed = List.of();
              } else {
                committed = unwrapped(() -> query.apply(mapper(mapperType)));
              }
              final var writes = staged(type);
              final List<K> result = new ArrayList<>();
              for (final Object key : committed) {
                if (!writes.containsKey(key)) {
                  result.add(type.castKey(key));
                }
              }
              for (final var write : writes.entrySet()) {
                final K key = type.castKey(write.getKey());
                if (write.getValue().row != null && group.equals(type.groupOf(key))) {
                  result.add(key);
                }
              }
              return result;
            }));
  }

  /** The mapper of the current transaction's session; keep it within the transaction. */
  private <M> M mapper(final Class<M> type) {
    final var existing = mappers.get(type);
    if (existing != null) {
      return type.cast(existing);
    }
    final M created = session().getMapper(type);
    mappers.put(type, created);
    return created;
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
    final var cached = loaded(type);
    final List<E> result = new ArrayList<>();
    for (final E row : dbResults) {
      final K key = type.keyOf(row);
      if (!writes.containsKey(key)) {
        result.add(row);
        cached.putIfAbsent(key, row);
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
   * Stages the update of the partition's last processed position. The row exists, as {@link
   * #readLastProcessedPosition} creates it before the stream processor starts, so a commit sends a
   * single update.
   */
  @Override
  public void markAsProcessed(final long position) {
    update(
        RdbmsProcessStore.PARTITION_STATE,
        new PartitionStateRow(LAST_PROCESSED_POSITION_KEY, Long.toString(position)));
  }

  /**
   * Reads the position of the last command whose changes the database contains for the partition,
   * or {@link JdbcTransactionContext#NO_POSITION} if there is none. Creates the position's row if
   * it doesn't exist yet, so that {@link #markAsProcessed} only needs to update it.
   */
  static long readLastProcessedPosition(
      final SqlSessionFactory sessionFactory, final int partitionId) {
    try (final var session = sessionFactory.openSession()) {
      final PartitionStateRow row =
          session.selectOne(
              RdbmsProcessStore.PARTITION_STATE.select,
              Map.of("partitionId", partitionId, "key", LAST_PROCESSED_POSITION_KEY));
      if (row != null) {
        return Long.parseLong(Objects.requireNonNull(row.value()));
      }
      session.insert(
          RdbmsProcessStore.PARTITION_STATE.insert,
          Map.of(
              "partitionId",
              partitionId,
              "row",
              new PartitionStateRow(
                  LAST_PROCESSED_POSITION_KEY, Long.toString(JdbcTransactionContext.NO_POSITION))));
      session.commit();
      return JdbcTransactionContext.NO_POSITION;
    }
  }

  /**
   * Writes all staged rows and commits the session's transaction; a read-only transaction just
   * commits. Doesn't clear the staged rows, so a failed commit can write them again; {@link
   * #reset()} does.
   */
  @Override
  public void commit() throws SQLException {
    final boolean hasWrites = staged.values().stream().anyMatch(writes -> !writes.isEmpty());
    if (session == null && !hasWrites) {
      return;
    }
    unwrapped(
        () -> {
          final var current = session();
          if (hasWrites) {
            flush(current);
          }
          current.commit(true);
          return null;
        });
    closeSession();
  }

  private void flush(final SqlSession batch) throws SQLException {
    // the updates' parameters, to find their update counts among the batch results
    final Map<Object, Update> updates = new IdentityHashMap<>();
    for (final var entry : staged.entrySet()) {
      final var type = entry.getKey();
      for (final var write : entry.getValue().entrySet()) {
        final var operation = write.getValue().operation;
        if (operation == Operation.DELETE || operation == Operation.UPSERT) {
          batch.delete(type.delete, keyParameter(write.getKey()));
        }
      }
      for (final var write : entry.getValue().values()) {
        if (write.operation == Operation.INSERT || write.operation == Operation.UPSERT) {
          batch.insert(type.insert, rowParameter(write.row));
        }
      }
      for (final var write : entry.getValue().values()) {
        if (write.operation == Operation.UPDATE) {
          final var parameter = rowParameter(write.row);
          updates.put(parameter, new Update(type, Objects.requireNonNull(write.row)));
          batch.update(type.update, parameter);
        }
      }
    }

    for (final var result : batch.flushStatements()) {
      final var parameters = result.getParameterObjects();
      final var counts = result.getUpdateCounts();
      for (int i = 0; i < counts.length && i < parameters.size(); i++) {
        final var update = updates.get(parameters.get(i));
        if (update != null) {
          verifyUpdated(update.type, update.row, counts[i]);
        }
      }
    }
  }

  /** Rolls back the transaction's session, if there is one, and returns its connection. */
  @Override
  public void rollback() {
    final var current = session;
    if (current != null) {
      try {
        current.rollback(true);
      } catch (final RuntimeException ignored) {
        // a broken connection lost its transaction anyway
      }
    }
    closeSession();
  }

  /** Drops all staged writes and loaded rows and ends the transaction's session. */
  @Override
  public void reset() {
    firstGeneratedKey = NO_GENERATED_KEY;
    staged.values().forEach(Map::clear);
    clearLoaded();
    closeSession();
  }

  /**
   * Keeps the staged writes for the retry, which gets a new session on a new connection. The loaded
   * rows are dropped, the retry reads them again.
   */
  @Override
  public void commitFailed() {
    rollback();
    clearLoaded();
  }

  private void clearLoaded() {
    loaded.clear();
    loadedGroups.clear();
  }

  /** Whether the session knows the current row of the key, or that there is none. */
  private <K, E> boolean isKnown(final EntityType<K, E> type, final K key) {
    return staged(type).containsKey(key)
        || loaded(type).containsKey(key)
        || isKnownAbsent(type, key);
  }

  /** The current row of a {@link #isKnown known} key, or null if there is none. */
  private <K, E> @Nullable E knownRow(final EntityType<K, E> type, final K key) {
    final var write = staged(type).get(key);
    if (write != null) {
      return write.row == null ? null : type.cast(write.row);
    }
    final Object committed = loaded(type).get(key);
    return committed == null || committed == ABSENT ? null : type.cast(committed);
  }

  /** Whether the session knows that the database has no row of the key. */
  private <K, E> boolean isCommittedAbsent(final EntityType<K, E> type, final K key) {
    final Object committed = loaded(type).get(key);
    return committed != null ? committed == ABSENT : isKnownAbsent(type, key);
  }

  /**
   * Whether the database has no row of a key that wasn't loaded itself: its group was loaded
   * without it, or the key belongs to an engine key generated in this transaction.
   */
  private <K, E> boolean isKnownAbsent(final EntityType<K, E> type, final K key) {
    final Object group = type.groupOf(key);
    return (group != null && loadedGroups(type).containsKey(group))
        || isGeneratedInTransaction(type.engineKeyOf(key));
  }

  /**
   * Whether the database has no row of the group, as it is an engine key new in this transaction.
   */
  private boolean isNewGroup(final Object group) {
    return group instanceof final Long engineKey && isGeneratedInTransaction(engineKey);
  }

  private SqlSession session() {
    if (session == null) {
      session = sessionFactory.openSession(ExecutorType.BATCH);
    }
    return session;
  }

  /** The connection may be dead, so errors on closing are of no interest. */
  private void closeSession() {
    mappers.clear();
    final var closing = session;
    session = null;
    if (closing != null) {
      try {
        closing.close();
      } catch (final RuntimeException ignored) {
        // nothing to release on a dead connection
      }
    }
  }

  /**
   * MyBatis wraps every {@link SQLException} in a runtime exception. The JDBC transaction tells
   * retryable errors apart only by the SQL exception, so it must get that one.
   */
  private static <T> T unwrapped(final SessionCall<T> call) throws SQLException {
    try {
      return call.run();
    } catch (final PersistenceException e) {
      for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
        if (cause instanceof final SQLException sqlException) {
          throw sqlException;
        }
      }
      throw e;
    }
  }

  private Map<String, Object> keyParameter(final Object key) {
    return Map.of("partitionId", partitionId, "key", key);
  }

  private Map<String, Object> rowParameter(final @Nullable Object row) {
    return Map.of("partitionId", partitionId, "row", Objects.requireNonNull(row));
  }

  private Map<Object, Object> loaded(final EntityType<?, ?> type) {
    return loaded.computeIfAbsent(type, ignored -> new HashMap<>());
  }

  private Map<Object, List<Object>> loadedGroups(final EntityType<?, ?> type) {
    return loadedGroups.computeIfAbsent(type, ignored -> new HashMap<>());
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
  private interface SessionCall<T> {
    T run() throws SQLException;
  }

  @FunctionalInterface
  interface MapperCall<M, T> {
    T apply(M mapper);
  }

  /** A query for the rows of several keys or groups. */
  @FunctionalInterface
  interface BatchQuery<M, P, E> {
    Collection<E> select(M mapper, List<P> parameters);
  }

  private enum Operation {
    INSERT,
    UPDATE,
    DELETE,
    /** Insert or overwrite: delete and insert, as it isn't known whether the row exists. */
    UPSERT
  }

  /** A staged write of a row; the row is null for a delete. */
  private record Staged(Operation operation, @Nullable Object row) {}

  private record Update(EntityType<?, ?> type, Object row) {}
}
