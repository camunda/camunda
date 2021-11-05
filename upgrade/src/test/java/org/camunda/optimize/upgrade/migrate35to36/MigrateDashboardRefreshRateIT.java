/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.migrate35to36;

import lombok.SneakyThrows;
import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.factories.Upgrade36To37PlanFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateDashboardRefreshRateIT extends AbstractUpgrade36IT {

  @SneakyThrows
  @Test
  public void dashboardInstanceDateFiltersAreMigrated() {
    // given
    executeBulk("steps/3.6/dashboard/36-dashboards-with-date-filters.json");
    final UpgradePlan upgradePlan = new Upgrade36To37PlanFactory().createUpgradePlan(upgradeDependencies);

    // when
    upgradeProcedure.performUpgrade(upgradePlan);

    // then
    assertThat(getAllDocumentsOfIndex(DASHBOARD_INDEX.getIndexName()))
      .hasSize(3)
      .allSatisfy(dashboard -> assertThat(dashboard.getSourceAsMap()).containsEntry(
        DashboardIndex.REFRESH_RATE_SECONDS,
        null
      ));
  }

}
