package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.UpgradeStep;

/**
 * @author Askar Akhmerov
 */
public class CreateIndexStep implements UpgradeStep {
  public static final String NAME = "create-index";
  private final String indexName;
  private final String mapping;

  public CreateIndexStep(String indexName, String mapping) {
    this.indexName = indexName;
    this.mapping = mapping;
  }

  public String getName() {
    return NAME;
  }

  public String getIndexName() {
    return indexName;
  }

  public String getMapping() {
    return mapping;
  }
}
