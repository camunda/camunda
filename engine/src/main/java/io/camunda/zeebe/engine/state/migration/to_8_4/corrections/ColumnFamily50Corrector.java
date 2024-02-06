/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_4.corrections;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.migration.DbMigratorImpl;
import io.camunda.zeebe.engine.state.migration.MigrationTaskState;
import io.camunda.zeebe.engine.state.migration.MigrationTaskState.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to correct the column family prefix for the signal name and subscription key
 * which may contain entries for the MigrationState column family. Vice-versa correction is not
 * needed because no data was written wrongly to the MigrationState column family.
 *
 * <p>Correction: {@link ZbColumnFamilies#DEPRECATED_SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY} -> {@link
 * ZbColumnFamilies#MIGRATIONS_STATE}
 */
@SuppressWarnings("deprecation") // deals with deprecated column families
public final class ColumnFamily50Corrector {

  private static final Logger LOG = LoggerFactory.getLogger(DbMigratorImpl.class.getPackageName());

  private static final ZbColumnFamilies CF_UNDER_RECOVERY =
      ZbColumnFamilies.DEPRECATED_SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY;
  private static final ZbColumnFamilies CF_POSSIBLE_TARGET = ZbColumnFamilies.MIGRATIONS_STATE;

  private final ColumnFamily<DbBytes, DbBytes> recoverySignalNameAndSubscriptionKeyColumnFamily;

  private final DbString migrationIdentifier;
  private final MigrationTaskState migrationTaskState;
  private final ColumnFamily<DbString, MigrationTaskState> migrationStateColumnFamily;

  public ColumnFamily50Corrector(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    recoverySignalNameAndSubscriptionKeyColumnFamily =
        zeebeDb.createColumnFamily(
            CF_UNDER_RECOVERY, transactionContext, new DbBytes(), new DbBytes());

    migrationIdentifier = new DbString();
    migrationTaskState = new MigrationTaskState();
    migrationStateColumnFamily =
        zeebeDb.createColumnFamily(
            CF_POSSIBLE_TARGET, transactionContext, migrationIdentifier, migrationTaskState);
  }

  public void correctColumnFamilyPrefix() {
    recoverySignalNameAndSubscriptionKeyColumnFamily.forEach(
        this::tryMoveDataToMigrationStateColumnFamily);
  }

  private void tryMoveDataToMigrationStateColumnFamily(final DbBytes key, final DbBytes value) {
    // All data in the recovery column family can only be of the type MigrationTaskState
    // let's move it to the correct cf

    // we can start by attempting to read the migration identifier and the migration task state
    try {
      migrationIdentifier.wrap(key.getDirectBuffer(), 0, key.getLength());
      migrationTaskState.wrap(value.getDirectBuffer(), 0, value.getLength());
    } catch (final Exception e) {
      // if we cannot read the migration identifier or the migration task state, we cannot move it
      // to the correct column family, we're not sure what this data is though. Let's throw an
      // error.
      final String reason = "unexpected data in column family";
      throw new ColumnFamilyCorrectionException(reason, key, value, CF_UNDER_RECOVERY, e);
    }

    if (migrationTaskState.getState() == State.NOT_STARTED) {
      // we don't want to override it if it is already marked as finished
      final var currentState = migrationStateColumnFamily.get(migrationIdentifier);
      if (currentState != null && currentState.getState() == State.FINISHED) {
        // already marked as finished in the actual column family
        // no need to override it, let's just delete the entry stored in the wrong cf
        deleteEntryFromRecoveryColumnFamily(key);
        return;
      }
    }

    // the migration state is marked as finished in the wrong column family, let's move it to the
    // correct column family
    moveEntryFromRecoveryColumnFamilyToMigrationStateColumnFamily(
        key, migrationIdentifier, migrationTaskState);
  }

  private void moveEntryFromRecoveryColumnFamilyToMigrationStateColumnFamily(
      final DbBytes key,
      final DbString migrationIdentifier,
      final MigrationTaskState migrationTaskState) {
    LOG.debug(
        "Copying entry with key[{}] from column family [{}] {} to column family [{}] {}",
        key,
        CF_UNDER_RECOVERY.ordinal(),
        CF_UNDER_RECOVERY.name(),
        CF_POSSIBLE_TARGET.ordinal(),
        CF_POSSIBLE_TARGET.name());
    migrationStateColumnFamily.upsert(migrationIdentifier, migrationTaskState);
    deleteEntryFromRecoveryColumnFamily(key);
  }

  private void deleteEntryFromRecoveryColumnFamily(final DbBytes key) {
    LOG.debug(
        "Deleting entry with key[{}] from column family [{}] {}",
        key,
        CF_UNDER_RECOVERY.ordinal(),
        CF_UNDER_RECOVERY.name());
    recoverySignalNameAndSubscriptionKeyColumnFamily.deleteExisting(key);
  }
}
