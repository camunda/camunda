/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.distribution;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.mutable.MutableDistributionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import java.util.Optional;
import org.agrona.collections.MutableBoolean;
import org.agrona.collections.MutableLong;
import org.agrona.collections.MutableReference;
import org.slf4j.Logger;

public class DbDistributionState implements MutableDistributionState {
  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  private final DbLong distributionKey;
  private final DbInt partitionKey;
  private final DbCompositeKey<DbForeignKey<DbLong>, DbInt> distributionPartitionKey;

  /** [distribution key | partition id] => [DbNil] */
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbInt>, DbNil>
      pendingDistributionColumnFamily;

  /** [distribution key | partition id] => [DbNil] */
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbInt>, DbNil>
      retriableDistributionColumnFamily;

  /** [distribution key] => [persisted command distribution] */
  private final ColumnFamily<DbLong, PersistedCommandDistribution>
      commandDistributionRecordColumnFamily;

  /** [queue id | partition id | distribution key ] => [] */
  private final ColumnFamily<
          DbCompositeKey<DbString, DbCompositeKey<DbInt, DbForeignKey<DbLong>>>, DbNil>
      queuedCommandDistributionColumnFamily;

  private final DbString queueId;
  private final DbCompositeKey<DbString, DbInt> queuePerPartitionKey;
  private final DbCompositeKey<DbString, DbCompositeKey<DbInt, DbForeignKey<DbLong>>>
      queuedDistributionKey;

  /** [queue id | continuation key] => persisted continuation command */
  private final ColumnFamily<DbCompositeKey<DbString, DbLong>, PersistedCommandDistribution>
      continuationCommandColumnFamily;

  private final DbLong continuationKey;
  private final DbCompositeKey<DbString, DbLong> continuationByQueueKey;

