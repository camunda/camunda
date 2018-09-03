package org.camunda.optimize.upgrade.steps;


import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
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
    DocumentContext parse = JsonPath.parse(oldMapping);
    parse.renameKey("$.mappings", getInitialTypeName(), getFinalTypeName());
    return parse.jsonString();
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
