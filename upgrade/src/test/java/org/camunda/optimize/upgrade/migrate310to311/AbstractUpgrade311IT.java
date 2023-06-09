/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate310to311;

import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate310to311.indices.CombinedDecisionReportIndexV4;
import org.camunda.optimize.upgrade.migrate310to311.indices.DashboardIndexV7;
import org.camunda.optimize.upgrade.migrate310to311.indices.SingleDecisionReportIndexV9;
import org.camunda.optimize.upgrade.migrate310to311.indices.SingleProcessReportIndexV10;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanRegistry;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

public abstract class AbstractUpgrade311IT extends AbstractUpgradeIT {

  protected static final String FROM_VERSION = "3.10.0";
  protected static final String TO_VERSION = "3.11.0";
  protected final SingleProcessReportIndexV10 SINGLE_PROCESS_REPORT_INDEX = new SingleProcessReportIndexV10();
  protected final SingleDecisionReportIndexV9 SINGLE_DECISION_REPORT_INDEX = new SingleDecisionReportIndexV9();
  protected final CombinedDecisionReportIndexV4 COMBINED_REPORT_INDEX = new CombinedDecisionReportIndexV4();
  protected final DashboardIndexV7 DASHBOARD_INDEX = new DashboardIndexV7();

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(List.of(SINGLE_PROCESS_REPORT_INDEX, SINGLE_DECISION_REPORT_INDEX, COMBINED_REPORT_INDEX, DASHBOARD_INDEX));
    setMetadataVersion(FROM_VERSION);
  }

  protected void performUpgrade() {
    final List<UpgradePlan> upgradePlans =
      new UpgradePlanRegistry(upgradeDependencies).getSequentialUpgradePlansToTargetVersion(TO_VERSION);
    upgradePlans.forEach(plan -> upgradeProcedure.performUpgrade(plan));
  }

}
