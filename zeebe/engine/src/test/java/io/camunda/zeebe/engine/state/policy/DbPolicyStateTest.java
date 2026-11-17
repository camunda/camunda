/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.policy;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.immutable.PolicyState;
import io.camunda.zeebe.engine.state.immutable.PolicyState.CommittedPolicy;
import io.camunda.zeebe.engine.state.mutable.MutablePolicyState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbPolicyStateTest {

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
  void shouldReplaceThePreviouslyCommittedPolicy() {
    // given
    policyState.commit("policy", 1L, 10L);

    // when
    policyState.commit("other-policy", 5L, 50L);

    // then
    assertThat(policyState.getCurrentPolicy())
        .contains(new CommittedPolicy("other-policy", 5L, 50L));
    assertThat(policyState.getCurrentPolicyVersion()).isEqualTo(5L);
  }
}
