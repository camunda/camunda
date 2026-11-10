/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.mailbox.api.CleanupResult;
import io.camunda.zeebe.mailbox.api.GatewayId;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.api.MessageKind;
import io.camunda.zeebe.mailbox.api.NewInboxMessage;
import io.camunda.zeebe.mailbox.api.OutboxResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MailboxCleanerTest {

  private static final MailboxConfig CONFIG =
      new MailboxConfig(
          Duration.ofMillis(10),
          Duration.ofMillis(100),
          Duration.ofMinutes(1),
          10,
          Duration.ofMinutes(10),
          Duration.ofMinutes(5));

  private final MutableInstantSource clock = new MutableInstantSource();
  private final InMemoryMailboxStore store = new InMemoryMailboxStore(clock);

  @Test
  void shouldRemoveRowsOlderThanTheirTimeToLive() {
    // given
    store.insert(new NewInboxMessage(1, MessageKind.CLIENT_REQUEST, "gateway-1", 1, new byte[0]));
    store.putResponse(new OutboxResponse(new GatewayId("gateway-1"), 1, new byte[0]));
    clock.advance(Duration.ofMinutes(6));

    // when
    final var afterFiveMinutes = new MailboxCleaner(store, CONFIG).cleanOnce();
    clock.advance(Duration.ofMinutes(5));
    final var afterTenMinutes = new MailboxCleaner(store, CONFIG).cleanOnce();

    // then
    assertThat(afterFiveMinutes).isEqualTo(new CleanupResult(0, 1));
    assertThat(afterTenMinutes).isEqualTo(new CleanupResult(1, 0));
  }

  @Test
  void shouldReportNothingRemovedWhenTheStoreFails() {
    // given
    final var failingStore = StoreProxy.wrap(store, Set.of("deleteExpired"), new ArrayList<>());

    // when
    final var result = new MailboxCleaner(failingStore, CONFIG).cleanOnce();

    // then
    assertThat(result).isEqualTo(new CleanupResult(0, 0));
  }
}
