/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.group;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableGroupState;
import io.camunda.zeebe.engine.state.mutable.MutablePolicyUpdateState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
final class DbGroupStateVersionTest {

  private MutableProcessingState processingState;
  private MutableGroupState groupState;
  private MutablePolicyUpdateState policyUpdateState;

  @BeforeEach
  void setup() {
    groupState = processingState.getGroupState();
    policyUpdateState = processingState.getPolicyUpdateState();
  }

  @Test
  void shouldReadGroupVisibleToTheCommandVersion() {
    // given
    groupState.create(group("g", "A", 0));
    groupState.update(group("g", "B", 2));

    // then
    assertThat(groupState.get("g", 2))
        .isPresent()
        .get()
        .satisfies(persisted -> assertThat(persisted.getName()).isEqualTo("B"));
    assertThat(groupState.get("g"))
        .isPresent()
        .get()
        .satisfies(persisted -> assertThat(persisted.getName()).isEqualTo("A"));

    // when
    policyUpdateState.setCurrentVersion(2);

    // then
    assertThat(groupState.get("g"))
        .isPresent()
        .get()
        .satisfies(persisted -> assertThat(persisted.getName()).isEqualTo("B"));
  }

  @Test
  void shouldHideGroupDeletedAtCurrentVersion() {
    // given
    groupState.create(group("g", "A", 0));

    // when
    groupState.delete(group("g", "A", 1));
    policyUpdateState.setCurrentVersion(1);

    // then
    assertThat(groupState.get("g")).isEmpty();
  }

  @Test
  void shouldFindGroupsByNameOfTheVisibleVersionOnly() {
    // given
    groupState.create(group("g1", "shared", 0));
    assertThat(groupState.findByIdOrName("shared")).hasSize(1);

    // when: a pending group with the same name is written after the cache was filled
    groupState.create(group("g2", "shared", 3));

    // then: a user command does not see it, and a command of version 3 does
    assertThat(groupState.findByIdOrName("shared")).hasSize(1);
    assertThat(groupState.findByIdOrName("shared", 3)).hasSize(2);
  }

  private static GroupRecord group(final String id, final String name, final long version) {
    return new GroupRecord().setGroupId(id).setName(name).setPolicyVersion(version);
  }
}
