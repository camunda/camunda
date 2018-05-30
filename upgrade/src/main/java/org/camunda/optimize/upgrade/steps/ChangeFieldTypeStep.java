package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.es.ESIndexAdjuster;

public class ChangeFieldTypeStep extends ReindexStep {
  private final String initialIndexName;
  private final String finalIndexName;
  private final String mappingAndSettings;
  private final String mappingScript;

  public ChangeFieldTypeStep(
    String initialIndexName,
    String finalIndexName,
    String mappingAndSettings,
    String mappingScript
  ) {
    this.initialIndexName = initialIndexName;
    this.finalIndexName = finalIndexName;
    this.mappingAndSettings = mappingAndSettings;
    this.mappingScript = mappingScript;
  }

  @Override
  public void execute(ESIndexAdjuster ESIndexAdjuster) {
    transformCompleteMapping(ESIndexAdjuster);
  }

  public String getInitialIndexName() {
    return initialIndexName;
  }

  public String getFinalIndexName() {
    return finalIndexName;
  }

  public String getMappingAndSettings() {
    return mappingAndSettings;
  }

  public String getMappingScript() {
    return mappingScript;
  }

}
