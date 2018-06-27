package org.camunda.operate.es.types;

import java.io.IOException;
import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkflowType extends StrictTypeMappingCreator {

  public static final String ID = "id";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String NAME = "name";
  public static final String VERSION = "version";
  public static final String BPMN_XML = "bpmnXml";
  public static final String RESOURCE_NAME = "resourceName";

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public String getType() {
    return operateProperties.getElasticsearch().getWorkflowIndexName();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
    return xContentBuilder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(BPMN_PROCESS_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(RESOURCE_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(VERSION)
        .field("type", "long")
      .endObject()
      .startObject(BPMN_XML)
        .field("type", "text")
        .field("index", false)
      .endObject()
      .startObject(PARTITION_ID)
        .field("type", "integer")
      .endObject()
      .startObject(POSITION)
        .field("type", "long")
      .endObject();
  }

}
