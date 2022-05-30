/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate38to39;

import org.camunda.optimize.service.es.schema.index.DashboardIndex;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrateManagementDashboardIT extends AbstractUpgrade38IT {

  @Test
  public void existingDashboardsAreMarkedAsNonManagement() {
    // given
    executeBulk("steps/3.8/dashboard/38-dashboard.json");

    // when
    performUpgrade();

    // then
    assertThat(getAllDocumentsOfIndex(DASHBOARD_INDEX.getIndexName()))
      .singleElement()
      .satisfies(report -> {
        final Map<String, Object> dashboardAsMap = report.getSourceAsMap();
        final Boolean isManagementDashboard = (Boolean) dashboardAsMap.get(DashboardIndex.MANAGEMENT_DASHBOARD);
        assertThat(isManagementDashboard).isFalse();
      });
  }

}
