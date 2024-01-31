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
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.ZeebeDbConstants;
import io.camunda.zeebe.engine.state.migration.DbMigratorImpl;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to correct the column family prefix of the Dmn Decision Requirements Key By
 * Decision Requirement Id And Version column family, which may contain entries for the Process
 * Instance Key by Definition Key column family. Vice-versa correction is not needed because no data
 * was written wrongly to the Process Instance Key by Definition Key column family.
 *
 * <p>Correction: {@link
 * ZbColumnFamilies#DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION} -> {@link
 * ZbColumnFamilies#PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY}
 */
@SuppressWarnings("deprecation") // deals with deprecated column families
public class ColumnFamily49Corrector {

  private static final Logger LOG = LoggerFactory.getLogger(DbMigratorImpl.class.getPackageName());

  private static final ZbColumnFamilies CF_UNDER_RECOVERY =
      ZbColumnFamilies.DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION;
  private static final ZbColumnFamilies CF_POSSIBLE_TARGET =
      ZbColumnFamilies.PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY;

  private final ColumnFamily<DbBytes, DbBytes> recoverColumnFamily;

  private final ColumnFamily<DbCompositeKey<DbLong, DbLong>, DbNil>
      processInstanceKeyByProcessDefinitionKeyColumnFamily;
  private final DbCompositeKey<DbLong, DbLong> processInstanceKeyByProcessDefinitionKey;
  private final DbLong processDefinitionKey;
  private final DbLong elementInstanceKey;

  public ColumnFamily49Corrector(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    recoverColumnFamily =
        zeebeDb.createColumnFamily(
            CF_UNDER_RECOVERY, transactionContext, new DbBytes(), new DbBytes());

    processDefinitionKey = new DbLong();
    elementInstanceKey = new DbLong();
    processInstanceKeyByProcessDefinitionKey =
        new DbCompositeKey<>(processDefinitionKey, elementInstanceKey);
    processInstanceKeyByProcessDefinitionKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_INSTANCE_KEY_BY_DEFINITION_KEY,
            transactionContext,
            processInstanceKeyByProcessDefinitionKey,
            DbNil.INSTANCE);
  }

  public void correctColumnFamilyPrefix() {
    recoverColumnFamily.forEach(
        (key, value) -> {
          if (!isKeyWithExpectedLength(key)) {
            // the key is not fitting to the expected data
            LOG.trace(
                "Found invalid key [{}] (incorrect key length) in column family [{}] {}",
                key,
                CF_UNDER_RECOVERY.ordinal(),
                CF_UNDER_RECOVERY.name());
            tryMoveDataToCorrectColumnFamily(key, value);
            return;
          }

          try {
            // so it appears the key fits the expected length, let's try to read it to ensure it
            // is a decision requirement id and version
            processInstanceKeyByProcessDefinitionKey.wrap(
                key.getDirectBuffer(), 0, key.getLength());
          } catch (final Exception e) {
            LOG.trace(
                "Found invalid key [{}] (unable to read key) in column family [{}] {}",
                key,
                CF_UNDER_RECOVERY.ordinal(),
                CF_UNDER_RECOVERY.name());
            tryMoveDataToCorrectColumnFamily(key, value);
          }

          // if we got here, the key is fitting to the expected length, and we're able to read it,
          // let's see if the data makes any sense.

          // the value is supposed to be the dmn decision requirements key, which is a long.
          // we can check if the value is a long by checking its length (longs are 8 bytes).
          if (value.getLength() != Long.BYTES) {
            LOG.trace(
                "Found invalid value [{}] (incorrect value length) in column family [{}] {}",
                value,
                CF_UNDER_RECOVERY.ordinal(),
                CF_UNDER_RECOVERY.name());
            tryMoveDataToCorrectColumnFamily(key, value);
            return;
          }

          // if we got here, both the key and the value appear to be fitting to the expected
          // format and the data appears to make any sense. We can assume that this is a dmn
          // decision requirements key and we can leave it as is.
          LOG.trace(
              "Found valid dmn requirements key entry with key[{}] in recovery column family", key);
        });
  }

  /**
   * The key is supposed to be a composite key of a decision requirement id and a version. The
   * decision requirement id is a string, these are encoded as [length][bytes] where length is an
   * int. version is an int. So the key is supposed to be [int][bytes][int].
   *
   * <p>We can calculate the total expected length as:
   *
   * <ul>
   *   <li>4 bytes for the length of the string (number of bytes to store an int)
   *   <li>+ the length of the string
   *   <li>+ 4 bytes for the int (number of bytes to store a int)
   * </ul>
   *
   * if the key is shorter or longer than this, we can assume that it is not a key from the
   * DmnDecisionRequirementsKeyByDecisionRequirementIdAndVersion column family.
   */
  private boolean isKeyWithExpectedLength(final DbBytes key) {
    final int stringLength = key.getDirectBuffer().getInt(0, ZeebeDbConstants.ZB_DB_BYTE_ORDER);
    return key.getLength() == Integer.BYTES + stringLength + Integer.BYTES;
  }

  private void tryMoveDataToCorrectColumnFamily(final DbBytes key, final DbBytes value) {
    // Now we caught a case where the key is not fitting to the expected data
    // likely this is a key from the process instance key by definition key column family.
    // let's try to move the data to the correct column family

    // we can start by attempting to read the key as a composite key of a process definition key
    // and a process instance key (element instance key).
    try {
      processInstanceKeyByProcessDefinitionKey.wrap(key.getDirectBuffer(), 0, key.getLength());
    } catch (final Exception e) {
      // if we cannot read the process instance key by process definition key, we cannot move it
      // to the correct column family, we're not sure what this data is though. Let's throw an
      // error.
      final String reason = "unexpected data in column family";
      throw new ColumnFamilyCorrectionException(reason, key, value, CF_UNDER_RECOVERY, e);
    }

    // We can also try to read the value as a nil. We can do so, by checking the length of it.
    if (value.getLength() != DbNil.INSTANCE.getLength()) {
      // if the value is not a nil, we cannot move it to the correct column family, we're not sure
      // what this data is though. Let's throw an error.
      final String reason = "unexpected data in column family";
      throw new ColumnFamilyCorrectionException(reason, key, value, CF_UNDER_RECOVERY);
    }

    moveEntryFromRecoveryColumnFamilyToCorrectColumnFamily(
        key, processInstanceKeyByProcessDefinitionKey);
  }

  private void moveEntryFromRecoveryColumnFamilyToCorrectColumnFamily(
      final DbBytes key,
      final DbCompositeKey<DbLong, DbLong> processInstanceKeyByProcessDefinitionKey) {
    LOG.debug(
        "Copying entry with key [{}] from column family [{}] {} to column family [{}] {}",
        key,
        CF_UNDER_RECOVERY.ordinal(),
        CF_UNDER_RECOVERY.name(),
        CF_POSSIBLE_TARGET.ordinal(),
        CF_POSSIBLE_TARGET.name());
    processInstanceKeyByProcessDefinitionKeyColumnFamily.upsert(
        processInstanceKeyByProcessDefinitionKey, DbNil.INSTANCE);
    deleteEntryFromRecoveryColumnFamily(key);
  }

  private void deleteEntryFromRecoveryColumnFamily(final DbBytes key) {
    LOG.debug(
        "Deleting entry with key[{}] from column family [{}] {}",
        key,
        CF_UNDER_RECOVERY.ordinal(),
        CF_UNDER_RECOVERY.name());
    recoverColumnFamily.deleteExisting(key);
  }
}
