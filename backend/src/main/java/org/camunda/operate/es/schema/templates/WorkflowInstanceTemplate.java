package org.camunda.operate.es.schema.templates;

import java.io.IOException;
import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WorkflowInstanceTemplate extends AbstractTemplateCreator {

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String WORKFLOW_ID = "workflowId";
  public static final String WORKFLOW_NAME = "workflowName";
  public static final String WORKFLOW_VERSION = "workflowVersion";
  public static final String WORKFLOW_INSTANCE_ID = "workflowInstanceId";
  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String INCIDENTS = "incidents";
  public static final String ACTIVITY_ID = "activityId";
  public static final String ACTIVITY_INSTANCE_ID = "activityInstanceId";
  public static final String JOB_ID = "jobId";
  public static final String ERROR_TYPE = "errorType";
  public static final String ERROR_MSG = "errorMessage";
  public static final String STATE = "state";
  public static final String TYPE = "type";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String ACTIVITIES = "activities";
  public static final String STRING_VARIABLES = "stringVariables";
  public static final String LONG_VARIABLES = "longVariables";
  public static final String DOUBLE_VARIABLES = "doubleVariables";
  public static final String BOOLEAN_VARIABLES = "booleanVariables";
  public static final String VARIABLE_NAME = "name";
  public static final String VARIABLE_VALUE = "value";
  public static final String NULL_VALUE = "NULL";
  public static final String SEQUENCE_FLOWS = "sequenceFlows";

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public String getMainIndexName() {
    return operateProperties.getElasticsearch().getWorkflowInstanceIndexName();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder builder) throws IOException {
    XContentBuilder newBuilder =  builder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(WORKFLOW_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(WORKFLOW_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(WORKFLOW_VERSION)
        .field("type", "long")
      .endObject()
      .startObject(BPMN_PROCESS_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(STATE)
        .field("type", "keyword")
      .endObject()
      .startObject(START_DATE)
        .field("type", "date")
        .field("format", operateProperties.getElasticsearch().getDateFormat())
      .endObject()
      .startObject(END_DATE)
        .field("type", "date")
        .field("format", operateProperties.getElasticsearch().getDateFormat())
      .endObject()
      .startObject(INCIDENTS)
        .field("type", "nested")
        .startObject("properties");
          addNestedIncidentsField(newBuilder)
        .endObject()
      .endObject()
      .startObject(ACTIVITIES)
        .field("type", "nested")
        .startObject("properties");
          addNestedActivitiesField(newBuilder)
        .endObject()
      .endObject()
      .startObject(STRING_VARIABLES)
        .field("type", "nested")
        .startObject("properties");
          addNestedVariablesField(newBuilder, "keyword")
        .endObject()
      .endObject()
      .startObject(LONG_VARIABLES)
        .field("type", "nested")
        .startObject("properties");
          addNestedVariablesField(newBuilder, "long")
        .endObject()
      .endObject()
      .startObject(DOUBLE_VARIABLES)
        .field("type", "nested")
        .startObject("properties");
          addNestedVariablesField(newBuilder, "double")
        .endObject()
      .endObject()
      .startObject(BOOLEAN_VARIABLES)
        .field("type", "nested")
        .startObject("properties");
          addNestedVariablesField(newBuilder, "boolean")
        .endObject()
      .endObject()
      .startObject(SEQUENCE_FLOWS)
        .field("type", "nested")
        .startObject("properties");
          addNestedSequenceFlowsField(newBuilder)
        .endObject()
      .endObject()
      .startObject(PARTITION_ID)
        .field("type", "integer")
      .endObject()
      .startObject(KEY)
        .field("type", "long")
      .endObject();
    return newBuilder;
  }

  private XContentBuilder addNestedIncidentsField(XContentBuilder builder) throws IOException {
    builder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(PARTITION_ID)
        .field("type", "integer")
      .endObject()
      .startObject(KEY)
        .field("type", "long")
      .endObject()
      .startObject(ERROR_MSG)
        .field("type", "keyword")
      .endObject()
      .startObject(ERROR_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(STATE)
        .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_INSTANCE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(JOB_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(WORKFLOW_INSTANCE_ID)
        .field("type", "keyword")
      .endObject();
    return builder;
  }

  private XContentBuilder addNestedActivitiesField(XContentBuilder builder) throws IOException {
    builder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(PARTITION_ID)
        .field("type", "integer")
      .endObject()
      .startObject(KEY)
        .field("type", "long")
      .endObject()
      .startObject(STATE)
        .field("type", "keyword")
      .endObject()
      .startObject(TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(START_DATE)
        .field("type", "date")
        .field("format", operateProperties.getElasticsearch().getDateFormat())
      .endObject()
      .startObject(END_DATE)
        .field("type", "date")
        .field("format", operateProperties.getElasticsearch().getDateFormat())
      .endObject();
    return builder;
  }

  private XContentBuilder addNestedSequenceFlowsField(XContentBuilder builder) throws IOException {
    builder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(PARTITION_ID)
        .field("type", "integer")
      .endObject()
      .startObject(KEY)
        .field("type", "long")
      .endObject()
      .startObject(ACTIVITY_ID)
        .field("type", "keyword")
      .endObject();
    return builder;
  }

  private XContentBuilder addNestedVariablesField(XContentBuilder builder, String fieldType) throws IOException {
    final XContentBuilder xContentBuilder =
      builder
        .startObject(VARIABLE_NAME)
          .field("type", "keyword")
        .endObject()
        .startObject(VARIABLE_VALUE)
          .field("type", fieldType);
    if (fieldType.equals("keyword")) {
      xContentBuilder.field("null_value", NULL_VALUE);
    }
    xContentBuilder.endObject();
    return builder;
  }

}
