package org.camunda.optimize.upgrade.steps;


import org.camunda.optimize.upgrade.es.ESIndexAdjuster;

public class RenameFieldStep extends ReindexStep {
  private final String indexName;
  private final String indexStructure;
  private final String mappingScript;

  public RenameFieldStep(String indexName, String indexStructure, String mappingScript) {
    this.indexName = indexName;
    this.indexStructure = indexStructure;
    this.mappingScript = mappingScript;
  }

  @Override
  public void execute(ESIndexAdjuster ESIndexAdjuster) {
    transformCompleteMapping(ESIndexAdjuster);
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
