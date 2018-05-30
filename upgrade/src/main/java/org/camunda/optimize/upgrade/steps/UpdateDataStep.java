package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.es.ESIndexAdjuster;


public class UpdateDataStep implements UpgradeStep {
  private final String index;
  private final String query;
  private final String updateScript;

  public UpdateDataStep(String testIndex, String query, String updateScript) {
    this.index = testIndex;
    this.query = query;
    this.updateScript = updateScript;
  }

  @Override
  public void execute(ESIndexAdjuster ESIndexAdjuster) {
    ESIndexAdjuster.updateData(index, updateScript, query);
  }

}
