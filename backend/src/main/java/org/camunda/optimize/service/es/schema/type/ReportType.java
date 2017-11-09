package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ReportType extends StrictTypeMappingCreator {

  public static final String ID = "id";
  public static final String NAME = "name";
  public static final String LAST_MODIFIED = "lastModified";
  public static final String CREATED = "created";
  public static final String OWNER = "owner";
  public static final String LAST_MODIFIER = "lastModifier";

  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";

  public static final String DATA = "data";
  public static final String FILTER = "filter";

  public static final String VIEW = "view";
  public static final String VIEW_OPERATION = "operation";
  public static final String VIEW_ENTITY = "entity";
  public static final String VIEW_PROPERTY = "entity";

  public static final String GROUP_BY = "groupBy";
  public static final String GROUP_BY_TYPE = "type";
  public static final String GROUP_BY_UNIT = "unit";

  public static final String VISUALIZATION = "visualization";

  public static final String DATES = "dates";
  public static final String DATE_TYPE = "type";
  public static final String DATE_OPERATOR = "operator";
  public static final String DATE_VALUE =  "value";

  public static final String VARIABLES = "variables";
  public static final String VARIABLE_NAME = "name";
  public static final String VARIABLE_TYPE = "type";
  public static final String VARIABLE_OPERATOR = "operator";
  public static final String VARIABLE_VALUES =  "values";

  public static final String EXECUTED_FLOW_NODES = "executedFlowNodes";
  public static final String FLOW_NODE_OPERATOR = "operator";
  public static final String FLOW_NODE_VALUES =  "values";

  @Override
  public String getType() {
    return configurationService.getReportType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder xContentBuilder) throws IOException {
     XContentBuilder newBuilder = xContentBuilder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(LAST_MODIFIED)
        .field("type", "date")
              .field("format",configurationService.getDateFormat())
      .endObject()
      .startObject(CREATED)
        .field("type", "date")
              .field("format",configurationService.getDateFormat())
      .endObject()
      .startObject(OWNER)
        .field("type", "keyword")
      .endObject()
      .startObject(LAST_MODIFIER)
        .field("type", "keyword")
      .endObject()
      .startObject(DATA)
        .field("type", "nested")
        .startObject("properties");
          addNestedDataField(newBuilder)
        .endObject()
      .endObject();
     return newBuilder;
  }

  private XContentBuilder addNestedDataField(XContentBuilder builder) throws IOException {
    XContentBuilder newBuilder = builder
      .startObject(PROCESS_DEFINITION_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(FILTER)
        .field("type", "nested")
        .startObject("properties");
          addNestedFilterField(newBuilder)
        .endObject()
      .endObject()
      .startObject(VIEW)
        .field("type", "nested")
        .startObject("properties");
          addViewField(newBuilder)
        .endObject()
      .endObject()
      .startObject(GROUP_BY)
        .field("type", "nested")
        .startObject("properties");
          addGroupByField(newBuilder)
        .endObject()
      .endObject()
      .startObject(VISUALIZATION)
        .field("type", "keyword")
      .endObject();
    return newBuilder;
  }

  private XContentBuilder addViewField(XContentBuilder builder) throws IOException {
    XContentBuilder newBuilder = builder
      .startObject(VIEW_OPERATION)
        .field("type", "keyword")
      .endObject()
      .startObject(VIEW_ENTITY)
        .field("type", "keyword")
      .endObject()
      .startObject(VIEW_PROPERTY)
        .field("type", "keyword")
      .endObject();
    return newBuilder;
  }

  private XContentBuilder addGroupByField(XContentBuilder builder) throws IOException {
    XContentBuilder newBuilder = builder
      .startObject(GROUP_BY_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(GROUP_BY_UNIT)
        .field("type", "keyword")
      .endObject();
    return newBuilder;
  }

  private XContentBuilder addNestedFilterField(XContentBuilder builder) throws IOException {
    XContentBuilder newBuilder = builder
      .startObject(DATES)
        .field("type", "nested")
        .startObject("properties");
          addNestedDatesField(newBuilder)
        .endObject()
      .endObject()
      .startObject(VARIABLES)
        .field("type", "nested")
        .startObject("properties");
          addNestedVariablesField(newBuilder)
        .endObject()
      .endObject()
      .startObject(EXECUTED_FLOW_NODES)
        .field("type", "nested")
        .startObject("properties");
          addNestedExecutedFlowNodesField(newBuilder)
        .endObject()
      .endObject();
    return newBuilder;
  }

  private XContentBuilder addNestedDatesField(XContentBuilder builder) throws IOException {
    return builder
      .startObject(DATE_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(DATE_OPERATOR)
        .field("type", "keyword")
      .endObject()
      .startObject(DATE_VALUE)
        .field("type", "date")
        .field("format",configurationService.getDateFormat())
      .endObject();
  }

  private XContentBuilder addNestedVariablesField(XContentBuilder builder) throws IOException {
    return builder
      .startObject(VARIABLE_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(VARIABLE_OPERATOR)
        .field("type", "keyword")
      .endObject()
      .startObject(VARIABLE_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(VARIABLE_VALUES)
        .field("type", "keyword")
      .endObject();
  }

  private XContentBuilder addNestedExecutedFlowNodesField(XContentBuilder builder) throws IOException {
    return builder
      .startObject(FLOW_NODE_OPERATOR)
        .field("type", "keyword")
      .endObject()
      .startObject(FLOW_NODE_VALUES)
        .field("type", "keyword")
      .endObject();
  }
}
