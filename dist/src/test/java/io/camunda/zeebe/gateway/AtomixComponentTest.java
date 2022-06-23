/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.utils.net.Address;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class AtomixComponentTest {
  private final GatewayCfg config = new GatewayCfg();
  private final AtomixComponent component = new AtomixComponent(config);

  @Nested
  final class MessagingServiceTest {
    @Test
    void shouldAdvertiseConfiguredAddress() {
      // given
      config.getCluster().setAdvertisedHost("foo").setAdvertisedPort(5);

      // when
      final var cluster = component.createAtomixCluster();

      // then
      assertThat(cluster.getMessagingService().address()).isEqualTo(Address.from("foo", 5));
      assertThat(cluster.getMessagingService().bindingAddresses())
          .doesNotContain(Address.from("foo", 5));
    }

    @Test
    void shouldBindToCorrectAddress() {
      // given
      config.getCluster().setHost("foo").setPort(5).setAdvertisedPort(6).setAdvertisedHost("bar");

      // when
      final var cluster = component.createAtomixCluster();

      // then
      assertThat(cluster.getMessagingService().address()).isNotEqualTo(Address.from("foo", 5));
      assertThat(cluster.getMessagingService().bindingAddresses())
          .containsExactly(Address.from("foo", 5));
    }
  }
}
