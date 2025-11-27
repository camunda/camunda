/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.state;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.api.state.KeyGeneratorControls;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.exception.UnrecoverableException;

public final class DbKeyGenerator implements KeyGeneratorControls {

  private static final long INITIAL_VALUE = 0;

  private static final String LATEST_KEY = "latestKey";

  private final long keyStartValue;
  private final NextValueManager nextValueManager;
  private final int partitionId;

  /**
   * Initializes the key state with the corresponding partition id, so that unique keys are
   * generated over all partitions.
   *
   * @param partitionId the partition to determine the key start value
   */
  public DbKeyGenerator(
      final int partitionId, final ZeebeDb zeebeDb, final TransactionContext transactionContext) {
    this.partitionId = partitionId;
    keyStartValue = Protocol.encodePartitionId(partitionId, INITIAL_VALUE);
    nextValueManager =
        new NextValueManager(
            keyStartValue, zeebeDb, transactionContext, ZbColumnFamilies.KEY, LATEST_KEY);

    final var currentKey = nextValueManager.getCurrentValue();
    if (Protocol.decodePartitionId(currentKey) != partitionId) {
      throw new UnrecoverableException(
          new IllegalStateException(
              String.format(
                  "Current key in the state %d does not belong to partition %d, but belongs to %d. Potential data corruption suspected.",
                  currentKey, partitionId, Protocol.decodePartitionId(currentKey))));
    }
  }

  @Override
  public long nextKey() {
    final var nextKey = nextValueManager.getNextValue();
    if (Protocol.decodePartitionId(nextKey) != partitionId) {
      throw new UnrecoverableException(
          new IllegalStateException(
              String.format(
                  "Generated key %d does not belong to partition %d, but belongs to %d",
                  nextKey, partitionId, Protocol.decodePartitionId(nextKey))));
    }
    return nextKey;
  }

  /**
   * Retrieve the current key from the state, since it is only used in tests it is not part of the
   * interface.
   *
   * @return the current key from the state
   */
  @VisibleForTesting
  public long getCurrentKey() {
    return nextValueManager.getCurrentValue();
  }

  @Override
  public void setKeyIfHigher(final long key) {
    final var currentKey = nextValueManager.getCurrentValue();

    if (key > currentKey) {
      if (Protocol.decodePartitionId(key) != partitionId) {
        throw new UnrecoverableException(
            new IllegalArgumentException(
                String.format(
                    "Provided key %d does not belong to partition %d, it belongs to %d",
                    key, partitionId, Protocol.decodePartitionId(key))));
      }
      nextValueManager.setValue(LATEST_KEY, key);
    }
  }
}
