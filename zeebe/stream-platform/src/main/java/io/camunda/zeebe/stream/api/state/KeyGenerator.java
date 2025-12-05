/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api.state;

/** Generate unique keys. Should be used for records only. */
public interface KeyGenerator {

  KeyGenerator IMMUTABLE =
      new KeyGenerator() {
        @Override
        public long nextKey() {
          throw new UnsupportedOperationException("KeyGenerator is not supported in this context.");
        }

        @Override
        public void overwriteNextKey(final long nextKey) {
          throw new UnsupportedOperationException("KeyGenerator is not supported in this context.");
        }
      };

  /**
   * Returns the next key of a record and updates the key generator.
   *
   * @return the next key for a new record
   */
  long nextKey();

  /**
   * Overwrite the next key with the given value without any further validations. Use with caution.
   *
   * @param nextKey the next key to set
   */
  void overwriteNextKey(long nextKey);
}
