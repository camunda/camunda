/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api.state;

import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.stream.api.KeyValidator;
import io.camunda.zeebe.util.exception.UnrecoverableException;

/** Generate unique keys. Should be used for records only. */
public interface KeyGenerator extends KeyValidator {

  int partitionId();

  /**
   * Returns the next key of a record and updates the key generator.
   *
   * @return the next key for a new record
   */
  long nextKey();

  /**
   * Returns the current key value from the state, which represents the last generated key.
   *
   * @return the current key value
   */
  long getCurrentKey();

  @Override
  default void validateKey(final long key) {
    {
      if (key == -1L) {
        return;
      }
      final var decodedPartitionId = Protocol.decodePartitionId(key);
      final var currentKey = getCurrentKey();
      if (decodedPartitionId == partitionId() && key > currentKey) {
        throw new UnrecoverableException(
            "Expected to append event with key lesser than the last generated key %d, but got %d"
                .formatted(currentKey, key));
      }
    }
  }

  /**
   * Returns an immutable {@code KeyGenerator} that throws {@link UnsupportedOperationException} for
   * all operations. Useful for contexts where key generation should not be allowed, such as
   * read-only or restricted environments.
   *
   * @return an immutable KeyGenerator instance
   */
  static KeyGenerator immutable(final int partitionId) {
    return new KeyGenerator() {
      @Override
      public int partitionId() {
        return partitionId;
      }

      @Override
      public long nextKey() {
        throw new UnsupportedOperationException("Not allowed to generate a new key");
      }

      @Override
      public long getCurrentKey() {
        throw new UnsupportedOperationException("Not allowed to fetch current key");
      }
    };
  }
}
