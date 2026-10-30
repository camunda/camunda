/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.consent;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.immutable.ConsentState;
import io.camunda.zeebe.engine.state.mutable.MutableConsentState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.consent.ConsentRecord;
import java.util.Optional;
import java.util.function.LongPredicate;

public final class DbConsentState implements MutableConsentState {

  private final DbString grantId = new DbString();
  private final PersistedConsent persistedConsent = new PersistedConsent();
  // grantId -> the live grant
  private final ColumnFamily<DbString, PersistedConsent> grantsColumnFamily;

  private final DbLong consentKey = new DbLong();
  // consentKey -> grantId
  private final ColumnFamily<DbLong, DbString> grantIdsByKeyColumnFamily;

  private final DbLong processInstanceKey = new DbLong();
  private final DbCompositeKey<DbLong, DbString> approvalKey =
      new DbCompositeKey<>(processInstanceKey, grantId);
  // (processInstanceKey, grantId) -> the approval
  private final ColumnFamily<DbCompositeKey<DbLong, DbString>, PersistedConsent>
      approvalsColumnFamily;

  private final DbLong jobKey = new DbLong();
  private final DbCompositeKey<DbString, DbLong> parkedJobKey =
      new DbCompositeKey<>(grantId, jobKey);
  // (grantId, jobKey) -> ∅
  private final ColumnFamily<DbCompositeKey<DbString, DbLong>, DbNil> parkedJobsColumnFamily;
  // jobKey -> grantId
  private final ColumnFamily<DbLong, DbString> parkedJobGrantIdsColumnFamily;
  private final DbString parkedGrantId = new DbString();

  public DbConsentState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    grantsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CONSENT_GRANTS, transactionContext, grantId, persistedConsent);
    grantIdsByKeyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CONSENT_GRANT_IDS_BY_KEY,
            transactionContext,
            consentKey,
            new DbString());
    approvalsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CONSENT_APPROVALS,
            transactionContext,
            approvalKey,
            new PersistedConsent());
    parkedJobsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CONSENT_PARKED_JOBS, transactionContext, parkedJobKey, DbNil.INSTANCE);
    parkedJobGrantIdsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.CONSENT_PARKED_JOB_GRANT_IDS,
            transactionContext,
            jobKey,
            parkedGrantId);
  }

  @Override
  public Optional<ConsentRecord> getGrant(
      final String subject, final String connection, final String operation) {
    grantId.wrapString(ConsentState.grantId(subject, connection, operation));
    return Optional.ofNullable(grantsColumnFamily.get(grantId))
        .map(PersistedConsent::getRecord)
        .map(DbConsentState::copyOf);
  }

  @Override
  public Optional<ConsentRecord> getGrantByKey(final long key) {
    consentKey.wrapLong(key);
    final DbString id = grantIdsByKeyColumnFamily.get(consentKey);
    if (id == null) {
      return Optional.empty();
    }
    grantId.wrapString(id.toString());
    return Optional.ofNullable(grantsColumnFamily.get(grantId))
        .map(PersistedConsent::getRecord)
        .filter(grant -> grant.getConsentKey() == key)
        .map(DbConsentState::copyOf);
  }

  @Override
  public Optional<ConsentRecord> getApproval(
      final long processInstance,
      final String subject,
      final String connection,
      final String operation) {
    processInstanceKey.wrapLong(processInstance);
    grantId.wrapString(ConsentState.grantId(subject, connection, operation));
    return Optional.ofNullable(approvalsColumnFamily.get(approvalKey))
        .map(PersistedConsent::getRecord)
        .map(DbConsentState::copyOf);
  }

  @Override
  public void visitParkedJobs(
      final String subject,
      final String connection,
      final String operation,
      final long afterJobKey,
      final LongPredicate visitor) {
    final String id = ConsentState.grantId(subject, connection, operation);
    grantId.wrapString(id);
    parkedJobsColumnFamily.whileEqualPrefix(
        grantId,
        (key, value) -> {
          final long parked = key.second().getValue();
          return parked <= afterJobKey || visitor.test(parked);
        });
  }

  private static ConsentRecord copyOf(final ConsentRecord record) {
    final var copy = new ConsentRecord();
    copy.copyFrom(record);
    return copy;
  }

  @Override
  public boolean isParked(final long key) {
    jobKey.wrapLong(key);
    return parkedJobGrantIdsColumnFamily.exists(jobKey);
  }

  @Override
  public void putGrant(final ConsentRecord grant) {
    grantId.wrapString(
        ConsentState.grantId(grant.getSubject(), grant.getConnection(), grant.getOperation()));
    grantsColumnFamily.upsert(grantId, persistedConsent.setRecord(grant));
    consentKey.wrapLong(grant.getConsentKey());
    grantIdsByKeyColumnFamily.upsert(consentKey, grantId);
  }

  @Override
  public void removeGrant(final long key) {
    consentKey.wrapLong(key);
    final DbString id = grantIdsByKeyColumnFamily.get(consentKey);
    if (id == null) {
      return;
    }
    grantId.wrapString(id.toString());
    final var grant = grantsColumnFamily.get(grantId);
    if (grant != null && grant.getRecord().getConsentKey() == key) {
      grantsColumnFamily.deleteExisting(grantId);
    }
    grantIdsByKeyColumnFamily.deleteExisting(consentKey);
  }

  @Override
  public void putApproval(final ConsentRecord approval) {
    processInstanceKey.wrapLong(approval.getProcessInstanceKey());
    grantId.wrapString(
        ConsentState.grantId(
            approval.getSubject(), approval.getConnection(), approval.getOperation()));
    approvalsColumnFamily.upsert(approvalKey, new PersistedConsent().setRecord(approval));
  }

  @Override
  public void addParkedJob(
      final String subject, final String connection, final String operation, final long key) {
    grantId.wrapString(ConsentState.grantId(subject, connection, operation));
    jobKey.wrapLong(key);
    parkedJobsColumnFamily.upsert(parkedJobKey, DbNil.INSTANCE);
    parkedJobGrantIdsColumnFamily.upsert(jobKey, grantId);
  }

  @Override
  public void removeParkedJob(final long key) {
    jobKey.wrapLong(key);
    final DbString id = parkedJobGrantIdsColumnFamily.get(jobKey);
    if (id == null) {
      return;
    }
    grantId.wrapString(id.toString());
    parkedJobsColumnFamily.deleteIfExists(parkedJobKey);
    parkedJobGrantIdsColumnFamily.deleteExisting(jobKey);
  }
}
