/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.plan.factories;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.service.db.es.schema.index.BusinessValueOverviewIndexES;
import io.camunda.optimize.service.db.es.schema.index.BusinessValueTargetIndexES;
import io.camunda.optimize.service.db.es.schema.index.JobRegistryIndexES;
import io.camunda.optimize.service.db.os.schema.index.BusinessValueOverviewIndexOS;
import io.camunda.optimize.service.db.os.schema.index.BusinessValueTargetIndexOS;
import io.camunda.optimize.service.db.os.schema.index.JobRegistryIndexOS;
import io.camunda.optimize.service.db.schema.IndexMappingCreator;
import io.camunda.optimize.upgrade.AbstractUpgradeIT;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link Upgrade89to810PlanFactory} creates the {@code business-value-target}, {@code
 * business-value-overview}, and {@code job-registry} indices on an 8.9 cluster, so upgraded and
 * fresh-install schemas stay in parity.
 */
public class Upgrade89to810PlanFactoryIT extends AbstractUpgradeIT {

  private static final String FROM_MINOR = "8.9";
  private static final String FROM_PATCH = "8.9.0";

  @Test
  public void shouldCreateBusinessValueTargetIndexOnUpgrade() {
    // given an 8.9 cluster (schema version is set to the previous minor)
    setMetadataVersion(FROM_PATCH);
    final IndexMappingCreator businessValueTargetIndex = businessValueTargetIndex();
    assertThat(getIndicesForMapping(businessValueTargetIndex))
        .as("business-value-target index must not exist before the upgrade runs")
        .isEmpty();

    // when the 8.9 -> 8.10 upgrade plan runs
    final UpgradePlan plan = new Upgrade89to810PlanFactory().createUpgradePlan(upgradeDependencies);
    upgradeProcedure.performUpgrade(plan);

    // then the business-value-target index exists in the database
    assertThat(getIndicesForMapping(businessValueTargetIndex))
        .as("business-value-target index must exist after the upgrade runs")
        .isNotEmpty();
  }

  @Test
  public void shouldCreateBusinessValueOverviewIndexOnUpgrade() {
    // given an 8.9 cluster (schema version is set to the previous minor)
    setMetadataVersion(FROM_PATCH);
    final IndexMappingCreator businessValueOverviewIndex = businessValueOverviewIndex();
    assertThat(getIndicesForMapping(businessValueOverviewIndex))
        .as("business-value-overview index must not exist before the upgrade runs")
        .isEmpty();

    // when the 8.9 -> 8.10 upgrade plan runs
    final UpgradePlan plan = new Upgrade89to810PlanFactory().createUpgradePlan(upgradeDependencies);
    upgradeProcedure.performUpgrade(plan);

    // then the business-value-overview index exists in the database
    assertThat(getIndicesForMapping(businessValueOverviewIndex))
        .as("business-value-overview index must exist after the upgrade runs")
        .isNotEmpty();
  }

  @Test
  public void shouldCreateJobRegistryIndexOnUpgrade() {
    // given an 8.9 cluster (schema version is set to the previous minor)
    setMetadataVersion(FROM_PATCH);
    final IndexMappingCreator jobRegistryIndex = jobRegistryIndex();
    assertThat(getIndicesForMapping(jobRegistryIndex))
        .as("job-registry index must not exist before the upgrade runs")
        .isEmpty();

    // when the 8.9 -> 8.10 upgrade plan runs
    final UpgradePlan plan = new Upgrade89to810PlanFactory().createUpgradePlan(upgradeDependencies);
    upgradeProcedure.performUpgrade(plan);

    // then the job-registry index exists in the database
    assertThat(getIndicesForMapping(jobRegistryIndex))
        .as("job-registry index must exist after the upgrade runs")
        .isNotEmpty();
  }

  private IndexMappingCreator businessValueTargetIndex() {
    return isElasticSearchUpgrade()
        ? new BusinessValueTargetIndexES()
        : new BusinessValueTargetIndexOS();
  }

  private IndexMappingCreator businessValueOverviewIndex() {
    return isElasticSearchUpgrade()
        ? new BusinessValueOverviewIndexES()
        : new BusinessValueOverviewIndexOS();
  }

  private IndexMappingCreator jobRegistryIndex() {
    return isElasticSearchUpgrade() ? new JobRegistryIndexES() : new JobRegistryIndexOS();
  }
}
