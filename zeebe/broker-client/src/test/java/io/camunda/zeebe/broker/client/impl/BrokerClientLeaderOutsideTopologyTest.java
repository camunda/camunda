/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.client.api.mailbox.GatewayMailboxSetup;
import io.camunda.zeebe.broker.client.impl.mailbox.GatewayMailbox;
import io.camunda.zeebe.mailbox.InMemoryMailboxStore;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.api.PartitionLeader;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A client reaches a leader that the topology does not show through the mailbox, if the store has
 * one recorded for the partition. A region that leads no partition relies on this, as its topology
 * holds none of the leaders.
 */
final class BrokerClientLeaderOutsideTopologyTest {

  private static final String DEFAULT_GROUP = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;
  private static final MailboxConfig CONFIG =
      new MailboxConfig(
          Duration.ofMillis(5),
          Duration.ofMillis(20),
          Duration.ofMinutes(1),
          10,
          Duration.ofHours(1),
          Duration.ofHours(1));

  private final InMemoryMailboxStore store = new InMemoryMailboxStore();
  private final GatewayMailbox mailbox =
      new GatewayMailbox(new GatewayMailboxSetup(store, CONFIG, "gateway-1", Runnable::run));

  @AfterEach
  void tearDown() {
    mailbox.close();
  }

  @Test
  void shouldHaveALeaderOutsideTheTopologyWhenTheMailboxRecordsOne() {
    // given
    store.putLeader(new PartitionLeader(1, "east_0", "east", 1));
    mailbox.refreshLeaders();

    // then
    assertThat(BrokerClientImpl.hasLeaderInMailbox(mailbox, DEFAULT_GROUP, 1)).isTrue();
    assertThat(BrokerClientImpl.hasLeaderInMailbox(mailbox, DEFAULT_GROUP, 2))
        .as("a partition without a recorded leader")
        .isFalse();
  }

  @Test
  void shouldHaveNoLeaderOutsideTheTopologyForAnotherPartitionGroup() {
    // given - the mailbox is for the default physical tenant only
    store.putLeader(new PartitionLeader(1, "east_0", "east", 1));
    mailbox.refreshLeaders();

    // then
    assertThat(BrokerClientImpl.hasLeaderInMailbox(mailbox, "tenant-b", 1)).isFalse();
  }

  @Test
  void shouldHaveNoLeaderOutsideTheTopologyWithoutAMailbox() {
    // then
    assertThat(BrokerClientImpl.hasLeaderInMailbox(null, DEFAULT_GROUP, 1)).isFalse();
  }
}
