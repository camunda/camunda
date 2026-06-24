/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl;

import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;

import org.agrona.DirectBuffer;

/**
 * Utility for deterministically mapping a key to a partition ID based on hashing. This is used to
 * ensure that the same key always maps to the same partition, enabling per-partition uniqueness
 * validation and consistent routing across scaling operations.
 */
public final class PartitionUtil {

  private PartitionUtil() {}

  /**
   * Computes a hash code for the given key buffer, equivalent to {@link String#hashCode()}.
   *
   * @param key the key buffer to hash
   * @return the hash code
   */
  static int hashCode(final DirectBuffer key) {
    int hashCode = 0;

    for (int i = 0, length = key.capacity(); i < length; i++) {
      hashCode = 31 * hashCode + key.getByte(i);
    }
    return hashCode;
  }

  /**
   * Deterministically maps a key to a partition ID by hashing the key and applying modulo over the
   * partition count.
   *
   * @param key the key buffer to hash
   * @param partitionCount the total number of partitions
   * @return a partition ID in the range [{@link
   *     io.camunda.zeebe.protocol.Protocol#START_PARTITION_ID}, {@code START_PARTITION_ID +
   *     partitionCount})
   */
  public static int getPartitionId(final DirectBuffer key, final int partitionCount) {
    final int hashCode = hashCode(key);
    return Math.abs(hashCode % partitionCount) + START_PARTITION_ID;
  }
}
