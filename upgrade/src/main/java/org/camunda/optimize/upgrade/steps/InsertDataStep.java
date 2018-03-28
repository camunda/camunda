package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.UpgradeStep;

/**
 * @author Askar Akhmerov
 */
public class InsertDataStep implements UpgradeStep {
  public static final String NAME = "insert-data";
  private final String indexName;
  private final String data;
  private final String type;

  public InsertDataStep(String indexName, String type, String data) {
    this.indexName = indexName;
    this.type = type;
    this.data = data;
  }

  public String getName() {
    return NAME;
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
