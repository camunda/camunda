/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;

/**
 * Stages the writes to one entity type during a transaction and flushes them as batches when the
 * transaction commits. Reads see the staged writes first, so a transaction sees its own uncommitted
 * changes.
 *
 * <p>Each key has at most one staged write; repeated writes collapse:
 *
 * <ul>
 *   <li>insert, then update: one insert with the latest entity (the row isn't in the database yet)
 *   <li>insert, then delete: nothing is written
 *   <li>update, then delete: one delete
 *   <li>delete, then insert: one update. This assumes the deleted row exists in the database; if it
 *       doesn't, the flush fails like any update of a missing row.
 *   <li>insert of a staged insert/update, or update of a staged delete: {@link
 *       IllegalStateException}
 * </ul>
 *
 * <p>The cache doesn't check the database on writes: an insert of an existing row or an update of a
 * missing one fails on flush. Committed rows are not cached; reads of unstaged keys always go to
 * the database. Not thread-safe, like the {@link JdbcTransactionContext} it takes part in.
 *
 * @param <K> the entity's primary key
 * @param <E> the entity, an immutable snapshot (see {@link EntityPersister})
 */
public final class EntityCache<K, E> implements JdbcTransactionParticipant {

  private final String entityName;
  private final EntityPersister<K, E> persister;
  private final Map<K, Staged<E>> staged = new LinkedHashMap<>();

  /**
   * @param entityName how error messages name the entity, e.g. {@code "element instance"}
   */
  public EntityCache(final String entityName, final EntityPersister<K, E> persister) {
    this.entityName = entityName;
    this.persister = persister;
  }

  /** Returns the staged entity, or null if its deletion is staged, or else the committed one. */
  public @Nullable E get(final K key) throws SQLException {
    final var write = staged.get(key);
    if (write != null) {
      return write.entity;
    }
    return persister.load(key);
  }

  public void insert(final E entity) {
    final K key = persister.keyOf(entity);
    final var write = staged.get(key);
    if (write != null && write.operation != Operation.DELETE) {
      throw new IllegalStateException(
          String.format(
              "Expected to insert %s with key %s, but it already exists", entityName, key));
    }
    // a staged delete means the row exists in the database: re-inserting it overwrites the row
    final var operation = write == null ? Operation.INSERT : Operation.UPDATE;
    staged.put(key, new Staged<>(operation, entity));
  }

  public void update(final E entity) {
    final K key = persister.keyOf(entity);
    final var write = staged.get(key);
    if (write != null && write.operation == Operation.DELETE) {
      throw new IllegalStateException(
          String.format("Expected to update %s with key %s, but it was removed", entityName, key));
    }
    final var operation = write == null ? Operation.UPDATE : write.operation;
    staged.put(key, new Staged<>(operation, entity));
  }

  /** Stages the deletion of the row with the given key; deleting a missing row is no error. */
  public void delete(final K key) {
    final var write = staged.get(key);
    if (write != null && write.operation == Operation.INSERT) {
      staged.remove(key);
    } else {
      staged.put(key, new Staged<>(Operation.DELETE, null));
    }
  }

  /**
   * Merges the result of a database query with the staged writes, as if the staged writes were
   * already committed: a staged write of a key replaces the committed row, so it hides a row that
   * no longer matches or was deleted, and staged entities matching the query are added.
   *
   * @param dbResults the committed rows matching the query
   * @param stagedMatches the query's condition, applied to the staged entities
   * @param order the order of the result
   */
  public List<E> query(
      final Collection<? extends E> dbResults,
      final Predicate<? super E> stagedMatches,
      final Comparator<? super E> order) {
    final List<E> result = new ArrayList<>();
    for (final E entity : dbResults) {
      if (!staged.containsKey(persister.keyOf(entity))) {
        result.add(entity);
      }
    }
    result.addAll(stagedMatching(stagedMatches));
    result.sort(order);
    return result;
  }

  /**
   * Like {@link #query(Collection, Predicate, Comparator)}, for queries that only select the keys
   * of the matching rows.
   */
  public List<K> queryKeys(
      final Collection<? extends K> dbKeys,
      final Predicate<? super E> stagedMatches,
      final Comparator<? super K> order) {
    final List<K> result = new ArrayList<>();
    for (final K key : dbKeys) {
      if (!staged.containsKey(key)) {
        result.add(key);
      }
    }
    for (final E entity : stagedMatching(stagedMatches)) {
      result.add(persister.keyOf(entity));
    }
    result.sort(order);
    return result;
  }

  /** The staged (not deleted) entities matching the given condition. */
  public List<E> stagedMatching(final Predicate<? super E> condition) {
    final List<E> result = new ArrayList<>();
    for (final var write : staged.values()) {
      if (write.entity != null && condition.test(write.entity)) {
        result.add(write.entity);
      }
    }
    return result;
  }

  /**
   * The keys of all staged writes, including deletions. A query can exclude these keys from the
   * committed rows and add the {@link #stagedMatching(Predicate) staged matches} itself, e.g. to
   * count without loading the rows.
   */
  public Set<K> stagedKeys() {
    return Collections.unmodifiableSet(staged.keySet());
  }

  @Override
  public void flush(final Connection connection) throws SQLException {
    if (staged.isEmpty()) {
      return;
    }

    final List<E> inserts = new ArrayList<>();
    final List<E> updates = new ArrayList<>();
    final List<K> deletes = new ArrayList<>();
    for (final var entry : staged.entrySet()) {
      final var write = entry.getValue();
      switch (write.operation) {
        case INSERT -> inserts.add(write.entity);
        case UPDATE -> updates.add(write.entity);
        case DELETE -> deletes.add(entry.getKey());
      }
    }

    final int[] updateCounts = persister.flush(connection, inserts, updates, deletes);
    verifyUpdated(updates, updateCounts);
  }

  @Override
  public void reset() {
    staged.clear();
  }

  private void verifyUpdated(final List<E> updates, final int[] updateCounts) throws SQLException {
    for (int i = 0; i < updateCounts.length && i < updates.size(); i++) {
      final int count = updateCounts[i];
      if (count == 0) {
        throw new SQLException(
            String.format(
                "Expected to update %s with key %s, but it does not exist",
                entityName, persister.keyOf(updates.get(i))));
      }
      if (count != Statement.SUCCESS_NO_INFO && count != 1) {
        throw new SQLException(
            String.format(
                "Expected to update one %s with key %s, but updated %d",
                entityName, persister.keyOf(updates.get(i)), count));
      }
    }
  }

  private enum Operation {
    INSERT,
    UPDATE,
    DELETE
  }

  /** A staged write; the entity is null for a delete. */
  private record Staged<E>(Operation operation, @Nullable E entity) {}
}
