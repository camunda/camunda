/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.cluster.migration.MigrationConditionStatus;
import io.camunda.cluster.migration.MigrationState;
import io.camunda.cluster.migration.MigrationStatusProvider;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
    final var response = aggregator.aggregate();

    // then - both providers reported their status, so neither call was left waiting forever
    assertThat(response.physicalTenants().get("default")).hasSize(2);
  }

  @Test
  void shouldReportNotUpgradeableWhenNoProviderIsRegistered() {
    // given - staged rollout: not every provider exists yet
    final var aggregator = new MigrationStatusAggregator(List.of());

    // when
    final var response = aggregator.aggregate();

    // then - an empty condition set must never be mistaken for readiness
    assertThat(response.upgradeable()).isFalse();
    assertThat(response.physicalTenants()).isEmpty();
  }

  @Test
  void shouldReportUpgradeableOnlyWhenEveryConditionIsMigratedForEveryTenant() {
    // given
    final var aggregator =
        new MigrationStatusAggregator(
            List.of(
                provider("a", Map.of("default", migrated("a done"))),
                provider("b", Map.of("default", migrated("b done")))));

    // when
    final var response = aggregator.aggregate();

    // then
    assertThat(response.upgradeable()).isTrue();
    assertThat(response.physicalTenants().get("default")).hasSize(2);
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
    final var response = aggregator.aggregate();

    // then
    assertThat(response.upgradeable()).isFalse();
    assertThat(response.physicalTenants().get("tenantA").get("a").state())
        .isEqualTo(MigrationState.MIGRATED);
    assertThat(response.physicalTenants().get("tenantB").get("a").state())
        .isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
  }

  @Test
  void shouldReportNotUpgradeableWhenOneConditionIsNotMigratedForOneTenant() {
    // given
    final var aggregator =
        new MigrationStatusAggregator(
            List.of(
                provider("a", Map.of("default", migrated("a done"))),
                provider("b", Map.of("default", inProgress("b behind")))));

    // when
    final var response = aggregator.aggregate();

    // then
    assertThat(response.upgradeable()).isFalse();
    assertThat(response.physicalTenants().get("default").get("b").state())
        .isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
  }

  @Test
  void shouldReportUnknownInsteadOfThrowingWhenAProviderThrows() {
    // given - the only provider throws, and no tenant is known yet from anywhere else
    final var aggregator = new MigrationStatusAggregator(List.of(throwingProvider("a", "boom")));

    // when
    final var response = aggregator.aggregate();

    // then - a broken provider must never crash the endpoint; with no known tenant, there's
    // simply nothing to report yet
    assertThat(response.upgradeable()).isFalse();
    assertThat(response.physicalTenants()).isEmpty();
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
    final var response = aggregator.aggregate();

    // then - "b" must not silently disappear for "default": a gap is UNKNOWN, never omission
    assertThat(response.upgradeable()).isFalse();
    final var conditions = response.physicalTenants().get("default");
    assertThat(conditions.get("a").state()).isEqualTo(MigrationState.MIGRATED);
    assertThat(conditions.get("b").state()).isEqualTo(MigrationState.UNKNOWN);
    assertThat(conditions.get("b").detail()).contains("no status reported");
  }

  @Test
  void shouldNeverRegressAConfirmedMigratedConditionWhenAProviderThrowsOnALaterPoll() {
    // given - a provider that reports MIGRATED once, then throws entirely afterwards (e.g. a
    // later distributed fan-out breaking)
    final var flakyProvider = new FlakyProvider("a", migrated("done"));
    final var aggregator = new MigrationStatusAggregator(List.of(flakyProvider));

    // when - first poll confirms MIGRATED, second poll would otherwise lose the entry entirely
    final var firstResponse = aggregator.aggregate();
    final var secondResponse = aggregator.aggregate();

    // then - monotonicity is preserved via the backfill, even under total provider failure
    assertThat(firstResponse.physicalTenants().get("default").get("a").state())
        .isEqualTo(MigrationState.MIGRATED);
    assertThat(secondResponse.physicalTenants().get("default").get("a").state())
        .isEqualTo(MigrationState.MIGRATED);
  }

  @Test
  void shouldNotCacheANonMigratedStatus() {
    // given - a provider that never reaches MIGRATED, then throws
    final var flakyProvider = new FlakyProvider("a", inProgress("not yet"));
    final var aggregator = new MigrationStatusAggregator(List.of(flakyProvider));

    // when
    aggregator.aggregate();
    final var secondResponse = aggregator.aggregate();

    // then - nothing was ever confirmed MIGRATED, so the backfill falls back to UNKNOWN
    assertThat(secondResponse.physicalTenants().get("default").get("a").state())
        .isEqualTo(MigrationState.UNKNOWN);
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
