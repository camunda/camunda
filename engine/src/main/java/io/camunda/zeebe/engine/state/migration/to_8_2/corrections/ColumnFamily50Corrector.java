/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_2.corrections;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.ZeebeDbConstants;
import io.camunda.zeebe.engine.state.migration.DbMigratorImpl;
import io.camunda.zeebe.engine.state.migration.MigrationTaskState;
import io.camunda.zeebe.engine.state.migration.MigrationTaskState.State;
import io.camunda.zeebe.engine.state.signal.SignalSubscription;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to correct the column family prefix for the signal name and subscription key
 * which may contain entries for the MigrationState column family. Vice-versa correction is not
 * needed because no data was written wrongly to the MigrationState column family.
 *
 * <p>Correction: {@link ZbColumnFamilies#SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY} -> {@link
 * ZbColumnFamilies#MIGRATIONS_STATE}
 */
@SuppressWarnings("deprecation") // deals with deprecated column families
public final class ColumnFamily50Corrector {

  private static final Logger LOG = LoggerFactory.getLogger(DbMigratorImpl.class.getPackageName());

  private static final ZbColumnFamilies CF_UNDER_RECOVERY =
      ZbColumnFamilies.SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY;
  private static final ZbColumnFamilies CF_POSSIBLE_TARGET = ZbColumnFamilies.MIGRATIONS_STATE;

  private final ColumnFamily<DbBytes, DbBytes> recoverySignalNameAndSubscriptionKeyColumnFamily;

  private final SignalSubscription signalSubscription = new SignalSubscription();

  private final DbLong subscriptionKey;
  private final DbString signalName;

  private final DbString migrationIdentifier;
  private final MigrationTaskState migrationTaskState;
  private final ColumnFamily<DbString, MigrationTaskState> migrationStateColumnFamily;

  public ColumnFamily50Corrector(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    recoverySignalNameAndSubscriptionKeyColumnFamily =
        zeebeDb.createColumnFamily(
            CF_UNDER_RECOVERY, transactionContext, new DbBytes(), new DbBytes());

    subscriptionKey = new DbLong();
    signalName = new DbString();

    migrationIdentifier = new DbString();
    migrationTaskState = new MigrationTaskState();
    migrationStateColumnFamily =
        zeebeDb.createColumnFamily(
            CF_POSSIBLE_TARGET, transactionContext, migrationIdentifier, migrationTaskState);
  }

  public void correctColumnFamilyPrefix() {
    recoverySignalNameAndSubscriptionKeyColumnFamily.forEach(
        (key, value) -> {
          if (!isKeyWithExpectedLength(key)) {
            // the key is not fitting to the expected data
            LOG.trace(
                "Found invalid key [{}] (incorrect key length) in column family [{}] {}",
                key,
                CF_UNDER_RECOVERY.ordinal(),
                CF_UNDER_RECOVERY.name());
            tryMoveDataToMigrationStateColumnFamily(key, value);
            return;
          }

          try {
            // so it appears the key fits the expected length, let's try to read it to ensure it
            // is a signal name and subscription key
            final DbCompositeKey<DbString, DbLong> signalNameAndSubscriptionKey =
                new DbCompositeKey<>(signalName, subscriptionKey);
            signalNameAndSubscriptionKey.wrap(key.getDirectBuffer(), 0, key.getLength());
          } catch (final Exception e) {
            LOG.trace(
                "Found invalid key [{}] (unable to read key) in column family [{}] {}",
                key,
                CF_UNDER_RECOVERY.ordinal(),
                CF_UNDER_RECOVERY.name());
            tryMoveDataToMigrationStateColumnFamily(key, value);
            return;
          }

          // if we got here, the key is fitting to the expected length, and we're able to read it,
          // let's see if the data makes any sense.

          // we can verify the value, which must be a signal subscription
          try {
            signalSubscription.wrap(value.getDirectBuffer(), 0, value.getLength());
          } catch (final Exception e) {
            // we were not able to read the signal subscription, so it is likely that this is
            // actually not a signal subscription but something else
            LOG.trace(
                "Found invalid value [{}] (unable to read value) in column family [{}] {}",
                value,
                CF_UNDER_RECOVERY.ordinal(),
                CF_UNDER_RECOVERY.name());
            tryMoveDataToMigrationStateColumnFamily(key, value);
            return;
          }

          // Lastly, let's ensure we read the entire value by checking the length
          if (value.getLength() != signalSubscription.getLength()) {
            // somehow there is still some data left in the value, let's assume this is not a
            // signal subscription
            LOG.trace(
                "Found invalid value [{}] (incorrect value length) in column family [{}] {}",
                value,
                CF_UNDER_RECOVERY.ordinal(),
                CF_UNDER_RECOVERY.name());
            tryMoveDataToMigrationStateColumnFamily(key, value);
            return;
          }

          // if we got here, both the key and the value appear to be fitting to the expected
          // format and the data appears to make any sense. We can assume that this is a signal
          // subscription and we can leave it as is.

          LOG.trace(
              "Found valid signal subscription entry with key[{}] in recovery column family", key);
        });
  }

  /**
   * the key is supposed to be a composite key of signalName and subscriptionKey. signalName is a
   * string, these are encoded as [length][bytes] where length is an int. subscriptionKey is a long,
   * so it is encoded as [key] so the expected key is supposed to be [length][bytes][key]
   *
   * <p>we can calculate the total expected length as:
   *
   * <ul>
   *   <li>4 bytes for the length of the string (number of bytes to store an int)
   *   <li>+ the length of the string
   *   <li>+ 8 bytes for the long (number of bytes to store a long)
   * </ul>
   *
   * if the key is shorter or longer than this, we can assume that it is not a key from the signal
   * name and subscription key column family.
   */
  private static boolean isKeyWithExpectedLength(final DbBytes key) {
    final int stringLength = key.getDirectBuffer().getInt(0, ZeebeDbConstants.ZB_DB_BYTE_ORDER);
    return key.getLength() == Integer.BYTES + stringLength + Long.BYTES;
  }

  private void tryMoveDataToMigrationStateColumnFamily(final DbBytes key, final DbBytes value) {
    // Now we caught a case where the key is not fitting to the expected data
    // likely this is a key from MIGRATE_STATE, let's try to move it to the correct cf

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
