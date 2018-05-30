package org.camunda.optimize.upgrade.steps;


import org.camunda.optimize.upgrade.es.ESIndexAdjuster;

public class RenameIndexStep extends ReindexStep {

  private final String originalIndexName;
  private final String resultIndexName;
  private final String mapping;

  public RenameIndexStep(String originalIndexName, String resultIndexName, String mapping) {
    this.originalIndexName = originalIndexName;
    this.resultIndexName = resultIndexName;
    this.mapping = mapping;
  }

  @Override
  public void execute(ESIndexAdjuster ESIndexAdjuster) {
    transformCompleteMapping(ESIndexAdjuster);
  }

  public String getOriginalIndexName() {
    return originalIndexName;
  }

  public String getResultIndexName() {
    return resultIndexName;
  }

  @Override
  public String getInitialIndexName() {
    return getOriginalIndexName();
  }

  @Override
  public String getMappingAndSettings() {
    return mapping;
  }

  @Override
  public String getMappingScript() {
    return null;
  }

  @Override
  public String getFinalIndexName() {
    return getResultIndexName();
  }
}
