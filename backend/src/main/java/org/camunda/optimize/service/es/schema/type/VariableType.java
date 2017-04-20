package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class VariableType extends StrictTypeMappingCreator {

  public static final String ID = "id";
  public static final String NAME = "name";
  public static final String TYPE = "type";
  public static final String VALUE = "value";
  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";

  @Override
  public String getType() {
    return configurationService.getVariableType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder builder) throws IOException {
    return builder
            .startObject(ID)
              .field("type", "keyword")
            .endObject()
            .startObject(NAME)
              .field("type", "keyword")
            .endObject()
            .startObject(TYPE)
              .field("type", "keyword")
            .endObject()
            .startObject(VALUE)
              .field("type", "keyword")
            .endObject()
            .startObject("processDefinitionKey")
              .field("type", "keyword")
            .endObject()
            .startObject(PROCESS_DEFINITION_ID)
              .field("type", "keyword")
            .endObject()
            .startObject("processInstanceId")
              .field("type", "keyword")
            .endObject();
  }
}

