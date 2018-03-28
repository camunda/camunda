package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.ReindexStep;

/**
 * @author Askar Akhmerov
 */
public class RenameFieldStep implements ReindexStep {
  public static final String NAME = "field-rename";
  private final String indexName;
  private final String indexStructure;
  private final String mappingScript;

  public RenameFieldStep(String indexName, String indexStructure, String mappingScript) {
    this.indexName = indexName;
    this.indexStructure = indexStructure;
    this.mappingScript = mappingScript;
  }

  public String getName() {
    return NAME;
  }

  public String getIndexName() {
    return indexName;
  }

  public String getIndexStructure() {
    return indexStructure;
  }

  @Override
  public String getInitialIndexName() {
    return getIndexName();
  }

  @Override
  public String getMappingAndSettings() {
    return getIndexStructure();
  }

  public String getMappingScript() {
    return mappingScript;
  }

  @Override
  public String getFinalIndexName() {
    return getIndexName();
  }
}
