package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.es.ESIndexAdjuster;


public class InsertDataStep implements UpgradeStep {
  private final String data;
  private final String type;

  public InsertDataStep(String type, String data) {
    this.type = type;
    this.data = data;
  }

  @Override
  public void execute(ESIndexAdjuster ESIndexAdjuster) {
    ESIndexAdjuster.insertData(type, data);
  }

}
