/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.leadership;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.mailbox.api.leadership.LeaseStore;
import io.camunda.zeebe.mailbox.api.leadership.LeaseStoreContractTest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The metered store must behave as the store it wraps, and time each call. */
final class MeteredLeaseStoreTest extends LeaseStoreContractTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

  @Override
  protected LeaseStore createStore() {
    return new MeteredLeaseStore(new InMemoryLeaseStore(), registry);
  }

  @Test
  void shouldTimeTheRenewals() {
    // given
    final var lease = store.claim(1, "e1", "east", LeaseStore.NO_LEASE, 0).orElseThrow();

    // when
    store.renew("e1", Map.of(1, lease.term()));
    store.renew("e1", Map.of(1, lease.term()));

    // then
    assertThat(
            registry
                .get(MeteredLeaseStore.STORE_LATENCY)
                .tag("operation", "renew")
                .tag("outcome", "success")
                .timer()
                .count())
        .isEqualTo(2);
  }
}
