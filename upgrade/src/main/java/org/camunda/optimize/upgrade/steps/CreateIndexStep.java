package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.es.ESIndexAdjuster;


public class CreateIndexStep implements UpgradeStep {
  private final String indexName;
  private final String mapping;

  public CreateIndexStep(String indexName, String mapping) {
    this.indexName = indexName;
    this.mapping = mapping;
  }

  @Override
  public void execute(ESIndexAdjuster ESIndexAdjuster) {
    ESIndexAdjuster.createIndex(indexName, mapping);
  }

  public String getIndexName() {
    return indexName;
  }

  public String getMapping() {
    return mapping;
  }
}
