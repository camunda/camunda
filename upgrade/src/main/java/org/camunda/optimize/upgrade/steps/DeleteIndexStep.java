package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.UpgradeStep;

/**
 * @author Askar Akhmerov
 */
public class DeleteIndexStep implements UpgradeStep {
  public static final String NAME = "delete-index";
  private final String indexName;

  public DeleteIndexStep(String indexName) {
    this.indexName = indexName;
  }

  public String getName() {
    return NAME;
  }

  public String getIndexName() {
    return indexName;
  }
}
