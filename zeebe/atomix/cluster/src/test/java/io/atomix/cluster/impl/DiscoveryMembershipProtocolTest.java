/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.atomix.cluster.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.Member;
import io.atomix.utils.net.Address;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class DiscoveryMembershipProtocolTest {

  @Test
  void shouldFindMemberByAddress() {
    // given
    final var member0 = Member.member("0", "localhost:5000");
    final var member1 = Member.member("1", "localhost:5001");
    final var protocol =
        new DiscoveryMembershipProtocol(Map.of(member0.id(), member0, member1.id(), member1));

    // when
    final var found = protocol.getMember(member1.address());

    // then
    assertThat(found).isEqualTo(member1);
  }

  @Test
  void shouldReturnNullForUnknownAddress() {
    // given
    final var member0 = Member.member("0", "localhost:5000");
    final var protocol = new DiscoveryMembershipProtocol(Map.of(member0.id(), member0));

    // when
    final var unknown = protocol.getMember(new Address("localhost", 5999));

    // then
    assertThat(unknown).isNull();
  }
}
