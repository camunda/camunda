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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClusterUpgradeStatusServicesTest {

  @Test
  void shouldReportUnknownWhenNoProviderIsRegistered() {
    // given - staged rollout: not every provider exists yet
    final var services = services();

    // when / then
    assertThat(statusOf(services)).isEqualTo(MigrationState.UNKNOWN);
  }

  @Test
  void shouldReportMigratedWhenEveryConditionIsMigratedForEveryTenant() {
    // given
    final var services =
        services(
            provider("a", Map.of("default", migrated("a done"))),
            provider("b", Map.of("default", migrated("b done"))));

    // when / then
    assertThat(statusOf(services)).isEqualTo(MigrationState.MIGRATED);
  }

  @Test
  void shouldReportMigrationInProgressWhenOneConditionIsInProgress() {
    // given - one condition confirmed migrated, another confidently not yet
    final var services =
        services(
            provider("a", Map.of("default", migrated("a done"))),
            provider("b", Map.of("default", inProgress("b behind"))));

    // when / then
    assertThat(statusOf(services)).isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
  }

  @Test
  void shouldReportUnknownWhenOneConditionIsUnknownButNoneIsInProgress() {
    // given - unknown beats migrated, but loses to migration-in-progress
    final var services =
        services(
            provider("a", Map.of("default", migrated("a done"))),
            provider("b", Map.of("default", unknown("b lookup failed"))));

    // when / then
    assertThat(statusOf(services)).isEqualTo(MigrationState.UNKNOWN);
  }

  @Test
  void shouldPreferMigrationInProgressOverUnknown() {
    // given - both an inconclusive and a confidently-incomplete condition are present
    final var services =
        services(
            provider("a", Map.of("default", unknown("a lookup failed"))),
            provider("b", Map.of("default", inProgress("b behind"))));

    // when / then
    assertThat(statusOf(services)).isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
  }

  private static MigrationConditionStatus migrated(final String detail) {
    return new MigrationConditionStatus(MigrationState.MIGRATED, detail);
  }

  private static MigrationConditionStatus inProgress(final String detail) {
    return new MigrationConditionStatus(MigrationState.MIGRATION_IN_PROGRESS, detail);
  }

  private static MigrationConditionStatus unknown(final String detail) {
    return new MigrationConditionStatus(MigrationState.UNKNOWN, detail);
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

  private static ClusterUpgradeStatusServices services(final MigrationStatusProvider... providers) {
    return new ClusterUpgradeStatusServices(new MigrationStatusAggregator(List.of(providers)));
  }

  private static MigrationState statusOf(final ClusterUpgradeStatusServices services) {
    return services.getStatus().join();
  }
}
