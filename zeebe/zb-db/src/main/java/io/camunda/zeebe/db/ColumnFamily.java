/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * Represents a column family, where it is possible to store keys of type {@link KeyType} and
 * corresponding values of type {@link ValueType}.
 *
 * @param <KeyType> the type of the keys
 * @param <ValueType> the type of the values
 */
public interface ColumnFamily<KeyType extends DbKey, ValueType extends DbValue>
    extends ScopedColumnFamily {

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
  @Nullable ValueType get(KeyType key);

  /**
   * Returns the corresponding stored value in the column family to the given key. Returns null if
   * the key does not exist. The difference to {@link #get(KeyType)} is that this method calls the
   * {@code valueSupplier} to create a new instance of {@link ValueType} instead of a reference to
   * the mutable internal value instance.
   *
   * <p>Use this method if you would otherwise immediately copy the returned value.
   */
  @Nullable ValueType get(KeyType key, Supplier<ValueType> valueSupplier);

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
   * Visits the key-value pairs in reverse order, which are stored in the column family. The visitor
   * can indicate via the return value, whether the iteration should continue or not. This means if
   * the visitor returns false the iteration will stop.
   *
   * <p>The given {@code startAtKey} indicates where the iteration should start. If the key exists,
   * the first key-value-pair will contain the equal key as {@code startAtKey}. If the key doesn't
   * exist it will start at the key just before it.
   *
   * @param startAtKey indicates on which key the reverse iteration should start
   * @param visitor the visitor which visits the key-value pairs
   */
  void whileTrueReverse(KeyType startAtKey, KeyValuePairVisitor<KeyType, ValueType> visitor);

  // ---- Key-only iteration methods ----
  // These methods iterate over keys without reading the corresponding values from the database,
  // which can be significantly more efficient when only the keys are needed.

  /**
   * Visits the keys stored in the column family without reading the values. The ordering depends on
   * the key.
   *
   * <p>The given consumer accepts the keys. Be aware that the given DbKey wraps the stored key and
   * reflects the current iteration step. The DbKey should not be stored, since it will change its
   * internal value during iteration.
   *
   * @param consumer the consumer which accepts the key
   */
  void forEachKey(Consumer<KeyType> consumer);

  /**
   * Visits the keys stored in the column family without reading the values. The ordering depends on
   * the key. The visitor can indicate via the return value whether the iteration should continue or
   * not. This means if the visitor returns false the iteration will stop.
   *
   * @param visitor the visitor which visits the keys
   */
  void whileTrue(KeyVisitor<KeyType> visitor);

  /**
   * Visits the keys stored in the column family without reading the values. The ordering depends on
   * the key. The visitor can indicate via the return value whether the iteration should continue or
   * not. This means if the visitor returns false the iteration will stop.
   *
   * <p>The given {@code startAtKey} indicates where the iteration should start. If the key exists,
   * the first key will be equal to {@code startAtKey}. If the key doesn't exist it will start
   * after.
   *
   * @param startAtKey indicates on which key the iteration should start
   * @param visitor the visitor which visits the keys
   */
  void whileTrue(KeyType startAtKey, KeyVisitor<KeyType> visitor);

  /**
   * Visits the keys stored in the column family which have the same common prefix, without reading
   * the values. The ordering depends on the key.
   *
   * @param keyPrefix the prefix which should have the keys in common
   * @param visitor the consumer which accepts the keys
   */
  void whileEqualPrefix(DbKey keyPrefix, Consumer<KeyType> visitor);

  /**
   * Visits the keys stored in the column family which have the same common prefix, without reading
   * the values. The ordering depends on the key. The visitor can indicate via the return value
   * whether the iteration should continue or not. This means if the visitor returns false the
   * iteration will stop.
   *
   * @param keyPrefix the prefix which should have the keys in common
   * @param visitor the visitor which visits the keys
   */
  void whileEqualPrefix(DbKey keyPrefix, KeyVisitor<KeyType> visitor);

  /**
   * Visits the keys stored in the column family which have the same common prefix, without reading
   * the values. The ordering depends on the key. The visitor can indicate via the return value
   * whether the iteration should continue or not. This means if the visitor returns false the
   * iteration will stop.
   *
   * <p>The given {@code startAtKey} indicates where the iteration should start. If the key exists,
   * the first key will be equal to {@code startAtKey}. If the key doesn't exist it will start
   * after.
   *
   * @param keyPrefix the prefix which should have the keys in common
   * @param startAtKey indicates on which key the iteration should start
   * @param visitor the visitor which visits the keys
   */
  void whileEqualPrefix(DbKey keyPrefix, KeyType startAtKey, KeyVisitor<KeyType> visitor);

  /**
   * Visits the keys in reverse order stored in the column family without reading the values. The
   * visitor can indicate via the return value whether the iteration should continue or not. This
   * means if the visitor returns false the iteration will stop.
   *
   * <p>The given {@code startAtKey} indicates where the iteration should start. If the key exists,
   * the first key will be equal to {@code startAtKey}. If the key doesn't exist it will start at
   * the key just before it.
   *
   * @param startAtKey indicates on which key the reverse iteration should start
   * @param visitor the visitor which visits the keys
   */
  void whileTrueReverse(KeyType startAtKey, KeyVisitor<KeyType> visitor);

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
