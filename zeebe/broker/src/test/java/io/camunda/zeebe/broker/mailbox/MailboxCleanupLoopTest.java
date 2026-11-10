/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.mailbox.api.CleanupResult;
import io.camunda.zeebe.mailbox.api.MailboxConfig;
import io.camunda.zeebe.mailbox.api.MailboxStore;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class MailboxCleanupLoopTest {

  private static MailboxConfig config(final Duration inboxTtl, final Duration outboxTtl) {
    return new MailboxConfig(
        Duration.ofMillis(10),
        Duration.ofMillis(100),
        Duration.ofSeconds(30),
        10,
        inboxTtl,
        outboxTtl);
  }

  @Test
  void shouldCleanUpWithTheTimesToLive() {
    // given
    final var store = mock(MailboxStore.class);
    when(store.deleteExpired(any(), any())).thenReturn(new CleanupResult(0, 0));
    final var loop =
        new MailboxCleanupLoop(
            "test-cleanup", store, config(Duration.ofSeconds(2), Duration.ofSeconds(3)));

    // when
    loop.start();

    // then
    verify(store, timeout(5_000).atLeastOnce())
        .deleteExpired(Duration.ofSeconds(2), Duration.ofSeconds(3));
    loop.close();
  }

  @Test
  void shouldNotCleanUpBeforeItStarts() {
    // given
    final var store = mock(MailboxStore.class);

    // when
    final var loop =
        new MailboxCleanupLoop(
            "test-cleanup", store, config(Duration.ofMinutes(5), Duration.ofMinutes(5)));

    // then
    verify(store, after(1_500).never()).deleteExpired(any(), any());
    loop.close();
  }

  @Test
  void shouldChooseATenthOfTheShorterTimeToLive() {
    assertThat(MailboxCleanupLoop.intervalFor(config(Duration.ofMinutes(5), Duration.ofMinutes(8))))
        .isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void shouldNotCleanUpMoreOftenThanEverySecond() {
    assertThat(MailboxCleanupLoop.intervalFor(config(Duration.ofSeconds(2), Duration.ofSeconds(2))))
        .isEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void shouldNotWaitLongerThanAMinute() {
    assertThat(MailboxCleanupLoop.intervalFor(config(Duration.ofDays(1), Duration.ofDays(1))))
        .isEqualTo(Duration.ofMinutes(1));
  }
}
