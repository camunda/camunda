package org.camunda.optimize.upgrade.steps;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.camunda.optimize.upgrade.es.ESIndexAdjuster;

public class ChangeFieldTypeStep extends ReindexStep {
  private final String typeName;
  private final String jsonPath;
  private final Object newFieldType;
  private final String mappingScript;

  public ChangeFieldTypeStep(
    String typeName,
    String jsonPath,
    Object newFieldType,
    String mappingScript
  ) {
    this.typeName = typeName;
    this.jsonPath = jsonPath;
    this.newFieldType = newFieldType;
    this.mappingScript = mappingScript;
  }


  @Override
  protected String adjustIndexMappings(String oldMapping) {
    DocumentContext parse = JsonPath.parse(oldMapping);
    parse.set(jsonPath, newFieldType);
    return parse.jsonString();
  }

  @Override
  public void execute(ESIndexAdjuster ESIndexAdjuster) {
    transformCompleteMapping(ESIndexAdjuster);
  }

  public String getInitialTypeName() {
    return typeName;
  }

  public String getFinalTypeName() {
    return typeName;
  }

  public String getMappingScript() {
    return mappingScript;
  }

}
