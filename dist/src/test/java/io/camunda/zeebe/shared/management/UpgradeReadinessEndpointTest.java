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
import io.camunda.service.MigrationStatusAggregator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class UpgradeReadinessEndpointTest {

  @Test
  void shouldExposeEveryRegisteredProviderUnderItsConditionNamePerPhysicalTenant() {
    // given
    final MigrationStatusProvider provider =
        new MigrationStatusProvider() {
          @Override
          public String conditionName() {
            return "rdbmsSchemaMigrated";
          }

          @Override
          public Map<String, MigrationConditionStatus> getMigrationStatus() {
            return Map.of(
                "default",
                new MigrationConditionStatus(MigrationState.MIGRATED, "schema is current"));
          }
        };
    final var endpoint =
        new UpgradeReadinessEndpoint(new MigrationStatusAggregator(List.of(provider)));

    // when
    final var response = endpoint.getUpgradeReadiness();

    // then
    assertThat(response.upgradeable()).isTrue();
    assertThat(response.physicalTenants()).containsOnlyKeys("default");
    assertThat(response.physicalTenants().get("default")).containsOnlyKeys("rdbmsSchemaMigrated");
    assertThat(response.physicalTenants().get("default").get("rdbmsSchemaMigrated").state())
        .isEqualTo(MigrationState.MIGRATED);
  }

  @Test
  void shouldReportNotUpgradeableWhenNoProviderIsRegisteredYet() {
    // given - staged rollout, before any MigrationStatusProvider bean exists
    final var endpoint = new UpgradeReadinessEndpoint(new MigrationStatusAggregator(List.of()));

    // when
    final var response = endpoint.getUpgradeReadiness();

    // then
    assertThat(response.upgradeable()).isFalse();
    assertThat(response.physicalTenants()).isEmpty();
  }

  @Test
  void shouldReportNotUpgradeableWhenAConditionIsInProgress() {
    // given
    final MigrationStatusProvider provider =
        new MigrationStatusProvider() {
          @Override
          public String conditionName() {
            return "rdbmsSchemaMigrated";
          }

          @Override
          public Map<String, MigrationConditionStatus> getMigrationStatus() {
            return Map.of(
                "default",
                new MigrationConditionStatus(
                    MigrationState.MIGRATION_IN_PROGRESS, "schema migration running"));
          }
        };
    final var endpoint =
        new UpgradeReadinessEndpoint(new MigrationStatusAggregator(List.of(provider)));

    // when
    final var response = endpoint.getUpgradeReadiness();

    // then
    assertThat(response.upgradeable()).isFalse();
    assertThat(response.physicalTenants().get("default").get("rdbmsSchemaMigrated").state())
        .isEqualTo(MigrationState.MIGRATION_IN_PROGRESS);
  }
}
