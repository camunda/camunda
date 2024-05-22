/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.migrate313to314;

import java.util.List;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.migrate313to314.indices.OnboardingStateIndexV2;
import org.camunda.optimize.upgrade.migrate313to314.indices.SettingsIndexV2;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.camunda.optimize.upgrade.plan.UpgradePlanRegistry;
import org.junit.jupiter.api.BeforeEach;

public class AbstractUpgrade314IT extends AbstractUpgradeIT {

  protected static final String FROM_VERSION = "3.13.0";
  protected static final String TO_VERSION = "3.14.0";

  protected final OnboardingStateIndexV2 ONBOARDING_STATE_INDEX = new OnboardingStateIndexV2();
  protected final SettingsIndexV2 SETTINGS_INDEX = new SettingsIndexV2();

  @BeforeEach
  protected void setUp() throws Exception {
    super.setUp();
    initSchema(List.of(ONBOARDING_STATE_INDEX, SETTINGS_INDEX));
    setMetadataVersion(FROM_VERSION);
  }

  protected void performUpgrade() {
    final List<UpgradePlan> upgradePlans =
        new UpgradePlanRegistry(upgradeDependencies)
            .getSequentialUpgradePlansToTargetVersion(TO_VERSION);
    upgradePlans.forEach(plan -> upgradeProcedure.performUpgrade(plan));
  }
}
