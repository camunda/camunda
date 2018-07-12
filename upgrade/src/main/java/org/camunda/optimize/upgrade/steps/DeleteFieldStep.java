package org.camunda.optimize.upgrade.steps;


import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.camunda.optimize.upgrade.es.ESIndexAdjuster;

public class DeleteFieldStep extends ReindexStep {
  private final String typeName;
  private final String jsonPath;
  private final String mappingScript;

  public DeleteFieldStep(String typeName, String jsonPath, String mappingScript) {
    this.typeName = typeName;
    this.jsonPath = jsonPath;
    this.mappingScript = mappingScript;
  }

  public String getTypeName() {
    return typeName;
  }

  @Override
  protected String adjustIndexMappings(String oldMapping) {
    DocumentContext parse = JsonPath.parse(oldMapping);
    parse.delete(jsonPath);
    return parse.jsonString();
  }

  @Override
  public void execute(ESIndexAdjuster ESIndexAdjuster) {
    transformCompleteMapping(ESIndexAdjuster);
  }

  @Override
  public String getInitialTypeName() {
    return getTypeName();
  }

  @Override
  public String getMappingScript() {
    return mappingScript;
  }

  @Override
  public String getFinalTypeName() {
    return getTypeName();
  }
}
