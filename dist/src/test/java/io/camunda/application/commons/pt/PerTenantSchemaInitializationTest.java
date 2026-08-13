/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.util.retry.RetryConfiguration;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Exercises the settle barrier and the background retry loop against a fake attempt, so that the
 * concurrency is asserted without a storage container and in milliseconds.
 */
@Timeout(60)
final class PerTenantSchemaInitializationTest {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";

  @Test
  void shouldReleaseBarrierOnlyAfterEveryTenantHasAFirstOutcome() throws Exception {
    // given - tenant A initializes immediately while tenant B is still held up, as during a
    // rolling upgrade where one tenant's migration takes much longer than the other's
    final var releaseTenantB = new CountDownLatch(1);
    final var tenantBEntered = new CountDownLatch(1);
    try (final var initialization =
        initialization(
            bothTenants(),
            tenantId -> {
              if (TENANT_B.equals(tenantId)) {
                tenantBEntered.countDown();
                awaitUninterruptibly(releaseTenantB);
              }
            })) {
      final var barrierReleased = startInBackground(initialization);

      // when - tenant A has settled but tenant B has not
      assertThat(tenantBEntered.await(10, TimeUnit.SECONDS)).isTrue();
      Awaitility.await("tenant A is initialized")
          .untilAsserted(() -> assertThat(initialization.isInitialized(TENANT_A)).isTrue());

      // then - the barrier still holds: admitting the node here would route tenant B's traffic to
      // a node that has not migrated tenant B yet
      assertThat(barrierReleased.await(200, TimeUnit.MILLISECONDS)).isFalse();

      // when - tenant B produces its first outcome too
      releaseTenantB.countDown();

      // then
      assertThat(barrierReleased.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(initialization.isInitialized(TENANT_B)).isTrue();
    }
  }

  @Test
  void shouldReleaseBarrierWhenATenantOnlyEverFails() {
    // given
    try (final var initialization =
        initialization(
            bothTenants(),
            tenantId -> {
              if (TENANT_B.equals(tenantId)) {
                throw new IllegalStateException("storage unreachable");
              }
            })) {

      // when - one tenant's storage never comes back
      initialization.startAndAwaitFirstOutcome();

      // then - startup is not held by it, and only that tenant is degraded
      assertThat(initialization.isInitialized(TENANT_A)).isTrue();
      assertThat(initialization.isInitialized(TENANT_B)).isFalse();
    }
  }

  @Test
  void shouldInitializeInBackgroundAfterTransientFailures() {
    // given - tenant B's storage only accepts the third attempt
    final var attempts = new AtomicInteger();
    try (final var initialization =
        initialization(
            Set.of(TENANT_B),
            tenantId -> {
              if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("storage unreachable");
              }
            })) {

      // when - the barrier releases on the first failure, so this returns without the tenant
      initialization.startAndAwaitFirstOutcome();

      // then - the retry loop keeps going in the background until the tenant recovers, with no
      // restart and no operator action
      Awaitility.await("tenant B recovers on its own")
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(initialization.isInitialized(TENANT_B)).isTrue());
      assertThat(attempts).hasValue(3);
    }
  }

  @Test
  void shouldStopRetryingAfterATerminalFailure() {
    // given
    final var attempts = new AtomicInteger();
    try (final var initialization =
        initialization(
            Set.of(TENANT_B),
            tenantId -> {
              attempts.incrementAndGet();
              throw new TerminalFailure();
            })) {

      // when
      initialization.startAndAwaitFirstOutcome();

      // then - the tenant is degraded and no further attempt is made
      assertThat(initialization.isInitialized(TENANT_B)).isFalse();
      assertAttemptCountStopsGrowing(attempts, 1);
    }
  }

  @Test
  void shouldClassifyTerminalFailuresByCauseChain() {
    // given - the terminal cause arrives wrapped, as SchemaManager's sneaky throws deliver it
    final var attempts = new AtomicInteger();
    try (final var initialization =
        initialization(
            Set.of(TENANT_B),
            tenantId -> {
              attempts.incrementAndGet();
              throw new IllegalStateException(
                  "init schema failed", new RuntimeException("wrapped", new TerminalFailure()));
            })) {

      // when
      initialization.startAndAwaitFirstOutcome();

      // then
      assertAttemptCountStopsGrowing(attempts, 1);
    }
  }

  @Test
  void shouldTreatACyclicCauseChainAsRetryable() {
    // given - a cyclic cause must neither hang the classification nor be mistaken for a terminal
    // failure
    final var attempts = new AtomicInteger();
    final var cyclic = new IllegalStateException("storage unreachable");
    cyclic.initCause(new IllegalStateException("client closed", cyclic));
    try (final var initialization =
        initialization(
            Set.of(TENANT_B),
            tenantId -> {
              attempts.incrementAndGet();
              throw cyclic;
            })) {

      // when
      initialization.startAndAwaitFirstOutcome();

      // then
      Awaitility.await("the tenant keeps being retried")
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(attempts.get()).isGreaterThan(1));
    }
  }

  @Test
  void shouldReleaseBarrierWhenTheRetryConfigurationIsUnusable() {
    // given - a max delay resilience4j rejects, which throws while the task is being set up rather
    // than from an attempt
    final var unusable = new RetryConfiguration();
    unusable.setMaxRetryDelay(Duration.ZERO);
    try (final var initialization =
        new PerTenantSchemaInitialization(
            bothTenants(),
            tenantId -> {},
            TerminalFailure.class::isInstance,
            tenantId -> TENANT_B.equals(tenantId) ? unusable : fastRetry())) {

      // when / then - a tenant that cannot even start must not hold startup open forever
      initialization.startAndAwaitFirstOutcome();
      assertThat(initialization.isInitialized(TENANT_A)).isTrue();
      assertThat(initialization.isInitialized(TENANT_B)).isFalse();
    }
  }

  @Test
  void shouldStopRetryingOnceTheConfiguredAttemptsAreExhausted() {
    // given - an operator who bounded the attempts rather than taking the unbounded default
    final var bounded = fastRetry();
    bounded.setMaxRetries(3);
    final var attempts = new AtomicInteger();
    try (final var initialization =
        new PerTenantSchemaInitialization(
            Set.of(TENANT_B),
            tenantId -> {
              attempts.incrementAndGet();
              throw new IllegalStateException("storage unreachable");
            },
            TerminalFailure.class::isInstance,
            tenantId -> bounded)) {

      // when
      initialization.startAndAwaitFirstOutcome();

      // then - the bound is honoured exactly, and the tenant is left degraded rather than retried
      // forever
      assertAttemptCountStopsGrowing(attempts, 3);
      assertThat(attempts).hasValue(3);
      assertThat(initialization.isInitialized(TENANT_B)).isFalse();
    }
  }

  @Test
  void shouldReportUnknownTenantAsNotInitialized() {
    // given
    try (final var initialization = initialization(Set.of(TENANT_A), tenantId -> {})) {
      // when
      initialization.startAndAwaitFirstOutcome();

      // then
      assertThat(initialization.isInitialized(TENANT_A)).isTrue();
      assertThat(initialization.isInitialized("never-configured")).isFalse();
    }
  }

  @Test
  void shouldReleaseABlockedBarrierOnClose() throws Exception {
    // given - attempts that do not observe interruption, standing in for a storage client blocked
    // in a socket read
    final var blockForever = new CountDownLatch(1);
    final var initialization =
        initialization(bothTenants(), tenantId -> awaitUninterruptibly(blockForever));
    try {
      final var barrierReleased = startInBackground(initialization);
      assertThat(barrierReleased.await(200, TimeUnit.MILLISECONDS)).isFalse();

      // when
      initialization.close();

      // then - shutdown does not wait for the stuck attempts
      assertThat(barrierReleased.await(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      blockForever.countDown();
      initialization.close();
    }
  }

  @Test
  void shouldStopRetryingOnClose() {
    // given
    final var attempts = new AtomicInteger();
    final var initialization =
        initialization(
            Set.of(TENANT_B),
            tenantId -> {
              attempts.incrementAndGet();
              throw new IllegalStateException("storage unreachable");
            });
    initialization.startAndAwaitFirstOutcome();
    Awaitility.await("the retry loop is running")
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(attempts.get()).isGreaterThan(1));

    // when
    initialization.close();

    // then - at most the attempt already in flight when close() returned still completes
    assertAttemptCountStopsGrowing(attempts, attempts.get() + 1);
  }

  @Test
  void shouldNotAttemptAnythingWhenClosedBeforeStarting() {
    // given
    final var attempts = new AtomicInteger();
    final var initialization =
        initialization(bothTenants(), tenantId -> attempts.incrementAndGet());

    // when
    initialization.close();
    initialization.startAndAwaitFirstOutcome();

    // then - the barrier releases without blocking, and no tenant is claimed as initialized
    assertThat(attempts).hasValue(0);
    assertThat(initialization.isInitialized(TENANT_A)).isFalse();
    assertThat(initialization.isInitialized(TENANT_B)).isFalse();
  }

  @Test
  void shouldUseEachTenantsOwnRetryConfiguration() {
    // given
    final var requestedFor = ConcurrentHashMap.<String>newKeySet();
    try (final var initialization =
        new PerTenantSchemaInitialization(
            bothTenants(),
            tenantId -> {},
            TerminalFailure.class::isInstance,
            tenantId -> {
              requestedFor.add(tenantId);
              return fastRetry();
            })) {

      // when
      initialization.startAndAwaitFirstOutcome();

      // then
      assertThat(requestedFor).containsExactlyInAnyOrder(TENANT_A, TENANT_B);
    }
  }

  @Test
  void shouldBeIdempotentOnClose() {
    // given
    final var initialization = initialization(Set.of(TENANT_A), tenantId -> {});
    initialization.startAndAwaitFirstOutcome();

    // when / then
    initialization.close();
    initialization.close();
    assertThat(initialization.isInitialized(TENANT_A)).isTrue();
  }

  @Test
  void shouldRunTenantsConcurrently() throws Exception {
    // given - neither tenant's attempt can finish unless the other one is running too
    final var bothRunning = new CountDownLatch(2);
    try (final var initialization =
        initialization(
            bothTenants(),
            tenantId -> {
              bothRunning.countDown();
              awaitUninterruptibly(bothRunning);
            })) {

      // when - a sequential initialization would deadlock here, which is what starves the tenants
      // queued behind an unreachable one today
      initialization.startAndAwaitFirstOutcome();

      // then
      assertThat(bothRunning.await(0, TimeUnit.SECONDS)).isTrue();
      assertThat(initialization.isInitialized(TENANT_A)).isTrue();
      assertThat(initialization.isInitialized(TENANT_B)).isTrue();
    }
  }

  @Test
  void shouldTolerateNoTenants() {
    // given - a deployment with no search-engine tenant must not hold startup
    try (final var initialization = initialization(Set.of(), tenantId -> {})) {
      // when / then
      initialization.startAndAwaitFirstOutcome();
      assertThat(initialization.isInitialized("anything")).isFalse();
    }
  }

  @Test
  void shouldKeepTenantStateIndependent() {
    // given
    final var tenants = new LinkedHashSet<>(List.of("a", "b", "c"));
    try (final var initialization =
        initialization(
            tenants,
            tenantId -> {
              if ("b".equals(tenantId)) {
                throw new TerminalFailure();
              }
            })) {

      // when
      initialization.startAndAwaitFirstOutcome();

      // then
      assertThat(initialization.isInitialized("a")).isTrue();
      assertThat(initialization.isInitialized("b")).isFalse();
      assertThat(initialization.isInitialized("c")).isTrue();
    }
  }

  private static CountDownLatch startInBackground(
      final PerTenantSchemaInitialization initialization) {
    final var barrierReleased = new CountDownLatch(1);
    Thread.ofPlatform()
        .name("test-startup")
        .start(
            () -> {
              initialization.startAndAwaitFirstOutcome();
              barrierReleased.countDown();
            });
    return barrierReleased;
  }

  private static void assertAttemptCountStopsGrowing(
      final AtomicInteger attempts, final int expectedCeiling) {
    Awaitility.await("the attempt count stops growing")
        .during(Duration.ofMillis(500))
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(attempts.get()).isLessThanOrEqualTo(expectedCeiling));
  }

  private static PerTenantSchemaInitialization initialization(
      final Set<String> tenantIds, final Consumer<String> attempt) {
    return new PerTenantSchemaInitialization(
        tenantIds, attempt, TerminalFailure.class::isInstance, retryConfig());
  }

  private static Function<String, RetryConfiguration> retryConfig() {
    return tenantId -> fastRetry();
  }

  /** Milliseconds rather than the production seconds, so retries are observable within a test. */
  private static RetryConfiguration fastRetry() {
    final var retry = new RetryConfiguration();
    retry.setMinRetryDelay(Duration.ofMillis(1));
    retry.setMaxRetryDelay(Duration.ofMillis(5));
    retry.setRetryDelayMultiplier(1.5);
    return retry;
  }

  private static Set<String> bothTenants() {
    return new LinkedHashSet<>(List.of(TENANT_A, TENANT_B));
  }

  private static void awaitUninterruptibly(final CountDownLatch latch) {
    boolean interrupted = false;
    try {
      while (true) {
        try {
          latch.await();
          return;
        } catch (final InterruptedException e) {
          interrupted = true;
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /** A failure the orchestrator is told retrying cannot repair. */
  private static final class TerminalFailure extends RuntimeException {}
}
