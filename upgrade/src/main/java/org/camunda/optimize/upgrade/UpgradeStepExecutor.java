package org.camunda.optimize.upgrade;

/**
 * @author Askar Akhmerov
 */
public interface UpgradeStepExecutor <T extends UpgradeStep> {
  void execute(T step) throws Exception;
}
