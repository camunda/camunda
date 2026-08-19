/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Exercises the startup gate and the background retry loop against a fake attempt, so that the
 * concurrency is asserted without a storage container and in milliseconds.
 *
 * <p>Three failure modes are worth more than the rest, and most of these tests exist for one of
 * them. Opening the gate too early serves the webapp a 503 storm with a broken login for as long as
 * a co-started storage takes to boot. Never opening it — one exit path that leaves a tenant counted
 * as still trying — is a permanent startup hang with nothing in the logs to say why; the class
 * timeout is what turns that into a failing test rather than a stuck build. And aborting on the
 * wrong set of tenants turns an outage the node would have retried through into a crash loop, or
 * lets a node that can serve nothing come up and export into a schema it was told is unusable.
 */
@Timeout(60)
final class PerTenantSchemaInitializationTest {

  private static final String TENANT_A = "tenanta";
  private static final String TENANT_B = "tenantb";

  @Test
  void shouldHoldTheGateWhileTheOnlyTenantKeepsFailing() throws Exception {
    // given - the ordinary case of a node started alongside its storage: the first attempt fails
    // within the client's one-second connect timeout, and the storage is simply not up yet
    final var attempts = new AtomicInteger();
    try (final var initialization =
        initialization(
            Set.of(TENANT_A),
            tenantId -> {
              attempts.incrementAndGet();
              throw new IllegalStateException("storage is still starting");
            })) {
      final var gateOpened = startInBackground(initialization);

      // when - the tenant has settled by failing, and keeps retrying
      Awaitility.await("the tenant is retrying")
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(attempts.get()).isGreaterThan(3));

      // then - the port stays shut. Releasing the gate on a first outcome alone is what admitted
      // the node a second into startup and then served 503 from every endpoint that needs
      // secondary storage.
      assertThat(gateOpened.await(500, TimeUnit.MILLISECONDS)).isFalse();
    }
  }

  @Test
  void shouldOpenTheGateOnceAnotherTenantBecomesServiceable() throws Exception {
    // given - one tenant whose storage is unreachable, and one that is merely slow
    final var releaseTenantB = new CountDownLatch(1);
    final var tenantAFailures = new AtomicInteger();
    try (final var initialization =
        initialization(
            bothTenants(),
            tenantId -> {
              if (TENANT_A.equals(tenantId)) {
                tenantAFailures.incrementAndGet();
                throw new IllegalStateException("storage unreachable");
              }
              awaitUninterruptibly(releaseTenantB);
            })) {
      final var gateOpened = startInBackground(initialization);

      // when - tenant A has settled, repeatedly, but nothing is serviceable yet
      Awaitility.await("tenant A is retrying")
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(tenantAFailures.get()).isGreaterThan(1));

      // then
      assertThat(gateOpened.await(200, TimeUnit.MILLISECONDS)).isFalse();

      // when - the other tenant initializes
      releaseTenantB.countDown();

      // then - the node is admitted for the tenant it can serve, while the degraded one keeps
      // retrying in the background
      assertThat(gateOpened.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(initialization.isInitialized(TENANT_B)).isTrue();
      assertThat(initialization.isInitialized(TENANT_A)).isFalse();
    }
  }

  @Test
  void shouldHoldTheGateWhileATenantIsStillInsideItsFirstAttempt() throws Exception {
    // given - tenant B initializes immediately while tenant A is still applying its schema, as
    // during a rolling upgrade where one tenant's migration takes much longer than the other's
    final var releaseTenantA = new CountDownLatch(1);
    final var tenantAEntered = new CountDownLatch(1);
    try (final var initialization =
        initialization(
            bothTenants(),
            tenantId -> {
              if (TENANT_A.equals(tenantId)) {
                tenantAEntered.countDown();
                awaitUninterruptibly(releaseTenantA);
              }
            })) {
      final var gateOpened = startInBackground(initialization);

      // when - tenant B is serviceable but tenant A has not settled
      assertThat(tenantAEntered.await(10, TimeUnit.SECONDS)).isTrue();
      Awaitility.await("tenant B is initialized")
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(initialization.isInitialized(TENANT_B)).isTrue());

      // then - the gate still holds: admitting the node here would route tenant A's traffic to a
      // node that has not migrated tenant A yet
      assertThat(gateOpened.await(200, TimeUnit.MILLISECONDS)).isFalse();

      // when - tenant A's migration completes too
      releaseTenantA.countDown();

      // then
      assertThat(gateOpened.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(initialization.isInitialized(TENANT_A)).isTrue();
    }
  }

  @Test
  void shouldAbortStartupWhenEveryTenantFailedTerminally() {
    // given - a misconfiguration no retry can repair, on every tenant
    try (final var initialization =
        initialization(
            bothTenants(),
            tenantId -> {
              throw new TerminalFailure();
            })) {

      // when - nothing will ever become serviceable, and nothing is still trying
      initialization.start();

      // then - startup aborts rather than releasing into a node that can serve nothing, becomes
      // ready never, and goes on exporting into the schema the classification just refused
      assertThatThrownBy(initialization::awaitGate)
          .isInstanceOf(EveryTenantTerminallyFailedException.class)
          .hasMessageContaining(TENANT_A)
          .hasMessageContaining(TENANT_B)
          .as("one stack trace carries every tenant's failure")
          .hasCauseInstanceOf(TerminalFailure.class)
          .extracting(Throwable::getSuppressed, InstanceOfAssertFactories.array(Throwable[].class))
          .singleElement()
          .isInstanceOf(TerminalFailure.class);
    }
  }

  @RepeatedTest(1000)
  void shouldAbortStartupWhenTheLastTenantGoesTerminalWhileTheGateIsAlreadyBeingWaitedOn() {
    final var bothStarted = new CountDownLatch(2);
    try (final var initialization =
        initialization(
            bothTenants(),
            tenantId -> {
              bothStarted.countDown();
              awaitUninterruptibly(bothStarted);
              throw new TerminalFailure();
            })) {
      initialization.start();

      // when / then - the waiter must see the diagnosis, never a bare "nothing is serviceable"
      assertThatThrownBy(initialization::awaitGate)
          .isInstanceOf(EveryTenantTerminallyFailedException.class);
    }
  }

  @Test
  void shouldNotAbortStartupWhileOneTenantIsStillServiceable() {
    // given - the isolation this whole change exists for: one tenant terminally misconfigured
    // must not take down a node that can serve the other
    try (final var initialization =
        initialization(
            bothTenants(),
            tenantId -> {
              if (TENANT_A.equals(tenantId)) {
                throw new TerminalFailure();
              }
            })) {

      // when / then
      initialization.start();
      assertThatNoException().isThrownBy(initialization::awaitGate);
      assertThat(initialization.isInitialized(TENANT_B)).isTrue();
      assertThat(initialization.isInitialized(TENANT_A)).isFalse();
    }
  }

  @Test
  void shouldNotAbortStartupWhenShuttingDown() {
    // given - every tenant terminal, so the gate is in exactly the state it aborts on
    final var attempts = new AtomicInteger();
    final var initialization =
        initialization(
            bothTenants(),
            tenantId -> {
              attempts.incrementAndGet();
              throw new TerminalFailure();
            });
    try {
      initialization.start();
      Awaitility.await("both tenants have failed terminally")
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(attempts.get()).isEqualTo(2));

      // when - shutdown arrives before anything waits on the gate
      initialization.close();

      // then - the abort is not raised on top of a shutdown that is already under way, where it
      // would mask whatever actually caused the context to close
      assertThatNoException().isThrownBy(initialization::awaitGate);
    } finally {
      initialization.close();
    }
  }

  @Test
  void shouldOpenTheGateWhenEveryTenantExhaustedItsRetries() {
    // given - an operator who bounded the attempts rather than taking the unbounded default
    final var bounded = fastRetry();
    bounded.setMaxRetries(3);
    final var attempts = new ConcurrentHashMap<String, AtomicInteger>();
    try (final var initialization =
        new PerTenantSchemaInitialization(
            bothTenants(),
            tenantId -> {
              attempts.computeIfAbsent(tenantId, id -> new AtomicInteger()).incrementAndGet();
              throw new IllegalStateException("storage unreachable");
            },
            TerminalFailure.class::isInstance,
            tenantId -> bounded)) {

      // when - the retry budget runs out on every tenant
      initialization.start();

      // then - the bound is honoured exactly, and the node comes up unable to serve rather than
      // waiting for a serviceable tenant no task is left to produce. It does not abort: an
      // exhausted budget is the operator's configured give-up, not a diagnosis that the
      // deployment is wrong, so it is not grounds for taking the node down.
      assertThatNoException().isThrownBy(initialization::awaitGate);
      assertThat(attempts.get(TENANT_A)).hasValue(3);
      assertThat(attempts.get(TENANT_B)).hasValue(3);
      assertThat(initialization.isInitialized(TENANT_A)).isFalse();
      assertThat(initialization.isInitialized(TENANT_B)).isFalse();
    }
  }

  @Test
  void shouldStillMakeOneAttemptWhenRetriesAreConfiguredAway() {
    // given - a max-retries an operator can set but that means nothing sensible. resilience4j
    // rejected it outright while it owned this bound; the loop that replaced it consults the bound
    // only after a failure, so the guarantee to pin is that the tenant still gets its attempt.
    final var none = fastRetry();
    none.setMaxRetries(0);
    final var attempts = new AtomicInteger();
    try (final var initialization =
        new PerTenantSchemaInitialization(
            Set.of(TENANT_A),
            tenantId -> attempts.incrementAndGet(),
            TerminalFailure.class::isInstance,
            tenantId -> none)) {

      // when
      initialization.start();
      initialization.awaitGate();

      // then - the tenant is initialized, on the one attempt the bound cannot take away from it
      assertThat(attempts).hasValue(1);
      assertThat(initialization.isInitialized(TENANT_A)).isTrue();
    }
  }

  @Test
  void shouldOpenTheGateWhenTheRetryConfigurationIsUnusable() {
    // given - a max delay resilience4j rejects, which throws while the task is being set up rather
    // than from an attempt, so only the outer handler can stop the tenant counting as still trying
    final var unusable = new RetryConfiguration();
    unusable.setMaxRetryDelay(Duration.ZERO);
    try (final var initialization =
        new PerTenantSchemaInitialization(
            Set.of(TENANT_A),
            tenantId -> {},
            TerminalFailure.class::isInstance,
            tenantId -> unusable)) {

      // when / then - a tenant that cannot even start must not hold the gate shut forever, and
      // must not abort startup either: this is our defect, not a diagnosis of the deployment
      initialization.start();
      assertThatNoException().isThrownBy(initialization::awaitGate);
      assertThat(initialization.isInitialized(TENANT_A)).isFalse();
    }
  }

  @Test
  void shouldOpenTheGateWhenTheTerminalClassificationItselfFails() {
    // given - the classification is caller-supplied, so it is one more thing that can throw from
    // an unexpected place in the loop
    try (final var initialization =
        new PerTenantSchemaInitialization(
            Set.of(TENANT_A),
            tenantId -> {
              throw new IllegalStateException("storage unreachable");
            },
            failure -> {
              throw new IllegalArgumentException("the classification is broken");
            },
            retryConfig())) {

      // when / then - a broken classification cannot classify anything as terminal, so the node
      // is released unable to serve rather than aborted on a diagnosis that was never made
      initialization.start();
      assertThatNoException().isThrownBy(initialization::awaitGate);
      assertThat(initialization.isInitialized(TENANT_A)).isFalse();
    }
  }

  @Test
  void shouldOpenTheGateWhenATenantsTaskDiesWithAnError() {
    // given - an Error is not an Exception, so no catch in the loop sees it and only the finally
    // is left to stop the tenant counting as still trying. The stack trace this prints is the
    // JVM's default handler and is expected.
    try (final var initialization =
        initialization(
            Set.of(TENANT_A),
            tenantId -> {
              throw new DeliberateError();
            })) {

      // when / then
      initialization.start();
      assertThatNoException().isThrownBy(initialization::awaitGate);
      assertThat(initialization.isInitialized(TENANT_A)).isFalse();
    }
  }

  @Test
  void shouldOpenTheGateOnClose() throws Exception {
    // given - attempts that do not observe interruption, standing in for a storage client blocked
    // in a socket read
    final var blockForever = new CountDownLatch(1);
    final var initialization =
        initialization(bothTenants(), tenantId -> awaitUninterruptibly(blockForever));
    try {
      final var gateOpened = startInBackground(initialization);
      assertThat(gateOpened.await(200, TimeUnit.MILLISECONDS)).isFalse();

      // when
      initialization.close();

      // then - shutdown does not wait for the stuck attempts
      assertThat(gateOpened.await(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      blockForever.countDown();
      initialization.close();
    }
  }

  @Test
  void shouldNotHoldTheCallerThatOnlyStartsTheTasks() {
    // given - a node with no HTTP gateway, whose exporter retries per partition and which has no
    // consumer that benefits from waiting
    final var blockForever = new CountDownLatch(1);
    final var initialization =
        initialization(bothTenants(), tenantId -> awaitUninterruptibly(blockForever));
    try {
      // when / then - starting returns even though no tenant can ever settle
      initialization.start();
      assertThat(initialization.isInitialized(TENANT_A)).isFalse();
    } finally {
      blockForever.countDown();
      initialization.close();
    }
  }

  @Test
  void shouldHoldTheGateUntilATenantRecoversFromTransientFailures() {
    // given - the only tenant's storage accepts the third attempt
    final var attempts = new AtomicInteger();
    try (final var initialization =
        initialization(
            Set.of(TENANT_B),
            tenantId -> {
              if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("storage is still starting");
              }
            })) {

      // when - the retry loop keeps going until the tenant recovers, with no restart and no
      // operator action
      initialization.start();
      initialization.awaitGate();

      // then - the gate opened on the tenant becoming serviceable, not on its first failure
      assertThat(initialization.isInitialized(TENANT_B)).isTrue();
      assertThat(attempts).hasValue(3);
    }
  }

  @Test
  void shouldRecoverADegradedTenantInTheBackgroundAfterTheGateOpened() {
    // given - one healthy tenant, and one whose storage only accepts the third attempt
    final var attempts = new AtomicInteger();
    try (final var initialization =
        initialization(
            bothTenants(),
            tenantId -> {
              if (TENANT_B.equals(tenantId) && attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("storage unreachable");
              }
            })) {

      // when - the gate opens on the healthy tenant
      initialization.start();
      initialization.awaitGate();
      assertThat(initialization.isInitialized(TENANT_A)).isTrue();

      // then - the degraded tenant is still retried, and recovers on its own
      Awaitility.await("tenant B recovers without a restart")
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(initialization.isInitialized(TENANT_B)).isTrue());
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

      // when - the only tenant is terminal, so the gate aborts as well as stopping the retries
      initialization.start();
      assertThatThrownBy(initialization::awaitGate)
          .isInstanceOf(EveryTenantTerminallyFailedException.class);

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
      initialization.start();
      assertThatThrownBy(initialization::awaitGate)
          .isInstanceOf(EveryTenantTerminallyFailedException.class);

      // then - the wrapped cause is classified, so it both stops the retries and reaches the abort
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

      // when - the gate is deliberately not awaited: a tenant that keeps retrying holds it, which
      // is what shouldHoldTheGateWhileTheOnlyTenantKeepsFailing asserts
      initialization.start();

      // then
      Awaitility.await("the tenant keeps being retried")
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(attempts.get()).isGreaterThan(1));
    }
  }

  @Test
  void shouldReportUnknownTenantAsNotInitialized() {
    // given
    try (final var initialization = initialization(Set.of(TENANT_A), tenantId -> {})) {
      // when
      initialization.start();
      initialization.awaitGate();

      // then
      assertThat(initialization.isInitialized(TENANT_A)).isTrue();
      assertThat(initialization.isInitialized("never-configured")).isFalse();
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
    initialization.start();
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
    initialization.start();
    initialization.awaitGate();

    // then - the gate opens without blocking, and no tenant is claimed as initialized
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
      initialization.start();
      initialization.awaitGate();

      // then
      assertThat(requestedFor).containsExactlyInAnyOrder(TENANT_A, TENANT_B);
    }
  }

  @Test
  void shouldBeIdempotentOnClose() {
    // given
    final var initialization = initialization(Set.of(TENANT_A), tenantId -> {});
    initialization.start();
    initialization.awaitGate();

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
      initialization.start();
      initialization.awaitGate();

      // then
      assertThat(bothRunning.await(0, TimeUnit.SECONDS)).isTrue();
      assertThat(initialization.isInitialized(TENANT_A)).isTrue();
      assertThat(initialization.isInitialized(TENANT_B)).isTrue();
    }
  }

  @Test
  void shouldTolerateNoTenants() {
    // given - a deployment with no search-engine tenant must not hold startup: every tenant has
    // settled vacuously, and none is trying
    try (final var initialization = initialization(Set.of(), tenantId -> {})) {
      // when / then
      initialization.start();
      initialization.awaitGate();
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
      initialization.start();
      initialization.awaitGate();

      // then
      assertThat(initialization.isInitialized("a")).isTrue();
      assertThat(initialization.isInitialized("b")).isFalse();
      assertThat(initialization.isInitialized("c")).isTrue();
    }
  }

  /** Runs the gate wait off the test thread, so that "the gate stays shut" is assertable. */
  private static CountDownLatch startInBackground(
      final PerTenantSchemaInitialization initialization) {
    final var gateOpened = new CountDownLatch(1);
    initialization.start();
    Thread.ofPlatform()
        .name("test-startup")
        .start(
            () -> {
              initialization.awaitGate();
              gateOpened.countDown();
            });
    return gateOpened;
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

  /**
   * Milliseconds rather than the production seconds, so retries are observable within a test.
   * Retries are unbounded, as {@code SchemaManagerRetryConfiguration} makes them in production —
   * the plain {@link RetryConfiguration} default is three attempts, which would quietly turn every
   * "keeps failing" case into an "exhausted its retries" one.
   */
  private static RetryConfiguration fastRetry() {
    final var retry = new RetryConfiguration();
    retry.setMaxRetries(Integer.MAX_VALUE);
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

  /** Stands in for anything that kills a worker without passing through a catch block. */
  private static final class DeliberateError extends Error {
    private DeliberateError() {
      super("deliberate: exercises the finally that stops a tenant counting as still trying");
    }
  }
}