  public DbDistributionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    distributionKey = new DbLong();
    final DbForeignKey<DbLong> fkDistribution =
        new DbForeignKey<>(distributionKey, ZbColumnFamilies.COMMAND_DISTRIBUTION_RECORD);
    commandDistributionRecordColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.COMMAND_DISTRIBUTION_RECORD,
            transactionContext,
            distributionKey,
            new PersistedCommandDistribution());

    partitionKey = new DbInt();
    distributionPartitionKey = new DbCompositeKey<>(fkDistribution, partitionKey);
    pendingDistributionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PENDING_DISTRIBUTION,
            transactionContext,
            distributionPartitionKey,
            DbNil.INSTANCE);

    retriableDistributionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.RETRIABLE_DISTRIBUTION,
            transactionContext,
            distributionPartitionKey,
            DbNil.INSTANCE);

    queueId = new DbString();
    queuePerPartitionKey = new DbCompositeKey<>(queueId, partitionKey);
    queuedDistributionKey =
        new DbCompositeKey<>(queueId, new DbCompositeKey<>(partitionKey, fkDistribution));
    queuedCommandDistributionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.QUEUED_DISTRIBUTION,
            transactionContext,
            queuedDistributionKey,
            DbNil.INSTANCE);

    continuationKey = new DbLong();
    continuationByQueueKey = new DbCompositeKey<>(queueId, continuationKey);
    continuationCommandColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DISTRIBUTION_CONTINUATION,
            transactionContext,
            continuationByQueueKey,
            new PersistedCommandDistribution());
  }

  @Override
  public void addCommandDistribution(
      final long distributionKey, final CommandDistributionRecord commandDistributionRecord) {
    this.distributionKey.wrapLong(distributionKey);
    commandDistributionRecordColumnFamily.insert(
        this.distributionKey, new PersistedCommandDistribution().wrap(commandDistributionRecord));
  }

  @Override
  public void removeCommandDistribution(final long distributionKey) {
    this.distributionKey.wrapLong(distributionKey);
    commandDistributionRecordColumnFamily.deleteIfExists(this.distributionKey);
  }

  @Override
  public void addRetriableDistribution(final long distributionKey, final int partition) {
    this.distributionKey.wrapLong(distributionKey);
    partitionKey.wrapInt(partition);
    retriableDistributionColumnFamily.insert(distributionPartitionKey, DbNil.INSTANCE);
  }

  @Override
  public void removeRetriableDistribution(final long distributionKey, final int partition) {
    this.distributionKey.wrapLong(distributionKey);
    partitionKey.wrapInt(partition);
    retriableDistributionColumnFamily.deleteExisting(distributionPartitionKey);
  }

  @Override
  public void addPendingDistribution(final long distributionKey, final int partition) {
    this.distributionKey.wrapLong(distributionKey);
    partitionKey.wrapInt(partition);
    pendingDistributionColumnFamily.upsert(distributionPartitionKey, DbNil.INSTANCE);
  }

  @Override
  public void removePendingDistribution(final long distributionKey, final int partition) {
    this.distributionKey.wrapLong(distributionKey);
    partitionKey.wrapInt(partition);
    pendingDistributionColumnFamily.deleteExisting(distributionPartitionKey);
  }

  @Override
  public void enqueueCommandDistribution(
      final String queue, final long distributionKey, final int partition) {
    queueId.wrapString(queue);
    this.distributionKey.wrapLong(distributionKey);
    partitionKey.wrapInt(partition);
    queuedCommandDistributionColumnFamily.insert(queuedDistributionKey, DbNil.INSTANCE);
  }

  @Override
  public void removeQueuedDistribution(
      final String queue, final int partition, final long distributionKey) {
    queueId.wrapString(queue);
    partitionKey.wrapInt(partition);
    this.distributionKey.wrapLong(distributionKey);
    queuedCommandDistributionColumnFamily.deleteExisting(queuedDistributionKey);
  }

  @Override
  public void addContinuationCommand(
      final long continuationKey, final CommandDistributionRecord record) {
    queueId.wrapString(record.getQueueId());
    this.continuationKey.wrapLong(continuationKey);

    continuationCommandColumnFamily.insert(
        continuationByQueueKey, new PersistedCommandDistribution().wrap(record));
  }

  @Override
  public void removeContinuationCommand(final long continuationKey, final String queue) {
    queueId.wrapString(queue);
    this.continuationKey.wrapLong(continuationKey);

    continuationCommandColumnFamily.deleteExisting(continuationByQueueKey);
  }

  @Override
  public boolean hasRetriableDistribution(final long distributionKey) {
    this.distributionKey.wrapLong(distributionKey);

    final var hasRetriable = new MutableBoolean();
    retriableDistributionColumnFamily.whileEqualPrefix(
        this.distributionKey,
        (compositeKey, dbNil) -> {
          hasRetriable.set(true);
          return false;
        });

    return hasRetriable.get();
  }

  @Override
  public boolean hasPendingDistribution(final long distributionKey) {
    this.distributionKey.wrapLong(distributionKey);

    final var hasPending = new MutableBoolean();
    pendingDistributionColumnFamily.whileEqualPrefix(
        this.distributionKey,
        (compositeKey, dbNil) -> {
          hasPending.set(true);
          return false;
        });

    return hasPending.get();
  }

  @Override
  public boolean hasRetriableDistribution(final long distributionKey, final int partition) {
    this.distributionKey.wrapLong(distributionKey);
    partitionKey.wrapInt(partition);
    return retriableDistributionColumnFamily.exists(distributionPartitionKey);
  }

  @Override
  public boolean hasPendingDistribution(final long distributionKey, final int partition) {
    this.distributionKey.wrapLong(distributionKey);
    partitionKey.wrapInt(partition);
    return pendingDistributionColumnFamily.exists(distributionPartitionKey);
  }

  @Override
  public CommandDistributionRecord getCommandDistributionRecord(
      final long distributionKey, final int partition) {
    this.distributionKey.wrapLong(distributionKey);

    final var persistedDistribution =
        commandDistributionRecordColumnFamily.get(this.distributionKey);

    if (persistedDistribution == null) {
      return null;
    }

    return new CommandDistributionRecord()
        .setPartitionId(partition)
        .setValueType(persistedDistribution.getValueType())
        .setIntent(persistedDistribution.getIntent())
        .setCommandValue(persistedDistribution.getCommandValue());
  }

  @Override
  public void foreachRetriableDistribution(final PendingDistributionVisitor visitor) {
    final var lastDistributionKey = new MutableLong(0);
    final var lastPendingDistribution = new MutableReference<CommandDistributionRecord>();

    retriableDistributionColumnFamily.forEach(
        (compositeKey, nil) -> {
          final var distributionKey = compositeKey.first().inner().getValue();
          final var partitionId = compositeKey.second().getValue();

          // we may encounter the same distribution key for several partitions, we can reuse it
          if (lastDistributionKey.value != distributionKey) {
            final var pendingDistribution =
                getCommandDistributionRecord(distributionKey, partitionId);
            if (pendingDistribution == null) {
              LOG.warn(
                  "Expected to find a pending distribution with key {} for a partition {}, but none found. The state is inconsistent",
                  distributionKey,
                  partitionId);
              // we ignore this currently
              return;
            }
            lastDistributionKey.set(distributionKey);
            lastPendingDistribution.set(pendingDistribution);
          }

          final var commandDistributionRecord = new CommandDistributionRecord();
          commandDistributionRecord.wrap(lastPendingDistribution.get()).setPartitionId(partitionId);
          visitor.visit(distributionKey, commandDistributionRecord);
        });
  }

  @Override
  public void foreachPendingDistribution(final PendingDistributionVisitor visitor) {
    final var lastDistributionKey = new MutableLong(0);
    final var lastCommandDistribution = new MutableReference<CommandDistributionRecord>();

    pendingDistributionColumnFamily.whileTrue(
        (compositeKey, nil) -> {
          final var distributionKey = compositeKey.first().inner().getValue();
          final var partitionId = compositeKey.second().getValue();

          // we may encounter the same distribution key for several partitions, we can reuse it
          if (lastDistributionKey.value != distributionKey) {
            final var commandDistributionRecord =
                getCommandDistributionRecord(distributionKey, partitionId);
            if (commandDistributionRecord == null) {
              LOG.warn(
                  "Expected to find a command distribution with key {} for a partition {}, but none found. The state is inconsistent",
                  distributionKey,
                  partitionId);
              // we ignore this currently
              return true;
            }
            lastDistributionKey.set(distributionKey);
            lastCommandDistribution.set(commandDistributionRecord);
          }

          final var commandDistributionRecord = new CommandDistributionRecord();
          commandDistributionRecord.copyFrom(lastCommandDistribution.get());
          commandDistributionRecord.setPartitionId(partitionId);
          return visitor.visit(distributionKey, commandDistributionRecord);
        });
  }

  @Override
  public Optional<Long> getNextQueuedDistributionKey(final String queue, final int partition) {
    queueId.wrapString(queue);
    partitionKey.wrapInt(partition);
    final var nextDistributionKey = new MutableReference<Long>(null);
    queuedCommandDistributionColumnFamily.whileEqualPrefix(
        queuePerPartitionKey,
        (key, value) -> {
          nextDistributionKey.set(key.second().second().inner().getValue());
          return false;
        });
    return Optional.ofNullable(nextDistributionKey.get());
  }

  @Override
  public Optional<String> getQueueIdForDistribution(final long distributionKey) {
    this.distributionKey.wrapLong(distributionKey);

    return Optional.ofNullable(commandDistributionRecordColumnFamily.get(this.distributionKey))
        .flatMap(PersistedCommandDistribution::getQueueId);
  }

  @Override
  public boolean hasQueuedDistributions(final String queue) {
    queueId.wrapString(queue);
    final var hasQueuedDistributions = new MutableBoolean();
    queuedCommandDistributionColumnFamily.whileEqualPrefix(
        queueId,
        (key, value) -> {
          hasQueuedDistributions.set(true);
          return false;
        });
    return hasQueuedDistributions.get();
  }

  @Override
  public void forEachContinuationCommand(
      final String queue, final ContinuationCommandVisitor consumer) {
    queueId.wrapString(queue);
    continuationCommandColumnFamily.whileEqualPrefix(
        queueId,
        (key, value) -> {
          final var continuationKey = key.second().getValue();
          consumer.visit(continuationKey);
          return true;
        });
  }

  @Override
  public CommandDistributionRecord getContinuationRecord(final String queue, final long key) {
    queueId.wrapString(queue);
    continuationKey.wrapLong(key);

    final var persistedCommandDistribution =
        continuationCommandColumnFamily.get(continuationByQueueKey);
    if (persistedCommandDistribution == null) {
      return null;
    }

    return new CommandDistributionRecord()
        .setQueueId(queue)
        .setValueType(persistedCommandDistribution.getValueType())
        .setIntent(persistedCommandDistribution.getIntent())
        .setCommandValue(persistedCommandDistribution.getCommandValue());
  }
}
