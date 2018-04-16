package org.camunda.optimize.upgrade.plan;

import org.camunda.optimize.upgrade.UpgradeStep;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Askar Akhmerov
 */
public abstract class AbstractUpgradePlan {
  protected static final List<UpgradeStep> UPGRADE_STEPS = new ArrayList<>();

  public List<UpgradeStep> getUpgradeSteps() {
    return UPGRADE_STEPS;
  }
}
