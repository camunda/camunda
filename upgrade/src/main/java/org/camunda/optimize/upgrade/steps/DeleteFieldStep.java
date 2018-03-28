package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.ReindexStep;

/**
 * @author Askar Akhmerov
 */
public class DeleteFieldStep implements ReindexStep {
  public static final String NAME = "delete-field";
  private final String indexName;
  private final String mapping;

  public DeleteFieldStep(String indexName, String mapping) {
    this.indexName = indexName;
    this.mapping = mapping;
  }

  public String getIndexName() {
    return indexName;
  }

  public String getName() {
    return NAME;
  }

  @Override
  public String getInitialIndexName() {
    return getIndexName();
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
    return getIndexName();
  }
}
