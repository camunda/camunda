/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.nodeid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.dynamic.nodeid.Lease.VersionMappings;
import io.camunda.zeebe.dynamic.nodeid.repository.Metadata;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository;
import io.camunda.zeebe.dynamic.nodeid.repository.NodeIdRepository.StoredLease;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RepositoryNodeIdProviderTest {

  private static final Duration EXPIRY_DURATION = Duration.ofSeconds(30);
  private static final String TASK_ID = UUID.randomUUID().toString();

  private NodeIdRepository repository;
  private ControlledInstantSource clock;
  private AtomicBoolean leaseFailedFlag;
  private RepositoryNodeIdProvider provider;

  @BeforeEach
  void setUp() {
    repository = mock(NodeIdRepository.class);
    clock =
        new ControlledInstantSource(
            Instant.parse("2025-11-01T13:00:00Z").atZone(ZoneId.of("UTC")).toInstant());
    leaseFailedFlag = new AtomicBoolean(false);
    provider =
        new RepositoryNodeIdProvider(
            repository,
            clock,
            EXPIRY_DURATION,
            Duration.ofSeconds(30),
            Duration.ofSeconds(60),
            TASK_ID,
            () -> leaseFailedFlag.set(true));
  }

  /**
   * Initializes the provider by mocking the repository to return a valid lease for nodeId 0. This
   * simulates the acquireInitialLease flow without requiring an actual repository.
   */
  private StoredLease.Initialized initializeProviderWithLease() {
    final var storedLease = createStoredLease(clock.millis() + EXPIRY_DURATION.toMillis());

    // Mock getLease to return an uninitialized lease that can be acquired
    final var uninitializedLease =
        new StoredLease.Uninitialized(new NodeInstance(0, Version.zero()), "initial-etag");
    when(repository.initialize(1)).thenReturn(1);
    when(repository.getLease(0)).thenReturn(uninitializedLease);
    when(repository.acquire(any(), any())).thenReturn(storedLease);

    provider.initialize(1).join();
    return storedLease;
  }

  private StoredLease.Initialized createStoredLease(final long expireAt) {
    final var nodeInstance = new NodeInstance(0, Version.zero().next());
    final var lease = new Lease(TASK_ID, expireAt, nodeInstance, VersionMappings.empty());
    final var metadata = new Metadata(Optional.of(TASK_ID), nodeInstance.version());
    return new StoredLease.Initialized(metadata, lease, "etag-" + UUID.randomUUID());
  }

  @Nested
  class Renew {

    @Test
    void shouldNotThrowNpeWhenLeaseIsNullDuringShutdown() throws Exception {
      // given — provider is initialized with a lease, then closed (which nullifies currentLease)
      initializeProviderWithLease();
      provider.close();

      // when — renew is called after shutdown (simulates a scheduled renewal racing with close)
      assertThatNoException().isThrownBy(provider::renew);

      // then — onLeaseFailure should NOT be called since this is an expected shutdown scenario
      assertThat(leaseFailedFlag).isFalse();
    }

    @Test
    void shouldInvokeFailureHandlerWhenLeaseIsNullWithoutShutdown() {
      // given — provider is initialized with a lease, then a renewal failure sets currentLease=null
      initializeProviderWithLease();
      simulateRenewalFailure();
      leaseFailedFlag.set(false); // reset after the first failure

      // when — the next scheduled renewal finds currentLease=null but shutdown is not initiated
      provider.renew();

      // then — onLeaseFailure should be invoked since this is an unexpected null
      assertThat(leaseFailedFlag).isTrue();
    }

    @Test
    void shouldRenewSuccessfullyWhenLeaseIsValid() {
      // given
      initializeProviderWithLease();
      clearInvocations(repository);
      final var renewedStoredLease = createStoredLease(clock.millis() + EXPIRY_DURATION.toMillis());
      when(repository.acquire(any(), any())).thenReturn(renewedStoredLease);

      // when
      provider.renew();

      // then
      assertThat(leaseFailedFlag).isFalse();
      assertThat(provider.getCurrentLease()).isEqualTo(renewedStoredLease);
      verify(repository).acquire(any(), any());
    }

    @Test
    void shouldNotWriteLeaseWhenShutdownInitiatedDuringRenewal() throws Exception {
      // given — provider has a valid lease
      initializeProviderWithLease();

      // when — shutdown is initiated before renew runs
      provider.close();
      reset(repository);

      provider.renew();

      // then — acquire should not be called since we're shutting down
      verify(repository, never()).acquire(any(), any());
      assertThat(leaseFailedFlag).isFalse();
    }

    private void simulateRenewalFailure() {
      when(repository.acquire(any(), any()))
          .thenThrow(new IllegalStateException("Injected failure"));
      provider.renew();
      assertThat(leaseFailedFlag).isTrue();
      assertThat(provider.getCurrentLease()).isNull();
    }
  }
}
