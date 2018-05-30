package org.camunda.optimize.upgrade.steps;


import org.camunda.optimize.upgrade.es.ESIndexAdjuster;

public class DeleteFieldStep extends ReindexStep {
  private final String indexName;
  private final String mapping;
  private final String mappingScript;

  public DeleteFieldStep(String indexName, String mapping, String mappingScript) {
    this.indexName = indexName;
    this.mapping = mapping;
    this.mappingScript = mappingScript;
  }

  public String getIndexName() {
    return indexName;
  }

  @Override
  public void execute(ESIndexAdjuster ESIndexAdjuster) {
    transformCompleteMapping(ESIndexAdjuster);
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
    return mappingScript;
  }

  @Override
  public String getFinalIndexName() {
    return getIndexName();
  }
}
