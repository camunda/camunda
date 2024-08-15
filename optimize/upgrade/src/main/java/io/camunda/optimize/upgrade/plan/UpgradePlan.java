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
import org.slf4j.Logger;

public class UpgradePlan {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(UpgradePlan.class);
  private final List<UpgradeStep> upgradeSteps = new ArrayList<>();
  private Semver toVersion;
  private Semver fromVersion;

  UpgradePlan() {}

  public void addUpgradeStep(final UpgradeStep upgradeStep) {
    upgradeSteps.add(upgradeStep);
  }

  public void addUpgradeSteps(final List<? extends UpgradeStep> upgradeSteps) {
    this.upgradeSteps.addAll(upgradeSteps);
  }

  public List<UpgradeStep> getUpgradeSteps() {
    return upgradeSteps;
  }

  public Semver getToVersion() {
    return toVersion;
  }

  public void setToVersion(final Semver toVersion) {
    this.toVersion = toVersion;
  }

  public Semver getFromVersion() {
    return fromVersion;
  }

  public void setFromVersion(final Semver fromVersion) {
    this.fromVersion = fromVersion;
  }
}
