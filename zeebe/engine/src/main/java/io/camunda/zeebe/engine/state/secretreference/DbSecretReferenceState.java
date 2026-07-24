/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.secretreference;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.LongPredicate;

public final class DbSecretReferenceState implements MutableSecretReferenceState {

  private final DbString storeId;
  private final DbString secretReference;
  private final DbCompositeKey<DbString, DbString> storeIdAndSecretReference;

  // pending secret references: (storeId, secretReference) → ∅
  private final ColumnFamily<DbCompositeKey<DbString, DbString>, DbNil>
      pendingSecretReferencesColumnFamily;

  private final DbLong jobKey;
  private final DbForeignKey<DbCompositeKey<DbString, DbString>> fkStoreIdAndSecretReference;

  // secondary index: (jobKey, storeId, secretReference) → ∅; supports prefix iteration by job key
  private final DbCompositeKey<DbLong, DbForeignKey<DbCompositeKey<DbString, DbString>>>
      jobKeyAndSecretRef;
  private final ColumnFamily<
          DbCompositeKey<DbLong, DbForeignKey<DbCompositeKey<DbString, DbString>>>, DbNil>
      waitingJobsByJobKeyColumnFamily;

  // secondary index: (storeId, secretReference, jobKey) → ∅; supports prefix iteration by
  // (storeId, secretReference)
  private final DbCompositeKey<DbForeignKey<DbCompositeKey<DbString, DbString>>, DbLong>
      secretRefAndJobKey;
  private final ColumnFamily<
          DbCompositeKey<DbForeignKey<DbCompositeKey<DbString, DbString>>, DbLong>, DbNil>
      waitingJobsBySecretRefColumnFamily;

  public DbSecretReferenceState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    storeId = new DbString();
    secretReference = new DbString();
    storeIdAndSecretReference = new DbCompositeKey<>(storeId, secretReference);

    pendingSecretReferencesColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PENDING_SECRET_REFERENCES,
            transactionContext,
            storeIdAndSecretReference,
            DbNil.INSTANCE);

    jobKey = new DbLong();
    fkStoreIdAndSecretReference =
        new DbForeignKey<>(storeIdAndSecretReference, ZbColumnFamilies.PENDING_SECRET_REFERENCES);

    jobKeyAndSecretRef = new DbCompositeKey<>(jobKey, fkStoreIdAndSecretReference);
    waitingJobsByJobKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.SECRET_REFERENCES_BY_JOB,
            transactionContext,
            jobKeyAndSecretRef,
            DbNil.INSTANCE);

    secretRefAndJobKey = new DbCompositeKey<>(fkStoreIdAndSecretReference, jobKey);
    waitingJobsBySecretRefColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOBS_BY_SECRET_REFERENCE,
            transactionContext,
            secretRefAndJobKey,
            DbNil.INSTANCE);
  }

  @Override
  public boolean isPending(final String storeId, final String secretReference) {
    this.storeId.wrapString(storeId);
    this.secretReference.wrapString(secretReference);
    return pendingSecretReferencesColumnFamily.exists(storeIdAndSecretReference);
  }

  @Override
  public void visitJobsBySecretReference(
      final String storeId, final String secretReference, final LongPredicate visitor) {
    this.storeId.wrapString(storeId);
    this.secretReference.wrapString(secretReference);
    final KeyValuePairVisitor<
            DbCompositeKey<DbForeignKey<DbCompositeKey<DbString, DbString>>, DbLong>, DbNil>
        kvVisitor = (key, value) -> visitor.test(key.second().getValue());
    waitingJobsBySecretRefColumnFamily.whileEqualPrefix(fkStoreIdAndSecretReference, kvVisitor);
  }

  @Override
  public void visitSecretReferencesByJob(
      final long jobKey, final BiPredicate<String, String> visitor) {
    this.jobKey.wrapLong(jobKey);
    final KeyValuePairVisitor<
            DbCompositeKey<DbLong, DbForeignKey<DbCompositeKey<DbString, DbString>>>, DbNil>
        kvVisitor =
            (key, value) ->
                visitor.test(
                    key.second().inner().first().toString(),
                    key.second().inner().second().toString());
    waitingJobsByJobKeyColumnFamily.whileEqualPrefix(this.jobKey, kvVisitor);
  }

  @Override
  public void visitPendingSecretReferences(final BiConsumer<String, String> visitor) {
    pendingSecretReferencesColumnFamily.forEach(
        (key, value) -> visitor.accept(key.first().toString(), key.second().toString()));
  }

  @Override
  public void addPendingSecretReference(final String storeId, final String secretReference) {
    this.storeId.wrapString(storeId);
    this.secretReference.wrapString(secretReference);
    pendingSecretReferencesColumnFamily.upsert(storeIdAndSecretReference, DbNil.INSTANCE);
  }

  @Override
  public void removePendingSecretReference(final String storeId, final String secretReference) {
    this.storeId.wrapString(storeId);
    this.secretReference.wrapString(secretReference);
    pendingSecretReferencesColumnFamily.deleteExisting(storeIdAndSecretReference);
  }

  @Override
  public void addWaitingJob(final String storeId, final String secretReference, final long jobKey) {
    this.storeId.wrapString(storeId);
    this.secretReference.wrapString(secretReference);
    this.jobKey.wrapLong(jobKey);
    waitingJobsByJobKeyColumnFamily.upsert(jobKeyAndSecretRef, DbNil.INSTANCE);
    waitingJobsBySecretRefColumnFamily.upsert(secretRefAndJobKey, DbNil.INSTANCE);
  }

  @Override
  public void removeWaitingJob(
      final String storeId, final String secretReference, final long jobKey) {
    this.storeId.wrapString(storeId);
    this.secretReference.wrapString(secretReference);
    this.jobKey.wrapLong(jobKey);
    waitingJobsByJobKeyColumnFamily.deleteIfExists(jobKeyAndSecretRef);
    waitingJobsBySecretRefColumnFamily.deleteIfExists(secretRefAndJobKey);
  }
}
