/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Represents an column family, where it is possible to store keys of type {@link KeyType} and
 * corresponding values of type {@link ValueType}.
 *
 * @param <KeyType> the type of the keys
 * @param <ValueType> the type of the values
 */
public interface ColumnFamily<KeyType extends DbKey, ValueType extends DbValue> {

  /**
   * Inserts a new key value pair into the column family.
   *
   * @throws IllegalStateException if key already exists
   */
  void insert(KeyType key, ValueType value);

  /**
   * Updates the value of an existing key in the column family.
   *
   * @throws IllegalStateException if key does not exist
   */
  void update(KeyType key, ValueType value);

  /** Inserts or updates a key value pair in the column family. */
  void upsert(KeyType key, ValueType value);

  /**
   * The corresponding stored value in the column family to the given key.
   *
   * @param key the key
   * @return if the key was found in the column family then the value, otherwise null
   */
  ValueType get(KeyType key);

  /**
   * Visits the values, which are stored in the column family. The ordering depends on the key.
   *
   * <p>The given consumer accepts the values. Be aware that the given DbValue wraps the stored
   * value and reflects the current iteration step. The DbValue should not be stored, since it will
   * change his internal value during iteration.
   *
   * @param consumer the consumer which accepts the value
   */
  void forEach(Consumer<ValueType> consumer);

  /**
   * Visits the key-value pairs, which are stored in the column family. The ordering depends on the
   * key.
   *
   * <p>Similar to {@link #forEach(Consumer)}.
   *
   * @param consumer the consumer which accepts the key-value pairs
   */
  void forEach(BiConsumer<KeyType, ValueType> consumer);

  /**
   * Visits the key-value pairs, which are stored in the column family. The ordering depends on the
   * key. The visitor can indicate via the return value, whether the iteration should continue or
   * not. This means if the visitor returns false the iteration will stop.
   *
   * <p>The given {@code startAtKey} indicates where the iteration should start. If the key exists,
   * the first key-value-pair will contain the equal key as {@code startAtKey}. If the key doesn't
   * exist it will start after.
   *
   * <p>Similar to {@link #whileTrue(KeyValuePairVisitor)}}.
   *
   * @param startAtKey indicates on which key the iteration should start
   * @param visitor the visitor which visits the key-value pairs
   */
  void whileTrue(KeyType startAtKey, KeyValuePairVisitor<KeyType, ValueType> visitor);

  /**
   * Visits the key-value pairs, which are stored in the column family. The ordering depends on the
   * key. The visitor can indicate via the return value, whether the iteration should continue or
   * not. This means if the visitor returns false the iteration will stop.
   *
   * <p>Similar to {@link #forEach(BiConsumer)}.
   *
   * @param visitor the visitor which visits the key-value pairs
   */
  void whileTrue(KeyValuePairVisitor<KeyType, ValueType> visitor);

  /**
   * Visits the key-value pairs, which are stored in the column family and which have the same
   * common prefix. The ordering depends on the key.
   *
   * <p>Similar to {@link #forEach(BiConsumer)}.
   *
   * @param keyPrefix the prefix which should have the keys in common
   * @param visitor the visitor which visits the key-value pairs
   */
  void whileEqualPrefix(DbKey keyPrefix, BiConsumer<KeyType, ValueType> visitor);

  /**
   * Visits the key-value pairs, which are stored in the column family and which have the same
   * common prefix. The ordering depends on the key. The visitor can indicate via the return value,
   * whether the iteration should continue or * not. This means if the visitor returns false the
   * iteration will stop.
   *
   * <p>Similar to {@link #whileEqualPrefix(DbKey, BiConsumer) and {@link
   * #whileTrue(KeyValuePairVisitor)}}.
   *
   * @param keyPrefix the prefix which should have the keys in common
   * @param visitor the visitor which visits the key-value pairs
   */
  void whileEqualPrefix(DbKey keyPrefix, KeyValuePairVisitor<KeyType, ValueType> visitor);

  /**
   * Visits the key-value pairs, which are stored in the column family and which have the same
   * common prefix. The ordering depends on the key. The visitor can indicate via the return value,
   * whether the iteration should continue or * not. This means if the visitor returns false the
   * iteration will stop.
   *
   * <p>The given {@code startAtKey} indicates where the iteration should start. If the key exists,
   * the first key-value-pair will contain the equal key as {@code startAtKey}. If the key doesn't
   * exist it will start after.
   *
   * <p>Similar to {@link #whileEqualPrefix(DbKey, BiConsumer) and {@link
   * #whileTrue(KeyValuePairVisitor)}}.
   *
   * @param keyPrefix the prefix which should have the keys in common
   * @param startAtKey indicates on which key the iteration should start
   * @param visitor the visitor which visits the key-value pairs
   */
  void whileEqualPrefix(
      DbKey keyPrefix, KeyType startAtKey, KeyValuePairVisitor<KeyType, ValueType> visitor);

  /**
   * Deletes the key-value pair with the given key if it exists in the column family
   *
   * @throws IllegalStateException if the key does not exist
   */
  void deleteExisting(KeyType key);

  /**
   * Deletes the key-value pair if the key does exist in the column family. No-op if the key does
   * not exist.
   */
  void deleteIfExists(KeyType key);

  /**
   * Checks for key existence in the column family.
   *
   * @param key the key to look for
   * @return true if the key exist in this column family, false otherwise
   */
  boolean exists(KeyType key);

  /**
   * Checks if the column family has any entry.
   *
   * @return <code>true</code> if the column family has no entry
   */
  boolean isEmpty();

  /**
   * Count the number of entries in the column family by iterating over all its entries. This is an
   * expensive operation and should be used with care.
   *
   * @return the number of entries in the column family
   */
  long count();

  /**
   * Count the number of entries in the column family which have the same common prefix by iterating
   * over all its entries. This is an expensive operation and should be used with care.
   *
   * @param prefix the prefix which should have the keys in common
   * @return the number of entries in the column family which have the same common prefix
   */
  long countEqualPrefix(DbKey prefix);
}
