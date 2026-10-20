/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutablePolicyUpdateState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableRoleState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbRoleStateVersionTest {

  private MutableProcessingState processingState;
  private MutableRoleState roleState;
  private MutablePolicyUpdateState policyUpdateState;

  @BeforeEach
  void setup() {
    roleState = processingState.getRoleState();
    policyUpdateState = processingState.getPolicyUpdateState();
  }

  @Test
  void shouldReadRoleWrittenWithoutPolicyUpdate() {
    // when
    roleState.create(role("r", "A", 0));

    // then
    assertThat(roleState.getRole("r"))
        .isPresent()
        .get()
        .satisfies(persisted -> assertThat(persisted.getName()).isEqualTo("A"));
  }

  @Test
  void shouldHidePendingVersionUntilCurrentVersionReachesIt() {
    // given
    roleState.create(role("r", "A", 0));

    // when
    roleState.update(role("r", "B", 2));

    // then
    assertThat(roleState.getRole("r"))
        .isPresent()
        .get()
        .satisfies(persisted -> assertThat(persisted.getName()).isEqualTo("A"));

    // when
    policyUpdateState.setCurrentVersion(2);

    // then
    assertThat(roleState.getRole("r"))
        .isPresent()
        .get()
        .satisfies(persisted -> assertThat(persisted.getName()).isEqualTo("B"));
  }

  @Test
  void shouldLetPolicyVersionReadItsOwnPendingWrite() {
    // given
    roleState.create(role("r", "A", 0));
    roleState.update(role("r", "B", 2));

    // then: a command of version 2 sees its own write, while a command outside of a policy update
    // still sees the current version
    assertThat(roleState.getRole("r", 2))
        .isPresent()
        .get()
        .satisfies(persisted -> assertThat(persisted.getName()).isEqualTo("B"));
    assertThat(roleState.getRole("r", 0))
        .isPresent()
        .get()
        .satisfies(persisted -> assertThat(persisted.getName()).isEqualTo("A"));
  }

  @Test
  void shouldHideRoleDeletedAtCurrentVersion() {
    // given
    roleState.create(role("r", "A", 0));

    // when
    roleState.delete(role("r", "A", 1));
    policyUpdateState.setCurrentVersion(1);

    // then
    assertThat(roleState.getRole("r")).isEmpty();
  }

  @Test
  void shouldNotConfuseRoleIdsThatArePrefixesOfEachOther() {
    // given
    roleState.create(role("a", "A", 0));
    roleState.create(role("ab", "AB", 0));
    roleState.update(role("a", "A2", 3));
    policyUpdateState.setCurrentVersion(1);

    // then
    assertThat(roleState.getRole("a"))
        .isPresent()
        .get()
        .satisfies(persisted -> assertThat(persisted.getName()).isEqualTo("A"));
    assertThat(roleState.getRole("ab"))
        .isPresent()
        .get()
        .satisfies(persisted -> assertThat(persisted.getName()).isEqualTo("AB"));
  }

  private static RoleRecord role(final String id, final String name, final long version) {
    return new RoleRecord().setRoleId(id).setName(name).setPolicyVersion(version);
  }
}
