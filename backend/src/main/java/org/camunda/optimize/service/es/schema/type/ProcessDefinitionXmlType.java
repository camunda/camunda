package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ProcessDefinitionXmlType extends StrictTypeMappingCreator {

  public static final String PROCESSS_DEFINITION_ID = "processDefinitionId";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String PROCESS_DEFINITION_VERSION = "processDefinitionVersion";
  public static final String BPMN_20_XML = "bpmn20Xml";
  public static final String ENGINE = "engine";
  public static final String FLOW_NODE_NAMES = "flowNodeNames";

  @Override
  public String getType() {
    return configurationService.getProcessDefinitionXmlType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject(PROCESSS_DEFINITION_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_DEFINITION_KEY)
        .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_DEFINITION_VERSION)
        .field("type", "keyword")
      .endObject()
      .startObject(ENGINE)
        .field("type", "keyword")
      .endObject()
      .startObject(FLOW_NODE_NAMES)
        .field("type", "object")
        .field("enabled", "false")
      .endObject()
      .startObject(BPMN_20_XML)
        .field("type", "text")
        .field("index", false)
      .endObject();
  }

}
