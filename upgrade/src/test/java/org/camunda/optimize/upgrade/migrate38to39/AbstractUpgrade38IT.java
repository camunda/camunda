/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate38to39;

import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate38to39.indices.CollectionIndex38;
import org.camunda.optimize.upgrade.migrate38to39.indices.DashboardIndex38;
import org.camunda.optimize.upgrade.migrate38to39.indices.SingleProcessReportIndex38;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanRegistry;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

public class AbstractUpgrade38IT extends AbstractUpgradeIT {
  protected static final String FROM_VERSION = "3.8.0";
  protected static final String TO_VERSION = "3.9.0-preview-1";

  protected static final SingleProcessReportIndex38 SINGLE_PROCESS_REPORT_INDEX = new SingleProcessReportIndex38();
  protected static final DashboardIndex38 DASHBOARD_INDEX = new DashboardIndex38();
  protected static final CollectionIndex38 COLLECTION_INDEX = new CollectionIndex38();

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(List.of(
      SINGLE_PROCESS_REPORT_INDEX,
      DASHBOARD_INDEX,
      COLLECTION_INDEX
    ));
    setMetadataVersion(FROM_VERSION);
  }

  protected void performUpgrade() {
    final List<UpgradePlan> upgradePlans =
      new UpgradePlanRegistry(upgradeDependencies).getSequentialUpgradePlansToTargetVersion(TO_VERSION);
    upgradePlans.forEach(plan -> upgradeProcedure.performUpgrade(plan));
  }

}