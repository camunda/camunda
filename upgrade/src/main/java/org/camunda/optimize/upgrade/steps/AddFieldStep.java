package org.camunda.optimize.upgrade.steps;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.camunda.optimize.upgrade.es.ESIndexAdjuster;

public class AddFieldStep extends ReindexStep {
  private final String typeName;
  private final String path;
  private final String key;
  private final Object value;
  private final String mappingScript;

  public AddFieldStep(String typeName, String path, String key, Object value , String mappingScript) {
    this.typeName = typeName;
    this.path = path;
    this.key = key;
    this.value = value;
    this.mappingScript = mappingScript;
  }

  @Override
  protected String adjustIndexMappings(String oldMapping) {
    DocumentContext parse = JsonPath.parse(oldMapping);
    parse.put(path, key, value);
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

  @Override
  public String getMappingScript() {
    return mappingScript;
  }

  @Override
  public String getFinalTypeName() {
    return getTypeName();
  }
}
