/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.state.immutable.PolicyState;
import io.camunda.zeebe.engine.state.immutable.PolicyState.CommittedPolicy;
import io.camunda.zeebe.engine.state.mutable.MutablePolicyState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.policy.PersistedPolicy.PolicyStatus;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbPolicyStateTest {

  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;
  private MutableProcessingState processingState;
  private MutablePolicyState policyState;

  @BeforeEach
  void setUp() {
    policyState = processingState.getPolicyState();
  }

  @Test
  void shouldHaveNoCurrentPolicyBeforeTheFirstCommit() {
    // when
    final var current = policyState.getCurrentPolicy();

    // then
    assertThat(current).isEmpty();
    assertThat(policyState.getCurrentPolicyVersion()).isEqualTo(PolicyState.NO_POLICY_VERSION);
  }

  @Test
  void shouldReturnTheCommittedPolicy() {
    // when
    policyState.commit("policy", 3L, 42L);

    // then
    assertThat(policyState.getCurrentPolicy()).contains(new CommittedPolicy("policy", 3L, 42L));
    assertThat(policyState.getCurrentPolicyVersion()).isEqualTo(3L);
  }

  @Test
  void shouldKeepOnlyTheNewestCommittedVersion() {
    // given
    policyState.commit("policy", 1L, 10L);
    policyState.commit("policy", 2L, 20L);

    // when
    policyState.commit("policy", 5L, 50L);

    // then
    assertThat(policyState.getCurrentPolicy()).contains(new CommittedPolicy("policy", 5L, 50L));
    assertThat(storedVersions()).containsExactly(5L);
  }

  @Test
  void shouldNotTreatAPendingNewerVersionAsCurrent() {
    // given
    policyState.commit("policy", 1L, 10L);
    storePending(2L);

    // when
    final var current = policyState.getCurrentPolicyVersion();

    // then
    assertThat(current).isEqualTo(1L);
  }

  @Test
  void shouldKeepAPendingNewerVersionOnCommit() {
    // given
    storePending(3L);

    // when
    policyState.commit("policy", 2L, 20L);

    // then
    assertThat(storedVersions()).containsExactly(2L, 3L);
  }

  /** There is no API for pending versions yet; buffering a policy update will add it. */
  private void storePending(final long version) {
    final var key = new DbLong();
    key.wrapLong(version);
    policyColumnFamily(key)
        .insert(
            key,
            new PersistedPolicy()
                .setPolicyId("policy")
                .setPolicyVersion(version)
                .setStatus(PolicyStatus.PENDING));
  }

  private List<Long> storedVersions() {
    final var versions = new ArrayList<Long>();
    policyColumnFamily(new DbLong()).forEachKey(key -> versions.add(key.getValue()));
    return versions;
  }

  private ColumnFamily<DbLong, PersistedPolicy> policyColumnFamily(final DbLong key) {
    return zeebeDb.createColumnFamily(
        ZbColumnFamilies.POLICY, transactionContext, key, new PersistedPolicy());
  }
}
