package org.camunda.optimize.upgrade.steps;


import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.camunda.optimize.upgrade.es.ESIndexAdjuster;

public class RenameFieldStep extends ReindexStep {
  private final String typeName;
  private final String jsonPath;
  private final String mappingScript;
  private final String oldFieldName;
  private final String newFieldName;

  public RenameFieldStep(String typeName, String jsonPath, String oldFieldName, String newFieldName, String mappingScript) {
    this.typeName = typeName;
    this.jsonPath = jsonPath;
    this.oldFieldName = oldFieldName;
    this.newFieldName = newFieldName;
    this.mappingScript = mappingScript;
  }

  @Override
  protected String adjustIndexMappings(String oldMapping) {
    DocumentContext parse = JsonPath.parse(oldMapping);
    parse.renameKey(jsonPath, oldFieldName, newFieldName);
    return parse.jsonString();
  }

  @Override
  public void execute(ESIndexAdjuster ESIndexAdjuster) {
    transformCompleteMapping(ESIndexAdjuster);
  }

  public String getTypeName() {
    return typeName;
  }

  @Override
  public String getInitialTypeName() {
    return getTypeName();
  }

  public String getMappingScript() {
    return mappingScript;
  }

  @Override
  public String getFinalTypeName() {
    return getTypeName();
  }
}
