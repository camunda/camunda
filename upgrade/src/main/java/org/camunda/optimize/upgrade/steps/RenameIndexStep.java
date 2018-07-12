package org.camunda.optimize.upgrade.steps;


import org.camunda.optimize.upgrade.es.ESIndexAdjuster;

public class RenameIndexStep extends ReindexStep {

  private final String initialTypeName;
  private final String resultTypeName;

  public RenameIndexStep(String initialTypeName, String resultTypeName) {
    this.initialTypeName = initialTypeName;
    this.resultTypeName = resultTypeName;
  }

  @Override
  protected String adjustIndexMappings(String oldMapping) {
    return oldMapping;
  }

  @Override
  public void execute(ESIndexAdjuster ESIndexAdjuster) {
    transformCompleteMapping(ESIndexAdjuster);
  }

  @Override
  public String getInitialTypeName() {
    return initialTypeName;
  }

  @Override
  public String getMappingScript() {
    return null;
  }

  @Override
  public String getFinalTypeName() {
    return resultTypeName;
  }
}
