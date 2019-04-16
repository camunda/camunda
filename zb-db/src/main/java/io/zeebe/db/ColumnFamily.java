/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.db;

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
   * Stores the key-value pair into the column family.
   *
   * @param key the key
   * @param value the value
   */
  void put(KeyType key, ValueType value);

  /**
   * Stores the key-value pair into the column family. Uses the provided {@code dbContext} instead
   * of the default instance, to be thread-safe.
   *
   * @param dbContext the database context
   * @param key the key
   * @param value the value
   */
  void put(DbContext dbContext, KeyType key, ValueType value);

  /**
   * The corresponding stored value in the column family to the given key.
   *
   * @param key the key
   * @return if the key was found in the column family then the value, otherwise null
   */
  ValueType get(KeyType key);

  /**
   * Looks up the value that corresponds to the {@code key} parameter in the column family. Uses the
   * provided {@code dbContext} and stores the result in the provided {@code value} instead of using
   * the default instances, making this method thread-safe.
   *
   * @param dbContext the database context
   * @param key the key
   * @param value the value
   * @return if the key was found in the column family then the value, otherwise null
   */
  ValueType get(DbContext dbContext, KeyType key, ValueType value);

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
   * <p>Similar to {@link #forEach(BiConsumer)}.
   *
   * @param consumer the consumer which accepts the key-value pairs
   */
  void forEach(BiConsumer<KeyType, ValueType> consumer);

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
   * Visits the key-value pairs, which are stored in the column family. The ordering depends on the
   * key. The visitor can indicate via the return value, whether the iteration should continue or
   * not. This means if the visitor returns false the iteration will stop. Uses the provided {@code
   * dbContext} as well as the {@code key} and {@code value} parameters to store the iterator's key
   * and value, making this method thread-safe.
   *
   * <p>Similar to {@link #forEach(BiConsumer)}.
   *
   * @param dbContext the database context
   * @param visitor the visitor which visits the key-value pairs
   * @param key the key instance
   * @param value the value instance
   */
  void whileTrue(
      DbContext dbContext,
      KeyValuePairVisitor<KeyType, ValueType> visitor,
      KeyType key,
      ValueType value);

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
   * Deletes the key-value pair with the given key from the column family.
   *
   * @param key the key which identifies the pair
   */
  void delete(KeyType key);

  /**
   * Deletes the key-value pair with the given key from the column family. Uses the provided {@code
   * dbContext} instead of the default instance, to be thread-safe.
   *
   * @param dbContext the database context
   * @param key the key which identifies the pair
   */
  void delete(DbContext dbContext, KeyType key);

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
   * Checks if the column family has any entry. Uses the provided {@code dbContext} instead of the
   * default instance, to be thread-safe.
   *
   * @param dbContext the database context
   * @return <code>true</code> if the column family has no entry
   */
  boolean isEmpty(DbContext dbContext);
}
