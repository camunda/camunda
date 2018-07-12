package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.es.ESIndexAdjuster;


public class DeleteIndexStep implements UpgradeStep {
  private final String typeName;

  public DeleteIndexStep(String typeName) {
    this.typeName = typeName;
  }

  @Override
  public void execute(ESIndexAdjuster ESIndexAdjuster) {
    ESIndexAdjuster.deleteIndex(typeName);
  }
}
