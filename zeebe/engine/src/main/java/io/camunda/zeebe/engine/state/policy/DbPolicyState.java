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
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.mutable.MutablePolicyState;
import io.camunda.zeebe.engine.state.policy.PersistedPolicy.PolicyStatus;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.ArrayList;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps the policy versions keyed by version, so that the current policy is the first committed row
 * of a reverse scan. Usually there is one committed version and at most one pending one.
 */
@NullMarked
public final class DbPolicyState implements MutablePolicyState {

  private final DbLong policyVersion = new DbLong();
  private final PersistedPolicy policy = new PersistedPolicy();
  private final ColumnFamily<DbLong, PersistedPolicy> policyColumnFamily;

  public DbPolicyState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    policyColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.POLICY, transactionContext, policyVersion, policy);
  }

  @Override
  public Optional<CommittedPolicy> getCurrentPolicy() {
    final var current = new CommittedPolicy[1];
    policyVersion.wrapLong(Long.MAX_VALUE);
    policyColumnFamily.whileTrueReverse(
        policyVersion,
        (version, stored) -> {
          if (stored.getStatus() != PolicyStatus.COMMITTED) {
            return true;
          }
          current[0] =
              new CommittedPolicy(
                  stored.getPolicyId(), stored.getPolicyVersion(), stored.getSeqNo());
          return false;
        });
    return Optional.ofNullable(current[0]);
  }

  @Override
  public long getCurrentPolicyVersion() {
    return getCurrentPolicy().map(CommittedPolicy::policyVersion).orElse(NO_POLICY_VERSION);
  }

  @Override
  public void commit(final String policyId, final long version, final long seqNo) {
    final var olderVersions = new ArrayList<Long>();
    policyColumnFamily.whileTrue(
        (key, stored) -> {
          if (key.getValue() >= version) {
            return false;
          }
          olderVersions.add(key.getValue());
          return true;
        });
    for (final long olderVersion : olderVersions) {
      policyVersion.wrapLong(olderVersion);
      policyColumnFamily.deleteExisting(policyVersion);
    }

    policyVersion.wrapLong(version);
    policy
        .setPolicyId(policyId)
        .setPolicyVersion(version)
        .setSeqNo(seqNo)
        .setStatus(PolicyStatus.COMMITTED);
    policyColumnFamily.upsert(policyVersion, policy);
  }
}
