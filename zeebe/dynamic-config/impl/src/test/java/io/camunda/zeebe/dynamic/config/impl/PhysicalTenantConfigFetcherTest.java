/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference;
import io.camunda.zeebe.dynamic.config.state.PhysicalTenantConfigReference.Owner;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PhysicalTenantConfigFetcherTest {

  private static final byte[] CONFIGURATION =
      "data.snapshot-period=7m".getBytes(StandardCharsets.UTF_8);
  private static final MemberId PEER_1 = MemberId.from("1");
  private static final MemberId PEER_2 = MemberId.from("2");

  @TempDir Path dataDirectory;

  private final ClusterCommunicationService communication = mock(ClusterCommunicationService.class);

  @Test
  void shouldUseTheStoredConfigurationWithoutAskingPeers() {
    // given
    final var store = new PhysicalTenantConfigStore(dataDirectory);
    store.put("tenant", 1, CONFIGURATION);
    final var fetcher = new PhysicalTenantConfigFetcher(communication, store);

    // when
    final var fetched = fetcher.fetch("tenant", reference(), List.of(PEER_1)).join();

    // then
    assertThat(fetched).isEqualTo(CONFIGURATION);
    verify(communication, never())
        .send(any(), any(), any(), any(), any(MemberId.class), any(Duration.class));
  }

  @Test
  void shouldSkipAPeerWhoseConfigurationDoesNotMatchTheHashAndStoreTheMatchingOne() {
    // given
    final var store = new PhysicalTenantConfigStore(dataDirectory);
    final var fetcher = new PhysicalTenantConfigFetcher(communication, store);
    answer(PEER_1, CompletableFuture.completedFuture("tampered".getBytes(StandardCharsets.UTF_8)));
    answer(PEER_2, CompletableFuture.completedFuture(CONFIGURATION));

    // when
    final var fetched = fetcher.fetch("tenant", reference(), List.of(PEER_1, PEER_2)).join();

    // then
    assertThat(fetched).isEqualTo(CONFIGURATION);
    assertThat(store.get("tenant", 1)).contains(CONFIGURATION);
  }

  @Test
  void shouldFailWhenNoPeerHoldsTheConfiguration() {
    // given
    final var fetcher =
        new PhysicalTenantConfigFetcher(
            communication, new PhysicalTenantConfigStore(dataDirectory));
    answer(PEER_1, CompletableFuture.failedFuture(new IllegalStateException("unreachable")));
    answer(PEER_2, CompletableFuture.completedFuture(new byte[0]));

    // when
    final var fetched = fetcher.fetch("tenant", reference(), List.of(PEER_1, PEER_2));

    // then
    assertThat(fetched)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableThat()
        .withMessageContaining("No reachable member holds version 1");
  }

  @Test
  void shouldNotTrustAStoredConfigurationThatDoesNotMatchTheHash() {
    // given
    final var store = new PhysicalTenantConfigStore(dataDirectory);
    store.put("tenant", 1, "stale".getBytes(StandardCharsets.UTF_8));

    // when
    final var stored = store.get("tenant", 1, reference().sha256());

    // then
    assertThat(stored).isEmpty();
  }

  private void answer(final MemberId peer, final CompletableFuture<byte[]> response) {
    when(communication.<Object, byte[]>send(
            eq(PhysicalTenantConfigFetcher.TOPIC),
            any(),
            any(),
            any(),
            eq(peer),
            any(Duration.class)))
        .thenReturn(response);
  }

  private static PhysicalTenantConfigReference reference() {
    return PhysicalTenantConfigReference.initial(
        PhysicalTenantConfigStore.sha256(CONFIGURATION), Owner.API, "incarnation");
  }
}
