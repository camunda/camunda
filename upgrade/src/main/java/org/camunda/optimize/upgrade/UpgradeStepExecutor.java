package org.camunda.optimize.upgrade;


public interface UpgradeStepExecutor <T extends UpgradeStep> {
  void execute(T step) throws Exception;
}
