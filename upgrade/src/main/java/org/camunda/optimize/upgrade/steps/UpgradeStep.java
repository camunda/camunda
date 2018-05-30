package org.camunda.optimize.upgrade.steps;


import org.camunda.optimize.upgrade.es.ESIndexAdjuster;

public interface UpgradeStep {

  void execute(ESIndexAdjuster ESIndexAdjuster);
}
