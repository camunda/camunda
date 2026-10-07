/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.migration.MigrationConditionStatus;
import io.camunda.cluster.migration.MigrationState;
import io.camunda.cluster.migration.MigrationStatusProvider;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class MigrationStatusAggregatorTest {

  @Test
  void shouldPollEveryProviderConcurrentlyRatherThanOneAtATime() {
    // given - two slow providers, each blocking until the other has started
    final var firstStarted = new CountDownLatch(1);
    final var secondStarted = new CountDownLatch(1);
    final var aggregator =
        new MigrationStatusAggregator(
            List.of(
                blockingProvider("a", firstStarted, secondStarted),
                blockingProvider("b", secondStarted, firstStarted)));

    // when
    final var physicalTenants = aggregator.aggregate();

    // then - both providers reported their status, so neither call was left waiting forever
    assertThat(physicalTenants.get("default")).hasSize(2);
  }

  @Test
  void shouldPollOnTheGivenExecutorInsteadOfTheCommonPool() {
    // given - a dedicated single-thread executor with a recognizable thread name, so a poll
    // routed through the common pool instead would be caught rather than passing by coincidence
    final var executor =
        Executors.newSingleThreadExecutor(r -> new Thread(r, "aggregate-test-executor"));
    final var observedThreadNames = new CopyOnWriteArrayList<String>();
    final var aggregator =
        new MigrationStatusAggregator(
            List.of(threadRecordingProvider("a", observedThreadNames)),
            Duration.ofSeconds(5),
            executor);

    try {
      // when
      aggregator.aggregate();

      // then
      assertThat(observedThreadNames).containsExactly("aggregate-test-executor");
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void shouldReportNoPhysicalTenantsWhenNoProviderIsRegistered() {
    // given - staged rollout: not every provider exists yet
    final var aggregator = new MigrationStatusAggregator(List.of());

    // when
    final var physicalTenants = aggregator.aggregate();

    // then - an empty condition set must never be mistaken for readiness
    assertThat(physicalTenants).isEmpty();
  }

  @Test
  void shouldReportEveryConditionForEveryPhysicalTenant() {
    // given
    final var aggregator =
        new MigrationStatusAggregator(
            List.of(
                provider("a", Map.of("default", migrated("a done"))),
                provider("b", Map.of("default", migrated("b done")))));

    // when
    final var physicalTenants = aggregator.aggregate();

    // then
    assertThat(physicalTenants.get("default")).hasSize(2);
  }

  @Test
  void shouldReportEachPhysicalTenantIndependently() {
    // given - a provider covering two physical tenants, one ready, one not
    final var aggregator =
        new MigrationStatusAggregator(
            List.of(
                provider(
                    "a",
                    Map.of(
                        "tenantA", migrated("a done"),
                        "tenantB", inProgress("b behind")))));

    // when
    final var physicalTenants = aggregator.aggregate();

    // then
    assertThat(physicalTenants.get("tenantA").get("a").state()).isEqualTo(MigrationState.MIGRATED);
    assertThat(physicalTenants.get("tenantB").get("a").state())
        .isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
  }

  @Test
  void shouldKeepEachConditionIndependentPerTenant() {
    // given
    final var aggregator =
        new MigrationStatusAggregator(
            List.of(
                provider("a", Map.of("default", migrated("a done"))),
                provider("b", Map.of("default", inProgress("b behind")))));

    // when
    final var physicalTenants = aggregator.aggregate();

    // then
    assertThat(physicalTenants.get("default").get("a").state()).isEqualTo(MigrationState.MIGRATED);
    assertThat(physicalTenants.get("default").get("b").state())
        .isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
  }

  @Test
  void shouldReportUnknownInsteadOfThrowingWhenAProviderThrows() {
    // given - the only provider throws, and no tenant is known yet from anywhere else
    final var aggregator = new MigrationStatusAggregator(List.of(throwingProvider("a", "boom")));

    // when
    final var physicalTenants = aggregator.aggregate();

    // then - a broken provider must never crash the endpoint; with no known tenant, there's
    // simply nothing to report yet
    assertThat(physicalTenants).isEmpty();
  }

  @Test
  void shouldBackfillUnknownWhenAProviderThrowsForATenantKnownFromAnotherProvider() {
    // given - "default" is known via provider "a"; provider "b" fails the entire poll
    final var aggregator =
        new MigrationStatusAggregator(
            List.of(
                provider("a", Map.of("default", migrated("a done"))),
                throwingProvider("b", "boom")));

    // when
    final var physicalTenants = aggregator.aggregate();

    // then - "b" must not silently disappear for "default": a gap is UNKNOWN, never omission
    final var conditions = physicalTenants.get("default");
    assertThat(conditions.get("a").state()).isEqualTo(MigrationState.MIGRATED);
    assertThat(conditions.get("b").state()).isEqualTo(MigrationState.UNKNOWN);
    assertThat(conditions.get("b").detail()).contains("no status reported");
  }

  @Test
  void shouldReportUnknownOnANewPollWhenAPreviouslyMigratedProviderStartsThrowing() {
    // given - a provider that reports MIGRATED once, then throws entirely afterwards (e.g. a
    // later distributed fan-out breaking)
    final var flakyProvider = new FlakyProvider("a", migrated("done"));
    final var aggregator = new MigrationStatusAggregator(List.of(flakyProvider));

    // when - first poll confirms MIGRATED, second poll's provider call fails entirely
    final var firstPhysicalTenants = aggregator.aggregate();
    final var secondPhysicalTenants = aggregator.aggregate();

    // then - each poll reflects only what it itself observed; a failure is reported as UNKNOWN
    // rather than silently reusing the earlier MIGRATED result, since that could go stale
    assertThat(firstPhysicalTenants.get("default").get("a").state())
        .isEqualTo(MigrationState.MIGRATED);
    assertThat(secondPhysicalTenants.get("default").get("a").state())
        .isEqualTo(MigrationState.UNKNOWN);
  }

  @Test
  void shouldReportUnknownOnANewPollWhenAProviderStopsRespondingAfterANonMigratedStatus() {
    // given - a provider that never reaches MIGRATED, then throws
    final var flakyProvider = new FlakyProvider("a", inProgress("not yet"));
    final var aggregator = new MigrationStatusAggregator(List.of(flakyProvider));

    // when
    aggregator.aggregate();
    final var secondPhysicalTenants = aggregator.aggregate();

    // then - nothing from the first poll carries over; the backfill defaults to UNKNOWN
    assertThat(secondPhysicalTenants.get("default").get("a").state())
        .isEqualTo(MigrationState.UNKNOWN);
  }

  @Test
  void shouldReportUnknownForAProviderThatDoesNotAnswerInTime() throws Exception {
    // given - one provider answers, another hangs well past the provider timeout
    final var release = new CountDownLatch(1);
    final var hanging =
        new MigrationStatusProvider() {
          @Override
          public String conditionName() {
            return "hanging";
          }

          @Override
          public Map<String, MigrationConditionStatus> getMigrationStatus() {
            try {
              release.await(30, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            return Map.of("default", migrated("late"));
          }
        };
    final var aggregator =
        new MigrationStatusAggregator(
            List.of(provider("fast", Map.of("default", migrated("fast done"))), hanging),
            Duration.ofSeconds(1));

    try {
      // when - the poll must finish long before the hanging provider would
      final var result = aggregator.aggregateAsync().get(10, TimeUnit.SECONDS);

      // then
      assertThat(result.get("default").get("fast").state()).isEqualTo(MigrationState.MIGRATED);
      assertThat(result.get("default").get("hanging").state()).isEqualTo(MigrationState.UNKNOWN);
    } finally {
      release.countDown();
    }
  }

  @Test
  void shouldShareOnePollAmongConcurrentCallersWhileItIsInFlight() throws Exception {
    // given - a provider that stays busy until released, and counts how often it is asked
    final var release = new CountDownLatch(1);
    final var calls = new AtomicInteger();
    final var slow =
        new MigrationStatusProvider() {
          @Override
          public String conditionName() {
            return "slow";
          }

          @Override
          public Map<String, MigrationConditionStatus> getMigrationStatus() {
            calls.incrementAndGet();
            try {
              release.await(30, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            return Map.of("default", migrated("done"));
          }
        };
    final var aggregator = new MigrationStatusAggregator(List.of(slow), Duration.ofSeconds(30));

    try {
      // when - several callers arrive while the first poll is still running
      final var first = aggregator.aggregateAsync();
      final var second = aggregator.aggregateAsync();
      final var third = aggregator.aggregateAsync();
      release.countDown();

      // then - they all see the same poll and the provider was asked once
      for (final var poll : List.of(first, second, third)) {
        assertThat(poll.get(10, TimeUnit.SECONDS).get("default").get("slow").state())
            .isEqualTo(MigrationState.MIGRATED);
      }
      assertThat(calls).hasValue(1);
    } finally {
      release.countDown();
    }
  }

  @Test
  void shouldNotDeadlockWhenTheExecutorHasASingleThread() throws Exception {
    // given - a one-thread executor: the poll must not hold that thread while waiting for the
    // provider tasks that run on it
    final var executor = Executors.newSingleThreadExecutor();
    try {
      final var aggregator =
          new MigrationStatusAggregator(
              List.of(
                  provider("a", Map.of("default", migrated("a done"))),
                  provider("b", Map.of("default", migrated("b done"))),
                  provider("c", Map.of("default", migrated("c done")))),
              Duration.ofSeconds(5),
              executor);

      // when
      final var result = aggregator.aggregateAsync().get(10, TimeUnit.SECONDS);

      // then
      assertThat(result.get("default")).containsOnlyKeys("a", "b", "c");
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void shouldNotStartAnotherTaskForAProviderThatIsStillRunning() throws Exception {
    // given - a provider that stays busy far past the provider timeout, counting its invocations
    final var release = new CountDownLatch(1);
    final var calls = new AtomicInteger();
    final var stuck =
        new MigrationStatusProvider() {
          @Override
          public String conditionName() {
            return "stuck";
          }

          @Override
          public Map<String, MigrationConditionStatus> getMigrationStatus() {
            calls.incrementAndGet();
            try {
              release.await(30, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            return Map.of("default", migrated("done"));
          }
        };
    final var aggregator = new MigrationStatusAggregator(List.of(stuck), Duration.ofMillis(100));

    try {
      // when - repeated polls, each giving up on the stuck provider after the timeout
      for (int i = 0; i < 5; i++) {
        aggregator.aggregateAsync().get(10, TimeUnit.SECONDS);
      }

      // then - the provider was started once, not once per poll, so it holds one thread
      assertThat(calls).hasValue(1);
    } finally {
      release.countDown();
      aggregator.close();
    }
  }

  @Test
  void shouldReportAProviderAgainOnceItHasRecovered() throws Exception {
    // given - a provider that hangs on its first call, then answers at once
    final var release = new CountDownLatch(1);
    final var calls = new AtomicInteger();
    final var provider =
        new MigrationStatusProvider() {
          @Override
          public String conditionName() {
            return "recovering";
          }

          @Override
          public Map<String, MigrationConditionStatus> getMigrationStatus() {
            if (calls.incrementAndGet() == 1) {
              try {
                release.await(30, TimeUnit.SECONDS);
              } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }
            return Map.of("default", migrated("done"));
          }
        };
    final var aggregator = new MigrationStatusAggregator(List.of(provider), Duration.ofMillis(100));

    try {
      // when - the first poll gives up on it, then the provider recovers
      final var first = aggregator.aggregateAsync().get(10, TimeUnit.SECONDS);
      release.countDown();
      final var second = aggregator.aggregateAsync().get(10, TimeUnit.SECONDS);

      // then - a poll reuses the finishing task or starts a fresh one; either way it is not stuck
      assertThat(first).isEmpty();
      assertThat(second.get("default").get("recovering").state())
          .isEqualTo(MigrationState.MIGRATED);
    } finally {
      release.countDown();
      aggregator.close();
    }
  }

  @Test
  void shouldCompleteThePollEvenWhenAProviderHoldsTheOnlyExecutorThread() throws Exception {
    // given - an injected one-thread executor that a hung provider occupies, so the second provider
    // never even starts. The merge must not queue behind them on that executor.
    final var release = new CountDownLatch(1);
    final var executor = Executors.newSingleThreadExecutor();
    final var hanging =
        new MigrationStatusProvider() {
          @Override
          public String conditionName() {
            return "hanging";
          }

          @Override
          public Map<String, MigrationConditionStatus> getMigrationStatus() {
            try {
              release.await(30, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            return Map.of("default", migrated("late"));
          }
        };
    final var aggregator =
        new MigrationStatusAggregator(
            List.of(hanging, provider("queued", Map.of("default", migrated("queued done")))),
            Duration.ofMillis(300),
            executor);

    try {
      // when / then - bounded by the provider timeout, not by the hung provider
      assertThat(aggregator.aggregateAsync().get(10, TimeUnit.SECONDS)).isEmpty();
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void shouldFailTheFutureInsteadOfThrowingOnceTheExecutorIsShutDown() {
    // given
    final var aggregator =
        new MigrationStatusAggregator(
            List.of(provider("a", Map.of("default", migrated("a done")))), Duration.ofSeconds(1));
    aggregator.close();

    // when
    final var poll = aggregator.aggregateAsync();

    // then
    assertThat(poll)
        .failsWithin(5, TimeUnit.SECONDS)
        .withThrowableThat()
        .withCauseInstanceOf(RejectedExecutionException.class);
  }

  @Test
  void shouldAllowClosingMoreThanOnce() {
    // given
    final var aggregator =
        new MigrationStatusAggregator(
            List.of(provider("a", Map.of("default", migrated("a done")))), Duration.ofSeconds(1));

    // when / then
    aggregator.close();
    aggregator.close();
  }

  @Test
  void shouldNotRetainAnythingPerPollOnAProviderThatStaysHung() throws Exception {
    // given - a provider that never answers while the test runs
    final var release = new CountDownLatch(1);
    final var stuck =
        new MigrationStatusProvider() {
          @Override
          public String conditionName() {
            return "stuck";
          }

          @Override
          public Map<String, MigrationConditionStatus> getMigrationStatus() {
            try {
              release.await(30, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            return Map.of("default", migrated("done"));
          }
        };
    final var aggregator = new MigrationStatusAggregator(List.of(stuck), Duration.ofMillis(20));

    try {
      // when - many polls each give up on the same hung call
      for (int i = 0; i < 40; i++) {
        aggregator.aggregateAsync().get(10, TimeUnit.SECONDS);
      }

      // then - the hung call carries one observer and no waiting polls, not one per poll
      final var call = aggregator.runningCall(0);
      assertThat(call.dependentsOfCall()).isLessThanOrEqualTo(1);
      // A poll drops its waiter just after its result is delivered, so give that a moment.
      final var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (call.waitingPolls() != 0 && System.nanoTime() < deadline) {
        Thread.onSpinWait();
      }
      assertThat(call.waitingPolls()).isZero();
    } finally {
      release.countDown();
      aggregator.close();
    }
  }

  private static MigrationConditionStatus migrated(final String detail) {
    return new MigrationConditionStatus(MigrationState.MIGRATED, detail);
  }

  private static MigrationConditionStatus inProgress(final String detail) {
    return new MigrationConditionStatus(MigrationState.MIGRATION_IN_PROGRESS, detail);
  }

  /**
   * A provider whose {@code getMigrationStatus()} signals {@code ownStart}, then blocks until
   * {@code otherStart} is also signalled -- used in pairs to prove two providers' calls actually
   * overlap, rather than the second one only starting once the first has already returned.
   */
  private static MigrationStatusProvider blockingProvider(
      final String name, final CountDownLatch ownStart, final CountDownLatch otherStart) {
    return new MigrationStatusProvider() {
      @Override
      public String conditionName() {
        return name;
      }

      @Override
      public Map<String, MigrationConditionStatus> getMigrationStatus() {
        ownStart.countDown();
        try {
          if (!otherStart.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError(
                "the other provider never started -- aggregate() is not polling providers"
                    + " concurrently");
          }
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException(e);
        }
        return Map.of("default", migrated(name + " done"));
      }
    };
  }

  /** A provider that records the name of the thread {@code getMigrationStatus()} ran on. */
  private static MigrationStatusProvider threadRecordingProvider(
      final String name, final List<String> observedThreadNames) {
    return new MigrationStatusProvider() {
      @Override
      public String conditionName() {
        return name;
      }

      @Override
      public Map<String, MigrationConditionStatus> getMigrationStatus() {
        observedThreadNames.add(Thread.currentThread().getName());
        return Map.of("default", migrated(name + " done"));
      }
    };
  }

  private static MigrationStatusProvider provider(
      final String name, final Map<String, MigrationConditionStatus> statuses) {
    return new MigrationStatusProvider() {
      @Override
      public String conditionName() {
        return name;
      }

      @Override
      public Map<String, MigrationConditionStatus> getMigrationStatus() {
        return statuses;
      }
    };
  }

  private static MigrationStatusProvider throwingProvider(final String name, final String message) {
    return new MigrationStatusProvider() {
      @Override
      public String conditionName() {
        return name;
      }

      @Override
      public Map<String, MigrationConditionStatus> getMigrationStatus() {
        throw new RuntimeException(message);
      }
    };
  }

  /** A provider that returns {@code firstStatus} for tenant "default" once, then throws. */
  private static final class FlakyProvider implements MigrationStatusProvider {
    private final String name;
    private final MigrationConditionStatus firstStatus;
    private boolean calledOnce = false;

    private FlakyProvider(final String name, final MigrationConditionStatus firstStatus) {
      this.name = name;
      this.firstStatus = firstStatus;
    }

    @Override
    public String conditionName() {
      return name;
    }

    @Override
    public Map<String, MigrationConditionStatus> getMigrationStatus() {
      if (!calledOnce) {
        calledOnce = true;
        return Map.of("default", firstStatus);
      }
      throw new RuntimeException("flaky provider failure");
    }
  }
}
