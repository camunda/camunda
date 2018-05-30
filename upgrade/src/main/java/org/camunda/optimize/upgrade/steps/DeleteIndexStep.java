package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.es.ESIndexAdjuster;


public class DeleteIndexStep implements UpgradeStep {
  private final String indexName;

  public DeleteIndexStep(String indexName) {
    this.indexName = indexName;
  }

  @Override
  public void execute(ESIndexAdjuster ESIndexAdjuster) {
    ESIndexAdjuster.deleteIndex(indexName);
  }

  public String getIndexName() {
    return indexName;
  }
}
