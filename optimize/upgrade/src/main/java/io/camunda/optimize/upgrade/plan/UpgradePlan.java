/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.upgrade.plan;

import com.vdurmont.semver4j.Semver;
import io.camunda.optimize.upgrade.steps.UpgradeStep;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@NoArgsConstructor(access = AccessLevel.PACKAGE)
@Slf4j
public class UpgradePlan {
  @Getter private final List<UpgradeStep> upgradeSteps = new ArrayList<>();
  @Getter @Setter private Semver toVersion;
  @Getter @Setter private Semver fromVersion;

  public void addUpgradeStep(UpgradeStep upgradeStep) {
    upgradeSteps.add(upgradeStep);
  }

  public void addUpgradeSteps(List<? extends UpgradeStep> upgradeSteps) {
    this.upgradeSteps.addAll(upgradeSteps);
  }
}
