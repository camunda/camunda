/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import static io.camunda.zeebe.engine.state.rdbms.RdbmsProcessStore.PARTITION_STATE;

import io.camunda.zeebe.db.impl.jdbc.MixedTransactionContext;
import io.camunda.zeebe.engine.state.rdbms.PartitionStateMapper.PartitionStateRow;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.stream.api.state.KeyGeneratorControls;
import io.camunda.zeebe.stream.api.state.KeyGeneratorFactory;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates the keys of a partition from a value of its state in the relational database, like
 * {@code DbKeyGenerator} does from RocksDB. The latest key is written through the partition's
 * {@link EntitySession}, so it commits in the same transaction as the changes that use the key.
 * Without it, a broker of another region that takes the partition over could only learn the latest
 * key by replaying the whole log.
 *
 * <p>The replay raises the key with {@link #setKeyIfHigher}; for events whose changes the database
 * already contains, the latest key in the database is already higher, so nothing is written.
 */
public final class RdbmsKeyGenerator implements KeyGeneratorControls {

  static final String LATEST_KEY = "key-generator.latest-key";

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsKeyGenerator.class);

  private final EntitySession session;
  private final int partitionId;
  private final long initialKey;
  private final long maxKey;

  RdbmsKeyGenerator(final EntitySession session) {
    this.session = session;
    partitionId = session.partitionId();
    initialKey = Protocol.encodePartitionId(partitionId, 0);
    // as DbKeyGenerator: leave the top bit of the key range as a buffer before it is exhausted
    maxKey = Protocol.encodePartitionId(partitionId, 1L << (Protocol.KEY_BITS - 1));
  }

  /**
   * Selects this key generator for a partition whose database is a {@code MixedZeebeDb}. It shares
   * the partition's {@link EntitySession} with {@link RdbmsStateStores}.
   */
  public static KeyGeneratorFactory factory(final RdbmsEngineStateResources resources) {
    return (partitionId, zeebeDb, transactionContext) ->
        new RdbmsKeyGenerator(
            EntitySession.of(
                ((MixedTransactionContext) transactionContext).jdbc(),
                partitionId,
                resources.sessionFactory()));
  }

  @Override
  public int partitionId() {
    return partitionId;
  }

  @Override
  public long nextKey() {
    final long nextKey = getCurrentKey() + 1;
    if (nextKey > maxKey) {
      throw new UnrecoverableException(
          new IllegalStateException(
              "Generated key %d exceeds the maximum allowed key value %d for partition %d"
                  .formatted(nextKey, maxKey, partitionId)));
    }
    write(nextKey);
    return nextKey;
  }

  @Override
  public long getCurrentKey() {
    final var row = session.get(PARTITION_STATE, LATEST_KEY);
    if (row == null) {
      return initialKey;
    }
    final long key = Long.parseLong(Objects.requireNonNull(row.value()));
    if (Protocol.decodePartitionId(key) != partitionId) {
      throw new UnrecoverableException(
          new IllegalStateException(
              ("Current key in the state %d does not belong to partition %d, but belongs to %d. "
                      + "Potential data corruption suspected.")
                  .formatted(key, partitionId, Protocol.decodePartitionId(key))));
    }
    return key;
  }

  @Override
  public void setKeyIfHigher(final long key) {
    if (key <= getCurrentKey()) {
      return;
    }
    if (Protocol.decodePartitionId(key) != partitionId) {
      throw new UnrecoverableException(
          new IllegalArgumentException(
              "Provided key %d does not belong to partition %d, it belongs to %d"
                  .formatted(key, partitionId, Protocol.decodePartitionId(key))));
    }
    if (key > maxKey) {
      // as DbKeyGenerator: don't stop the partition for a key that was already generated
      LOG.warn(
          "Provided key {} is higher than the maximum allowed key value {} for partition {}, "
              + "skipping setKeyIfHigher.",
          key,
          maxKey,
          partitionId);
      return;
    }
    write(key);
  }

  private void write(final long key) {
    session.upsert(PARTITION_STATE, new PartitionStateRow(LATEST_KEY, Long.toString(key)));
  }
}
