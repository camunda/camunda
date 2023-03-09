/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate39To310;

import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate39To310.indices.DashboardIndexV6;
import org.camunda.optimize.upgrade.migrate39To310.indices.DashboardShareIndexV3;
import org.camunda.optimize.upgrade.migrate39To310.indices.PositionBasedImportIndexV2;
import org.camunda.optimize.upgrade.migrate39To310.indices.SingleProcessReportIndexV9;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanRegistry;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

public class AbstractUpgrade310IT extends AbstractUpgradeIT {
  protected static final String FROM_VERSION = "3.9.0";
  protected static final String TO_VERSION = "3.10.0";
  protected final SingleProcessReportIndexV9 SINGLE_REPORT_INDEX = new SingleProcessReportIndexV9();
  protected final DashboardIndexV6 DASHBOARD_INDEX = new DashboardIndexV6();
  protected final DashboardShareIndexV3 DASHBOARD_SHARE_INDEX = new DashboardShareIndexV3();
  protected final PositionBasedImportIndexV2 POSITION_BASED_IMPORT_INDEX = new PositionBasedImportIndexV2();

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(List.of(DASHBOARD_INDEX, POSITION_BASED_IMPORT_INDEX, SINGLE_REPORT_INDEX, DASHBOARD_SHARE_INDEX));
    setMetadataVersion(FROM_VERSION);
  }

  protected void performUpgrade() {
    final List<UpgradePlan> upgradePlans =
      new UpgradePlanRegistry(upgradeDependencies).getSequentialUpgradePlansToTargetVersion(TO_VERSION);
    upgradePlans.forEach(plan -> upgradeProcedure.performUpgrade(plan));
  }
}
