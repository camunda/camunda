package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.ReindexStep;

/**
 * @author Askar Akhmerov
 */
public class RenameIndexStep implements ReindexStep {

  public static final String NAME = "index-rename";
  private final String originalIndexName;
  private final String resultIndexName;
  private final String mapping;

  public RenameIndexStep(String originalIndexName, String resultIndexName, String mapping) {
    this.originalIndexName = originalIndexName;
    this.resultIndexName = resultIndexName;
    this.mapping = mapping;
  }

  public String getName() {
    return NAME;
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
