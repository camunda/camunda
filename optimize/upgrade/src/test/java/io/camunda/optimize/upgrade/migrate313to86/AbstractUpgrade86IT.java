/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.migrate313to86;

import io.camunda.optimize.upgrade.AbstractUpgradeIT;
import io.camunda.optimize.upgrade.migrate313to86.indices.OnboardingStateIndexV2;
import io.camunda.optimize.upgrade.migrate313to86.indices.SettingsIndexV2;
import io.camunda.optimize.upgrade.plan.UpgradePlan;
import io.camunda.optimize.upgrade.plan.UpgradePlanRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;

public class AbstractUpgrade86IT extends AbstractUpgradeIT {

  protected static final String FROM_VERSION = "3.13.0";
  protected static final String TO_VERSION = "8.6.0";

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
