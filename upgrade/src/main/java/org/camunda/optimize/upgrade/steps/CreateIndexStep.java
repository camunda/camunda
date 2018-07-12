package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.es.ESIndexAdjuster;


public class CreateIndexStep implements UpgradeStep {
  private final String typeName;
  private final String mapping;

  public CreateIndexStep(String typeName, String mapping) {
    this.typeName = typeName;
    this.mapping = mapping;
  }

  @Override
  public void execute(ESIndexAdjuster ESIndexAdjuster) {
    ESIndexAdjuster.createIndex(typeName, mapping);
  }
}
