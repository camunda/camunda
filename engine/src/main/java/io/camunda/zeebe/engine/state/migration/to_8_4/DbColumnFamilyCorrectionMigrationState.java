/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_4;

import static io.camunda.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.message.DbMessageState;
import io.camunda.zeebe.engine.state.migration.MigrationTaskState;
import io.camunda.zeebe.engine.state.migration.MigrationTaskState.State;
import io.camunda.zeebe.engine.state.signal.SignalSubscription;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation") // deals with deprecated column families
public class DbColumnFamilyCorrectionMigrationState {

  private static final Logger LOG =
      LoggerFactory.getLogger(DbColumnFamilyCorrectionMigrationState.class.getPackageName());

  private final SignalNameAndSubscriptionKeyColumnFamilyCorrector
      signalNameAndSubscriptionKeyColumnFamilyCorrector;
  private final DmnDecisionKeyByDecisionIdAndVersionColumnFamilyCorrector
      dmnDecisionKeyByDecisionIdAndVersionColumnFamilyCorrector;

  public DbColumnFamilyCorrectionMigrationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    signalNameAndSubscriptionKeyColumnFamilyCorrector =
        new SignalNameAndSubscriptionKeyColumnFamilyCorrector(zeebeDb, transactionContext);
    dmnDecisionKeyByDecisionIdAndVersionColumnFamilyCorrector =
        new DmnDecisionKeyByDecisionIdAndVersionColumnFamilyCorrector(zeebeDb, transactionContext);
  }

  public void correctColumnFamilyPrefix() {
    signalNameAndSubscriptionKeyColumnFamilyCorrector.correctColumnFamilyPrefix();
    dmnDecisionKeyByDecisionIdAndVersionColumnFamilyCorrector.correctColumnFamilyPrefix();
  }

  /**
   * This class is used to correct the column family prefix for the signal name and subscription key
   * which may contain entries for the MigrationState column family. Vice-versa correction is not
   * needed because no data was written wrongly to the MigrationState column family.
   *
   * <p>Correction: {@link ZbColumnFamilies#DEPRECATED_SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY} ->
   * {@link ZbColumnFamilies#MIGRATIONS_STATE}
   */
  private static final class SignalNameAndSubscriptionKeyColumnFamilyCorrector {

    private static final ZbColumnFamilies CF_UNDER_RECOVERY =
        ZbColumnFamilies.DEPRECATED_SIGNAL_SUBSCRIPTION_BY_NAME_AND_KEY;
    private static final ZbColumnFamilies CF_POSSIBLE_TARGET = ZbColumnFamilies.MIGRATIONS_STATE;

    private final ColumnFamily<DbBytes, DbBytes> recoverySignalNameAndSubscriptionKeyColumnFamily;

    private final SignalSubscription signalSubscription = new SignalSubscription();

    private final DbLong subscriptionKey;
    private final DbString signalName;

    private final DbString migrationIdentifier;
    private final MigrationTaskState migrationTaskState;
    private final ColumnFamily<DbString, MigrationTaskState> migrationStateColumnFamily;

    public SignalNameAndSubscriptionKeyColumnFamilyCorrector(
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
                "Found valid signal subscription entry with key[{}] in recovery column family",
                key);
          });
    }

    /**
     * the key is supposed to be a composite key of signalName and subscriptionKey signalName is a
     * string, these are encoded as [length][bytes] where length is an int subscriptionKey is a
     * long, so it is encoded as [key] so the expected key is supposed to be [length][bytes][key]
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
      final int stringLength = key.getDirectBuffer().getInt(0, ZB_DB_BYTE_ORDER);
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
        // to the correct column family, we're not sure what this data is though. Let's log an
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

  /**
   * This class is used to correct the column family prefix for the DMN decision key by decision id
   * and version which may contain entries for the MessageStats column family. Vice-versa correction
   * is not needed because no data was written wrongly to the MessageStats column family.
   *
   * <p>Correction: {@link ZbColumnFamilies#DEPRECATED_DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION}
   * -> {@link ZbColumnFamilies#MESSAGE_STATS}
   */
  private static final class DmnDecisionKeyByDecisionIdAndVersionColumnFamilyCorrector {

    private static final ZbColumnFamilies CF_UNDER_RECOVERY =
        ZbColumnFamilies.DEPRECATED_DMN_DECISION_KEY_BY_DECISION_ID_AND_VERSION;
    private static final ZbColumnFamilies CF_POSSIBLE_TARGET = ZbColumnFamilies.MESSAGE_STATS;

    private final ColumnFamily<DbBytes, DbBytes> recoveryDmnDecisionKeyByDecisionIdAndVersion;

    private final DbString decisionId;
    private final DbInt decisionVersion;

    private final ColumnFamily<DbString, DbLong> messageStatsColumnFamily;
    private final DbString messagesDeadlineCountKey;
    private final DbLong messagesDeadlineCount;

    public DmnDecisionKeyByDecisionIdAndVersionColumnFamilyCorrector(
        final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
      decisionId = new DbString();
      decisionVersion = new DbInt();
      recoveryDmnDecisionKeyByDecisionIdAndVersion =
          zeebeDb.createColumnFamily(
              CF_UNDER_RECOVERY, transactionContext, new DbBytes(), new DbBytes());

      messagesDeadlineCountKey = new DbString();
      messagesDeadlineCount = new DbLong();
      messageStatsColumnFamily =
          zeebeDb.createColumnFamily(
              CF_POSSIBLE_TARGET,
              transactionContext,
              messagesDeadlineCountKey,
              messagesDeadlineCount);
    }

    public void correctColumnFamilyPrefix() {
      recoveryDmnDecisionKeyByDecisionIdAndVersion.forEach(
          (key, value) -> {
            if (!isKeyWithExpectedLength(key)) {
              // the key is not fitting to the expected data
              LOG.trace(
                  "Found invalid key [{}] (incorrect key length) in column family [{}] {}",
                  key,
                  CF_UNDER_RECOVERY.ordinal(),
                  CF_UNDER_RECOVERY.name());
              tryMoveDataToMessageStatsColumnFamily(key, value);
              return;
            }

            try {
              // so it appears the key fits the expected length, let's try to read it to ensure it
              // is a dmn decision key by decision id and version
              final DbCompositeKey<DbString, DbInt> dmnDecisionKeyByDecisionIdAndVersion =
                  new DbCompositeKey<>(decisionId, decisionVersion);
              dmnDecisionKeyByDecisionIdAndVersion.wrap(key.getDirectBuffer(), 0, key.getLength());
            } catch (final Exception e) {
              LOG.trace(
                  "Found invalid key [{}] (unable to read key) in column family [{}] {}",
                  key,
                  CF_UNDER_RECOVERY.ordinal(),
                  CF_UNDER_RECOVERY.name());
              tryMoveDataToMessageStatsColumnFamily(key, value);
              return;
            }

            // if we got here, the key is fitting to the expected length, and we're able to read it,
            // let's see if the data makes any sense.

            // we can verify the value, which must be a decision key
            final DbLong decisionKey = new DbLong();
            try {
              decisionKey.wrap(value.getDirectBuffer(), 0, value.getLength());
            } catch (final Exception e) {
              // we were not able to read the decision key, so it is likely that this is
              // actually not a decision key but something else
              LOG.trace(
                  "Found invalid value [{}] (unable to read value) in column family [{}] {}",
                  value,
                  CF_UNDER_RECOVERY.ordinal(),
                  CF_UNDER_RECOVERY.name());
              tryMoveDataToMessageStatsColumnFamily(key, value);
              return;
            }

            // Lastly, let's ensure we read the entire value by checking the length
            if (value.getLength() != decisionKey.getLength()) {
              // somehow there is still some data left in the value, let's assume this is not a
              // decision key
              LOG.trace(
                  "Found invalid value [{}] (incorrect value length) in column family [{}] {}",
                  value,
                  CF_UNDER_RECOVERY.ordinal(),
                  CF_UNDER_RECOVERY.name());
              tryMoveDataToMessageStatsColumnFamily(key, value);
              return;
            }

            // if we got here, both the key and the value appear to be fitting to the expected
            // format and the data appears to make any sense. We can assume that this is a decision
            // key and we can leave it as is.
            LOG.trace("Found valid decision key entry with key[{}] in recovery column family", key);
          });
    }

    /**
     * the key is supposed to be a composite key of decisionId and decisionVersion. decisionId is a
     * string, these are encoded as [length][bytes] where length is an int. decisionVersion is an
     * int, so it is encoded as [version] so the expected key is supposed to be
     * [length][bytes][version].
     *
     * <p>we can calculate the total expected length as:
     *
     * <ul>
     *   <li>4 bytes for the length of the string (number of bytes to store an int)
     *   <li>+ the length of the string
     *   <li>+ 4 bytes for the int (number of bytes to store an int)
     * </ul>
     *
     * if the key is shorter or longer than this, we can assume that it is not a key from the dmn
     * decision key by decision id and version column family.
     */
    private boolean isKeyWithExpectedLength(final DbBytes key) {
      final int stringLength = key.getDirectBuffer().getInt(0, ZB_DB_BYTE_ORDER);
      return key.getLength() == Integer.BYTES + stringLength + Integer.BYTES;
    }

    private void tryMoveDataToMessageStatsColumnFamily(final DbBytes key, final DbBytes value) {
      // Now we caught a case where the key is not fitting to the expected data
      // likely this is a key from MESSAGE_STATS, let's try to move it to the correct cf

      // we can start by attempting to read the messagesDeadlineCountKey and the
      // messagesDeadlineCount
      try {
        messagesDeadlineCountKey.wrap(key.getDirectBuffer(), 0, key.getLength());
        messagesDeadlineCount.wrap(value.getDirectBuffer(), 0, value.getLength());
      } catch (final Exception e) {
        // if we cannot read the messagesDeadlineCountKey or the messagesDeadlineCount, we cannot
        // move it to the correct column family, we're not sure what this data is though. Let's log
        // an error.
        final String reason = "unexpected data in column family";
        throw new ColumnFamilyCorrectionException(reason, key, value, CF_UNDER_RECOVERY, e);
      }

      // we should also verify the length of the key and value, to ensure we read the entire key
      // and value.
      if (key.getLength() != messagesDeadlineCountKey.getLength()) {
        throw new ColumnFamilyCorrectionException(
            "incorrect key length", key, value, CF_UNDER_RECOVERY, null);
      }

      if (value.getLength() != messagesDeadlineCount.getLength()) {
        throw new ColumnFamilyCorrectionException(
            "incorrect value length", key, value, CF_UNDER_RECOVERY, null);
      }

      // the messagesDeadlineCountKey is a hardcoded value DbMessageState.DEADLINE_MESSAGE_COUNT_KEY
      // so we can verify that the key is correct
      if (!DbMessageState.DEADLINE_MESSAGE_COUNT_KEY.equals(messagesDeadlineCountKey.toString())) {
        throw new ColumnFamilyCorrectionException(
            "incorrect key value", key, value, CF_UNDER_RECOVERY, null);
      }

      // it could be that there are already stats known for this key, in that case we don't want to
      // override it, but merge the values. For a count we can do so by simply adding the values.

      final var currentCount = messageStatsColumnFamily.get(messagesDeadlineCountKey);
      if (currentCount != null) {
        LOG.trace(
            "Found existing message stats entry with key [{}] and value [{}]", key, currentCount);
        messagesDeadlineCount.wrapLong(messagesDeadlineCount.getValue() + currentCount.getValue());
      }

      moveEntryFromRecoveryColumnFamilyToMessageStatsColumnFamily(
          key, messagesDeadlineCountKey, messagesDeadlineCount);
    }

    private void moveEntryFromRecoveryColumnFamilyToMessageStatsColumnFamily(
        final DbBytes key,
        final DbString messagesDeadlineCountKey,
        final DbLong messagesDeadlineCount) {
      LOG.debug(
          "Copying entry with key[{}] and value [{}] from column family [{}] {} to column family [{}] {}",
          key,
          messagesDeadlineCount,
          CF_UNDER_RECOVERY.ordinal(),
          CF_UNDER_RECOVERY.name(),
          CF_POSSIBLE_TARGET.ordinal(),
          CF_POSSIBLE_TARGET.name());
      messageStatsColumnFamily.upsert(messagesDeadlineCountKey, messagesDeadlineCount);
      deleteEntryFromRecoveryColumnFamily(key);
    }

    private void deleteEntryFromRecoveryColumnFamily(final DbBytes key) {
      LOG.debug(
          "Deleting entry with key[{}] from column family [{}] {}",
          key,
          CF_UNDER_RECOVERY.ordinal(),
          CF_UNDER_RECOVERY.name());
      recoveryDmnDecisionKeyByDecisionIdAndVersion.deleteExisting(key);
    }
  }

  private static final class ColumnFamilyCorrectionException extends RuntimeException {
    public ColumnFamilyCorrectionException(
        final String reason,
        final DbBytes key,
        final DbBytes value,
        final ZbColumnFamilies columnFamily,
        final Throwable cause) {
      super(
          String.format(
              "Failed to correct prefix of column family [%d] %s, due to %s - key[%s], value[%s]",
              columnFamily.ordinal(),
              columnFamily.name(),
              reason,
              BufferUtil.bufferAsHexString(key.getDirectBuffer()),
              BufferUtil.bufferAsHexString(value.getDirectBuffer())),
          cause);
    }
  }
}
