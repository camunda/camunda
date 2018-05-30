package org.camunda.optimize.upgrade.steps;

import org.camunda.optimize.upgrade.es.ESIndexAdjuster;

public class AddFieldStep extends ReindexStep {
  private final String indexName;
  private final String mapping;
  private final String mappingScript;

  public AddFieldStep(String indexName, String mapping, String mappingScript) {
    this.indexName = indexName;
    this.mapping = mapping;
    this.mappingScript = mappingScript;
  }

  @Override
  public void execute(ESIndexAdjuster ESIndexAdjuster) {
    transformCompleteMapping(ESIndexAdjuster);
  }

  public String getIndexName() {
    return indexName;
  }

  public String getMapping() {
    return mapping;
  }

  @Override
  public String getInitialIndexName() {
    return getIndexName();
  }

  @Override
  public String getMappingAndSettings() {
    return getMapping();
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
