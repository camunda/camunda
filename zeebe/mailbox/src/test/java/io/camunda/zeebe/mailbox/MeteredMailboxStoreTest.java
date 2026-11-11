/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.mailbox.api.GatewayId;
import io.camunda.zeebe.mailbox.api.MailboxStoreException;
import io.camunda.zeebe.mailbox.api.MessageKind;
import io.camunda.zeebe.mailbox.api.NewInboxMessage;
import io.camunda.zeebe.mailbox.api.OutboxResponse;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class MeteredMailboxStoreTest {

  private static final Duration CLAIM_TIMEOUT = Duration.ofSeconds(10);

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final MutableInstantSource clock = new MutableInstantSource();
  private final InMemoryMailboxStore delegate = new InMemoryMailboxStore(clock);
  private final MeteredMailboxStore store = new MeteredMailboxStore(delegate, registry);

  @Test
  void shouldTimeEachCallByOperationAndOutcome() {
    // given
    final var failing =
        new MeteredMailboxStore(
            StoreProxy.wrap(delegate, Set.of("takeResponses"), new ArrayList<>()), registry);

    // when
    store.insert(request(1, 1));
    store.insert(request(1, 2));
    assertThatThrownBy(() -> failing.takeResponses(new GatewayId("gw"), Set.of(1L)))
        .isInstanceOf(MailboxStoreException.class);

    // then
    assertThat(latency("insert", "success").count()).isEqualTo(2);
    assertThat(latency("takeResponses", "failure").count()).isOne();
    assertThat(registry.find(MeteredMailboxStore.STORE_LATENCY).tag("outcome", "success").timers())
        .extracting(timer -> timer.getId().getTag("operation"))
        .containsExactly("insert");
  }

  @Test
  void shouldCountTheMessagesThatAreHandedOutAgain() {
    // given
    store.insert(request(1, 1));
    store.insert(request(1, 2));
    store.claim(1, "old-leader", 10, CLAIM_TIMEOUT);
    store.releaseClaims(1, "old-leader");

    // when
    final var claimed = store.claim(1, "new-leader", 10, CLAIM_TIMEOUT);
    store.acknowledge(1, "new-leader", List.of(claimed.getFirst().sequence()));

    // then
    assertThat(inboxCount(MeteredMailboxStore.CLAIMED)).isEqualTo(4);
    assertThat(inboxCount(MeteredMailboxStore.REDELIVERED)).isEqualTo(2);
    assertThat(
            registry.get(MeteredMailboxStore.ACKNOWLEDGED).tag("partition", "1").counter().count())
        .isOne();
  }

  @Test
  void shouldCountTheRowsThatNobodyCollectedInTime() {
    // given
    store.insert(request(1, 1));
    store.putResponse(new OutboxResponse(new GatewayId("gw"), 1, new byte[0]));
    store.putResponse(new OutboxResponse(new GatewayId("gw"), 2, new byte[0]));
    clock.advance(Duration.ofMinutes(2));

    // when
    store.deleteExpired(Duration.ofMinutes(1), Duration.ofMinutes(1));

    // then
    assertThat(expired("inbox")).isOne();
    assertThat(expired("outbox")).isEqualTo(2);
  }

  private Timer latency(final String operation, final String outcome) {
    return registry
        .get(MeteredMailboxStore.STORE_LATENCY)
        .tag("operation", operation)
        .tag("outcome", outcome)
        .timer();
  }

  private double inboxCount(final String name) {
    return registry
        .get(name)
        .tag("partition", "1")
        .tag("kind", MessageKind.CLIENT_REQUEST.name())
        .counter()
        .count();
  }

  private double expired(final String table) {
    return registry.get(MeteredMailboxStore.EXPIRED).tag("table", table).counter().count();
  }

  private static NewInboxMessage request(final int partitionId, final long requestId) {
    return new NewInboxMessage(
        partitionId, MessageKind.CLIENT_REQUEST, "gw", requestId, new byte[0]);
  }
}
