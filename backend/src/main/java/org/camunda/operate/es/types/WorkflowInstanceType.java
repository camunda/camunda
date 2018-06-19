package org.camunda.operate.es.types;

import java.io.IOException;
import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("elasticsearch")
public class WorkflowInstanceType extends StrictTypeMappingCreator {

  public static final String TYPE = "workflow-instance";

  public static final String ID = "id";
  public static final String WORKFLOW_ID = "workflowId";
  public static final String WORKFLOW_INSTANCE_ID = "workflowInstanceId";
  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String INCIDENTS = "incidents";
  public static final String ACTIVITY_ID = "activityId";
  public static final String ACTIVITY_INSTANCE_ID = "activityInstanceId";
  public static final String TASK_ID = "taskId";
  public static final String ERROR_TYPE = "errorType";
  public static final String ERROR_MSG = "errorMessage";
  public static final String STATE = "state";
  public static final String BUSINESS_KEY = "businessKey";

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public String getType() {
    return TYPE;
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
      .startObject(BUSINESS_KEY)
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
      .endObject();
    return newBuilder;
  }

  private XContentBuilder addNestedIncidentsField(XContentBuilder builder) throws IOException {
    builder
      .startObject(ID)
        .field("type", "keyword")
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
      .startObject(TASK_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(WORKFLOW_INSTANCE_ID)
        .field("type", "keyword")
      .endObject();
    return builder;
  }

}
