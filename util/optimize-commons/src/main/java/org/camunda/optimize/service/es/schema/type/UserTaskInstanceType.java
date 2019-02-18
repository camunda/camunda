package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@Component
public class UserTaskInstanceType extends StrictTypeMappingCreator {

  public static final int VERSION = 1;

  public static final String ID = "id";

  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String PROCESS_DEFINITION_VERSION = "processDefinitionVersion";

  public static final String PROCESS_INSTANCE_ID = "processInstanceId";

  public static final String ACTIVITY_ID = "activityId";
  public static final String ACTIVITY_INSTANCE_ID = "activityInstanceId";

  public static final String TOTAL_DURATION = "totalDurationInMs";
  public static final String IDLE_DURATION = "idleDurationInMs";
  public static final String WORK_DURATION = "workDurationInMs";

  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String DUE_DATE = "dueDate";

  public static final String DELETE_REASON = "deleteReason";

  public static final String USER_OPERATIONS = "userOperations";
  public static final String USER_OPERATION_ID = "id";
  public static final String USER_OPERATION_USER_ID = "userId";
  public static final String USER_OPERATION_TIMESTAMP = "timestamp";
  public static final String USER_OPERATION_TYPE = "type";
  public static final String USER_OPERATION_PROPERTY = "property";
  public static final String USER_OPERATION_ORIGINAL_VALUE = "originalValue";
  public static final String USER_OPERATION_NEW_VALUE = "newValue";


  public static final String ENGINE = "engine";

  @Override
  public String getType() {
    return ElasticsearchConstants.USER_TASK_INSTANCE_TYPE;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public XContentBuilder addProperties(final XContentBuilder builder) throws IOException {
    // @formatter:off
    final XContentBuilder newBuilder =  builder
      .startObject(ID)
        .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_DEFINITION_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_DEFINITION_KEY)
        .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_DEFINITION_VERSION)
        .field("type", "keyword")
      .endObject()
      .startObject(PROCESS_INSTANCE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_INSTANCE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(TOTAL_DURATION)
        .field("type", "long")
      .endObject()
      .startObject(IDLE_DURATION)
        .field("type", "long")
      .endObject()
      .startObject(WORK_DURATION)
        .field("type", "long")
      .endObject()
      .startObject(START_DATE)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(END_DATE)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(DUE_DATE)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(DELETE_REASON)
        .field("type", "keyword")
      .endObject()
      .startObject(USER_OPERATIONS)
        .field("type", "nested")
        .field("include_in_all", false)
        .startObject("properties");
         addNestedUserOperationsField(newBuilder)
        .endObject()
      .endObject()
      .startObject(ENGINE)
        .field("type", "keyword")
      .endObject()
      ;
    // @formatter:on
    return newBuilder;
  }

  private XContentBuilder addNestedUserOperationsField(final XContentBuilder builder) throws IOException {
    // @formatter:off
    builder
      .startObject(USER_OPERATION_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(USER_OPERATION_USER_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(USER_OPERATION_TIMESTAMP)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(USER_OPERATION_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(USER_OPERATION_PROPERTY)
        .field("type", "keyword")
      .endObject()
      .startObject(USER_OPERATION_ORIGINAL_VALUE)
        .field("type", "keyword")
      .endObject()
      .startObject(USER_OPERATION_NEW_VALUE)
        .field("type", "keyword")
      .endObject()
      ;
    return builder;
    // @formatter:on
  }
}
