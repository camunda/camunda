package org.camunda.optimize.upgrade;

/**
 * @author Askar Akhmerov
 */
public interface ReindexStep extends UpgradeStep {

  String getInitialIndexName();

  String getMappingAndSettings();

  String getMappingScript();

  String getFinalIndexName();
}
