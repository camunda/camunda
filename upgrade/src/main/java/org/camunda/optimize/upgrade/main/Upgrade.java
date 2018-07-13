package org.camunda.optimize.upgrade.main;

public interface Upgrade {

  String getInitialVersion();
  String getTargetVersion();

  void performUpgrade();
}
