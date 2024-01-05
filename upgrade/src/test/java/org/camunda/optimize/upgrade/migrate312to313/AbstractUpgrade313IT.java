/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate312to313;

import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanRegistry;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

public abstract class AbstractUpgrade313IT extends AbstractUpgradeIT {

  protected static final String FROM_VERSION = "3.12.0";
  protected static final String TO_VERSION = "3.13.0";

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(List.of());
    setMetadataVersion(FROM_VERSION);
  }

  protected void performUpgrade() {
    final List<UpgradePlan> upgradePlans =
      new UpgradePlanRegistry(upgradeDependencies).getSequentialUpgradePlansToTargetVersion(TO_VERSION);
    upgradePlans.forEach(plan -> upgradeProcedure.performUpgrade(plan));
  }

}
