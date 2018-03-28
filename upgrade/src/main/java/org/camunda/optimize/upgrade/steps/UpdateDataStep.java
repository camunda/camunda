package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.UpgradeStep;

/**
 * @author Askar Akhmerov
 */
public class UpdateDataStep implements UpgradeStep {
  public static final String NAME = "update-data";
  private final String index;
  private final String type;
  private final String query;
  private final String updateScript;

  public UpdateDataStep(String testIndex, String type, String query, String updateScript) {
    this.index = testIndex;
    this.type = type;
    this.query = query;
    this.updateScript = updateScript;
  }

  public String getIndex() {
    return index;
  }

  public String getType() {
    return type;
  }

  public String getQuery() {
    return query;
  }

  public String getUpdateScript() {
    return updateScript;
  }

  @Override
  public String getName() {
    return NAME;
  }
}
