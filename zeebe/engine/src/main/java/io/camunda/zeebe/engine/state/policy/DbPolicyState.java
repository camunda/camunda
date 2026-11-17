/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.policy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutablePolicyState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps the committed policy as a single row under a constant key, so that the frequent question
 * "what is my current policy version" is a point read.
 */
@NullMarked
public final class DbPolicyState implements MutablePolicyState {

  private static final String CURRENT = "CURRENT";

  private final DbString key = new DbString();
  private final PersistedPolicy policy = new PersistedPolicy();
  private final ColumnFamily<DbString, PersistedPolicy> policyColumnFamily;

  public DbPolicyState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    policyColumnFamily =
        zeebeDb.createColumnFamily(ZbColumnFamilies.POLICY, transactionContext, key, policy);
  }

  @Override
  public Optional<CommittedPolicy> getCurrentPolicy() {
    key.wrapString(CURRENT);
    final var stored = policyColumnFamily.get(key);
    if (stored == null) {
      return Optional.empty();
    }
    return Optional.of(
        new CommittedPolicy(stored.getPolicyId(), stored.getPolicyVersion(), stored.getSeqNo()));
  }

  @Override
  public long getCurrentPolicyVersion() {
    key.wrapString(CURRENT);
    final var stored = policyColumnFamily.get(key);
    return stored == null ? NO_POLICY_VERSION : stored.getPolicyVersion();
  }

  @Override
  public void commit(final String policyId, final long policyVersion, final long seqNo) {
    policy.setPolicyId(policyId).setPolicyVersion(policyVersion).setSeqNo(seqNo);
    key.wrapString(CURRENT);
    policyColumnFamily.upsert(key, policy);
  }
}
