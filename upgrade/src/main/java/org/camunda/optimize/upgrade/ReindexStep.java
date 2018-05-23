package org.camunda.optimize.upgrade;


public interface ReindexStep extends UpgradeStep {

  String getInitialIndexName();

  String getMappingAndSettings();

  String getMappingScript();

  String getFinalIndexName();
}
