package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ProcessInstanceType extends StrictTypeMappingCreator {

  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String PROCESS_INSTANCE_ID = "processInstanceId";
  public static final String EVENTS = "events";
  public static final String VARIABLES = "variables";

  public static final String EVENT_ID = "id";
  public static final String ACTIVITY_ID = "activityId";
  public static final String ACTIVITY_TYPE = "activityType";
  public static final String EVENT_DURATION = "durationInMs";

  public static final String VARIABLE_ID = "id";
  public static final String VARIABLE_NAME = "name";
  public static final String VARIABLE_TYPE = "type";
  public static final String VARIABLE_VALUE = "value";
  public static final String VARIABLE_STRING_VALUE = "stringVal";
  public static final String VARIABLE_INTEGER_VALUE = "integerVal";
  public static final String VARIABLE_LONG_VALUE = "longVal";
  public static final String VARIABLE_SHORT_VALUE = "shortVal";
  public static final String VARIABLE_DOUBLE_VALUE = "doubleVal";
  public static final String VARIABLE_DATE_VALUE = "dateVal";
  public static final String VARIABLE_BOOLEAN_VALUE = "booleanVal";

  @Override
  public String getType() {
    return configurationService.getProcessInstanceType();
  }

  @Override
  protected XContentBuilder addProperties(XContentBuilder builder) throws IOException {
    XContentBuilder newBuilder =  builder
            .startObject(PROCESS_DEFINITION_KEY)
              .field("type", "keyword")
            .endObject()
            .startObject(PROCESS_DEFINITION_ID)
              .field("type", "keyword")
            .endObject()
            .startObject(PROCESS_INSTANCE_ID)
              .field("type", "keyword")
            .endObject()
            .startObject(START_DATE)
              .field("type", "date")
              .field("format",configurationService.getDateFormat())
            .endObject()
            .startObject(END_DATE)
              .field("type", "date")
              .field("format",configurationService.getDateFormat())
            .endObject()
            .startObject(EVENTS)
              .field("type", "nested")
              .startObject("properties");
                addNestedEventField(newBuilder)
              .endObject()
            .endObject()
            .startObject(VARIABLES)
              .field("type", "nested")
              .startObject("properties");
                addNestedVariableField(newBuilder)
              .endObject()
            .endObject();
    return newBuilder;
  }

  private XContentBuilder addNestedEventField(XContentBuilder builder) throws IOException {
    return builder
      .startObject(EVENT_ID)
      .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_ID)
      .field("type", "keyword")
      .endObject()
      .startObject(ACTIVITY_TYPE)
      .field("type", "keyword")
      .endObject()
      .startObject(EVENT_DURATION)
      .field("type", "long")
      .endObject();
  }

  private XContentBuilder addNestedVariableField(XContentBuilder builder) throws IOException {
    return builder
      .startObject(VARIABLE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(VARIABLE_NAME)
        .field("type", "keyword")
      .endObject()
      .startObject(VARIABLE_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(VARIABLE_VALUE)
        .field("type", "nested")
        .startObject("properties")
          .startObject(VARIABLE_STRING_VALUE)
            .field("type", "keyword")
          .endObject()
          .startObject(VARIABLE_INTEGER_VALUE)
            .field("type", "integer")
          .endObject()
          .startObject(VARIABLE_LONG_VALUE)
            .field("type", "long")
          .endObject()
          .startObject(VARIABLE_SHORT_VALUE)
            .field("type", "short")
          .endObject()
          .startObject(VARIABLE_DOUBLE_VALUE)
            .field("type", "double")
          .endObject()
          .startObject(VARIABLE_DATE_VALUE)
            .field("type", "date")
          .endObject()
          .startObject(VARIABLE_BOOLEAN_VALUE)
            .field("type", "boolean")
          .endObject()
        .endObject()
      .endObject();
  }

}
