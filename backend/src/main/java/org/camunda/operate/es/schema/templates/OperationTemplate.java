package org.camunda.operate.es.schema.templates;

import java.io.IOException;
import org.camunda.operate.property.OperateProperties;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OperationTemplate extends AbstractTemplateCreator {

  public static final String ID = "id";
  public static final String WORKFLOW_INSTANCE_ID = "workflowInstanceId";
  public static final String TYPE = "type";
  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String STATE = "state";
  public static final String ERROR_MSG = "errorMessage";
  public static final String LOCK_EXPIRATION_TIME = "lockExpirationTime";
  public static final String LOCK_OWNER = "lockOwner";

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public String getMainIndexName() {
    return operateProperties.getElasticsearch().getOperationIndexName();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder builder) throws IOException {
    XContentBuilder newBuilder =  builder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(WORKFLOW_INSTANCE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(TYPE)
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
      .startObject(LOCK_EXPIRATION_TIME)
        .field("type", "date")
        .field("format", operateProperties.getElasticsearch().getDateFormat())
      .endObject()
      .startObject(LOCK_OWNER)
        .field("type", "keyword")
      .endObject()
      .startObject(ERROR_MSG)
        .field("type", "keyword")
      .endObject();
    return newBuilder;
  }

}
