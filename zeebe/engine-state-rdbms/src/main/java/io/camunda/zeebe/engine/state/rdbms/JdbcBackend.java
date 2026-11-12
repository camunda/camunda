/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.writebehind.Backend;
import io.camunda.zeebe.db.writebehind.KeyRange;
import io.camunda.zeebe.engine.state.rdbms.VariableMapper.VariableKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.jspecify.annotations.Nullable;

/**
 * The write-behind {@link Backend} of the relational engine state of one partition: the keys are
 * {@link EntityKey}s and the values are the immutable rows of the {@link EntityType}s. The group of
 * a key is its type.
 *
 * <p>Only point reads and the batch are supported. Queries over many rows go to the database
 * directly, after the store flushed, see {@link EntitySession}.
 *
 * <p>A read and a flush each use their own connection of the pool, and the flush writes all rows of
 * its batch in one database transaction, so the last processed position is never flushed without
 * the rows that belong to it.
 */
final class JdbcBackend implements Backend<EntityKey, Object> {

  /** Rows are never decoded from bytes, so the store's prototype argument has no meaning here. */
  static final Object NO_PROTOTYPE = new Object();

  private static final Comparator<EntityKey> BY_TYPE =
      Comparator.comparingInt(key -> key.type().id);
  private static final int ENTRY_OVERHEAD = 64;
  private static final int ESTIMATED_ROW_BYTES = 256;

  private final int partitionId;
  private final SqlSessionFactory sessionFactory;

  private final VariableKeyFilter variableKeys;

  JdbcBackend(final int partitionId, final SqlSessionFactory sessionFactory) {
    this.partitionId = partitionId;
    this.sessionFactory = sessionFactory;
    variableKeys = new VariableKeyFilter(highestVariableScope(sessionFactory, partitionId));
  }

  private static long highestVariableScope(
      final SqlSessionFactory sessionFactory, final int partitionId) {
    try (final var session = sessionFactory.openSession(ExecutorType.SIMPLE)) {
      final Long highest =
          session.selectOne(
              VariableMapper.class.getName() + ".selectHighestVariableScopeKey",
              Map.of("partitionId", partitionId));
      return highest == null ? -1L : highest;
    } catch (final PersistenceException e) {
      throw new ZeebeDbException("Failed to read the highest variable scope", e);
    }
  }

  @Override
  public long groupOf(final EntityKey key) {
    return key.type().id;
  }

  @Override
  public KeyRange<EntityKey> groupRange(final long group) {
    throw new UnsupportedOperationException("The relational state is not scanned by key");
  }

  @Override
  public EntityKey groupLastSeekKey(final long group) {
    throw new UnsupportedOperationException("The relational state is not scanned by key");
  }

  @Override
  public @Nullable Object get(final EntityKey key, final Object prototype) {
    try (final var session = sessionFactory.openSession(ExecutorType.SIMPLE)) {
      return session.selectOne(key.type().select, keyParameter(key.key()));
    } catch (final PersistenceException e) {
      throw new ZeebeDbException("Failed to read %s %s".formatted(key.type(), key.key()), e);
    }
  }

  @Override
  public boolean exists(final EntityKey key) {
    return get(key, NO_PROTOTYPE) != null;
  }

  @Override
  public boolean mayExist(final EntityKey key) {
    return !knownAbsent(key);
  }

  /**
   * Variables are looked up before they are written, e.g. to merge a document into a scope, and
   * almost every one of those lookups finds nothing, which the database would only confirm.
   */
  @Override
  public boolean knownAbsent(final EntityKey key) {
    return key.type() == RdbmsVariableStore.VARIABLE
        && variableKeys.neverWritten((VariableKey) key.key());
  }

  @Override
  public Backend.Batch<EntityKey, Object> newBatch() {
    return new Batch();
  }

  @Override
  public Backend.Cursor<EntityKey, Object> newCursor() {
    throw new UnsupportedOperationException("The relational state is not scanned by key");
  }

  @Override
  public void copy(final Object source, final Object target) {
    throw new UnsupportedOperationException("Rows are immutable and never copied");
  }

  @Override
  public int weigh(final EntityKey key, final @Nullable Object value) {
    return ENTRY_OVERHEAD + (value == null ? 0 : ESTIMATED_ROW_BYTES);
  }

  private Map<String, Object> keyParameter(final Object key) {
    return Map.of("partitionId", partitionId, "key", key);
  }

  private Map<String, Object> rowParameter(final Object row) {
    return Map.of("partitionId", partitionId, "row", row);
  }

  /**
   * Collects the writes and sends them in one MyBatis batch, one entity type after the other, each
   * with all its deletes and then all its inserts. A row that is written is deleted first, as it
   * isn't known whether it exists in the database.
   */
  private final class Batch implements Backend.Batch<EntityKey, Object> {

    private final List<EntityKey> deletes = new ArrayList<>();
    private final List<Map.Entry<EntityKey, Object>> puts = new ArrayList<>();

    @Override
    public void put(final EntityKey key, final Object row) {
      if (key.type() == RdbmsVariableStore.VARIABLE) {
        variableKeys.written((VariableKey) key.key());
      }
      puts.add(Map.entry(key, row));
    }

    @Override
    public void delete(final EntityKey key) {
      deletes.add(key);
    }

    @Override
    public void write() {
      if (puts.isEmpty() && deletes.isEmpty()) {
        return;
      }
      try (final var session = sessionFactory.openSession(ExecutorType.BATCH)) {
        final List<EntityKey> toDelete = new ArrayList<>(deletes);
        puts.forEach(put -> toDelete.add(put.getKey()));
        // the batch executor reuses a statement while consecutive calls have the same SQL
        toDelete.sort(BY_TYPE);
        puts.sort(Comparator.comparingInt(put -> put.getKey().type().id));
        for (final var key : toDelete) {
          session.delete(key.type().delete, keyParameter(key.key()));
        }
        for (final var put : puts) {
          session.insert(put.getKey().type().insert, rowParameter(put.getValue()));
        }
        session.flushStatements();
        session.commit(true);
      } catch (final PersistenceException e) {
        throw new ZeebeDbException("Failed to write a batch of rows", e);
      }
    }

    @Override
    public void close() {}
  }
}
