/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_7;

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
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.distribution.PersistedCommandDistribution;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

public class DbDistributionMigrationState8dot7 {
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

  public DbDistributionMigrationState8dot7(
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

  public void migrateIdempotentCommandDistributions() {
    queueId.wrapString(DistributionQueue.DEPLOYMENT.getQueueId());
    final var isFirstInQueue = new AtomicBoolean(true);

    pendingDistributionColumnFamily.forEach(
        (compositeKey, nil) -> {
          final var distributionKey = compositeKey.first().inner().getValue();
          final var partitionId = compositeKey.second().getValue();
          this.distributionKey.wrapLong(distributionKey);
          partitionKey.wrapInt(partitionId);

          final var persistedDistribution =
              commandDistributionRecordColumnFamily.get(this.distributionKey);

          final var valueType = persistedDistribution.getValueType();
          final var isDeploymentOrDeletion =
              valueType == ValueType.DEPLOYMENT || valueType == ValueType.RESOURCE_DELETION;

          if (isDeploymentOrDeletion && persistedDistribution.getQueueId().isEmpty()) {
            persistedDistribution.setQueueId(DistributionQueue.DEPLOYMENT.getQueueId());
            commandDistributionRecordColumnFamily.update(
                this.distributionKey, persistedDistribution);
            queuedCommandDistributionColumnFamily.insert(queuedDistributionKey, DbNil.INSTANCE);

            if (isFirstInQueue.get()) {
              isFirstInQueue.set(false);
            } else {
              retriableDistributionColumnFamily.deleteExisting(distributionPartitionKey);
            }
          }
        });
  }
}
