package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ProcessDefinitionXmlType extends StrictTypeMappingCreator {

  public static final String ID = "id";
  public static final String BPMN_20_XML = "bpmn20Xml";

  @Override
  public String getType() {
    return configurationService.getProcessDefinitionXmlType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(BPMN_20_XML)
        .field("type", "text")
        .field("index", false)
      .endObject();
  }

}
