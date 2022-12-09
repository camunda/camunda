/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.upgrade.plan;

import com.vdurmont.semver4j.Semver;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.upgrade.steps.UpgradeStep;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PACKAGE)
@Slf4j
public class UpgradePlan {
  @Getter
  private final List<UpgradeStep> upgradeSteps = new ArrayList<>();
  @Getter
  @Setter
  private Semver toVersion;
  @Getter
  @Setter
  private Semver fromVersion;

  public void addUpgradeStep(UpgradeStep upgradeStep) {
    this.upgradeSteps.add(upgradeStep);
  }

  public void addUpgradeSteps(List<UpgradeStep> upgradeSteps) {
    this.upgradeSteps.addAll(upgradeSteps);
  }

}
