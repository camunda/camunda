/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate38to39;


import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate38to39.indices.DashboardIndexOld;
import org.camunda.optimize.upgrade.migrate38to39.indices.CollectionIndexOld;
import org.camunda.optimize.upgrade.migrate38to39.indices.SingleProcessReportIndexOld;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanRegistry;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

public class AbstractUpgrade38IT extends AbstractUpgradeIT {
  protected static final String FROM_VERSION = "3.8.0";
  protected static final String TO_VERSION = "3.9.0";

  protected static final SingleProcessReportIndexOld SINGLE_PROCESS_REPORT_INDEX = new SingleProcessReportIndexOld();
  protected static final DashboardIndexOld DASHBOARD_INDEX = new DashboardIndexOld();
  protected static final CollectionIndexOld COLLECTION_INDEX = new CollectionIndexOld();

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