/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.state;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.stream.api.state.KeyGeneratorControls;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DbKeyGenerator implements KeyGeneratorControls {

  private static final Logger LOGGER = LoggerFactory.getLogger(DbKeyGenerator.class);

  private static final long INITIAL_VALUE = 0;

  private static final String LATEST_KEY = "latestKey";

  private final long keyStartValue;
  private final NextValueManager nextValueManager;
  private final int partitionId;

  private final DbString maxKeyValueKey;
  private final DbLong maxKeyValueValue = new DbLong();
  private final ColumnFamily<DbString, DbLong> maxValueColumnFamily;
  private long maxKeyValue;

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

    maxKeyValueKey = new DbString();
    maxKeyValueKey.wrapString("maxKeyValue");
    // Reuse the column family of next key, but use a different key name.
    maxValueColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.KEY, transactionContext, maxKeyValueKey, maxKeyValueValue);

    final var currentKey = nextValueManager.getCurrentValue();
    if (Protocol.decodePartitionId(currentKey) != partitionId) {
      throw new UnrecoverableException(
          new IllegalStateException(
              String.format(
                  "Current key in the state %d does not belong to partition %d, but belongs to %d. Potential data corruption suspected.",
                  currentKey, partitionId, Protocol.decodePartitionId(currentKey))));
    }

    final var max = maxValueColumnFamily.get(maxKeyValueKey);
    if (max != null) {
      maxKeyValue = max.getValue();
    } else {
      // We deliberately leave 1 extra bit to give a buffer space before hitting the actual max key
      // range possible for this partition. This allows us to detect potential issues before we
      // exhaust the entire key space and provide more options for recovery.
      maxKeyValue = Protocol.encodePartitionId(partitionId, 1L << (Protocol.KEY_BITS - 1));
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

    if (nextKey > maxKeyValue) {
      throw new UnrecoverableException(
          new IllegalStateException(
              String.format(
                  "Generated key %d exceeds the maximum allowed key value %d for partition %d",
                  nextKey, maxKeyValue, partitionId)));
    }
    return nextKey;
  }

  /**
   * Retrieve the current key from the state, since it is only used in tests and debug tools it is
   * not part of the interface.
   *
   * @return the current key from the state
   */
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

      if (key > maxKeyValue) {
        // skip - we don't want to throw here to avoid stopping the processing of a partition due to
        // an invalid key that was generated already. Instead, we log a warning.
        LOGGER.warn(
            "Provided key {} is higher than the maximum allowed key value {} for partition {}, skipping setKeyIfHigher.",
            key,
            maxKeyValue,
            partitionId);
        return;
      }
      nextValueManager.setValue(LATEST_KEY, key);
    }
  }

  /**
   * Overwrite the current key in the state. Since this is a dangerous operation, use it with
   * caution. It is intended to be used to recover from specific failures manually..
   */
  public void overwriteKey(final long key) {
    if (Protocol.decodePartitionId(key) != partitionId) {
      throw new UnrecoverableException(
          new IllegalArgumentException(
              String.format(
                  "Provided key %d does not belong to partition %d, it belongs to %d",
                  key, partitionId, Protocol.decodePartitionId(key))));
    }

    if (key > maxKeyValue) {
      throw new UnrecoverableException(
          new IllegalArgumentException(
              String.format(
                  "Provided key %d exceeds the maximum allowed key value %d for partition %d",
                  key, maxKeyValue, partitionId)));
    }
    nextValueManager.setValue(LATEST_KEY, key);
  }

  /**
   * Retrieve the max key value allowed for this partition. Since it is only used in tests and debug
   * tools it is not part of the interface.
   *
   * @return the max key value stored in the state , or null if not set
   */
  public Long getMaxKeyValue() {
    final var readValue = maxValueColumnFamily.get(maxKeyValueKey);
    if (readValue != null) {
      return readValue.getValue();
    } else {
      return null;
    }
  }

  /**
   * This operation is intended to be used only to manually recover from specific failures.
   *
   * @param maxValue the max value allowed for the key
   */
  public void setMaxKeyValue(final long maxValue) {
    maxKeyValueValue.wrapLong(maxValue);
    maxValueColumnFamily.upsert(maxKeyValueKey, maxKeyValueValue);
    maxKeyValue = maxValue;
  }
}
