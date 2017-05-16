package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ProcessDefinitionType extends StrictTypeMappingCreator {

  public static final String PROCESS_DEFINITION_ID = "id";

  @Override
  public String getType() {
    return configurationService.getProcessDefinitionType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject(PROCESS_DEFINITION_ID)
        .field("type", "keyword")
      .endObject()
      .startObject("key")
        .field("type", "keyword")
      .endObject()
      .startObject("version")
        .field("type", "long")
      .endObject()
      .startObject("name")
        .field("type", "keyword")
      .endObject();
  }

}
