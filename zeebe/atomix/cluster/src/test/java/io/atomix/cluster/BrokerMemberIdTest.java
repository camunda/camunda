/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class BrokerMemberIdTest {

  @Test
  void shouldCreateBrokerMemberIdFromNodeId() {
    // given
    final var brokerMemberId = BrokerMemberId.from(7);

    // then
    assertThat(brokerMemberId)
        .returns(7, BrokerMemberId::nodeIdx)
        .returns(null, BrokerMemberId::zone)
        .returns("7", BrokerMemberId::id);
  }

  @Test
  void shouldCreateBrokerMemberIdFromZoneAndNodeId() {
    // given
    final var brokerMemberId = BrokerMemberId.from("eu-west", 7);

    // then
    assertThat(brokerMemberId)
        .returns(7, BrokerMemberId::nodeIdx)
        .returns("eu-west", BrokerMemberId::zone)
        .returns("eu-west/7", BrokerMemberId::id);
  }

  @Test
  void shouldCreateBrokerMemberIdFromMemberId() {
    // given
    final var memberId = MemberId.from("eu-west/7");

    // when
    final var brokerMemberId = BrokerMemberId.from(memberId);

    // then
    assertThat(brokerMemberId)
        .returns(7, BrokerMemberId::nodeIdx)
        .returns("eu-west", BrokerMemberId::zone)
        .returns("eu-west/7", BrokerMemberId::id);
  }

  @Test
  void shouldRejectNonBrokerMemberId() {
    // given
    final var memberId = MemberId.from("backup");

    // expect
    assertThatThrownBy(() -> BrokerMemberId.from(memberId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("to represent a broker");
  }
}
