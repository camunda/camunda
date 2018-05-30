package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.es.ESIndexAdjuster;


public class InsertDataStep implements UpgradeStep {
  private final String indexName;
  private final String data;
  private final String type;

  public InsertDataStep(String indexName, String type, String data) {
    this.indexName = indexName;
    this.type = type;
    this.data = data;
  }

  @Override
  public void execute(ESIndexAdjuster ESIndexAdjuster) {
    ESIndexAdjuster.insertData(indexName, type, data);
  }

  public String getIndexName() {
    return indexName;
  }

  public String getData() {
    return data;
  }

  public String getType() {
    return type;
  }
}
