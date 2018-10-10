package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ProcessInstanceType extends StrictTypeMappingCreator {

  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String DURATION = "durationInMs";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String PROCESS_DEFINITION_VERSION = "processDefinitionVersion";
  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String PROCESS_INSTANCE_ID = "processInstanceId";
  public static final String BUSINESS_KEY = "businessKey";
  public static final String EVENTS = "events";
  public static final String STATE = "state";

  public static final String EVENT_ID = "id";
  public static final String ACTIVITY_ID = "activityId";
  public static final String ACTIVITY_TYPE = "activityType";
  public static final String ACTIVITY_DURATION = "durationInMs";
  public static final String ACTIVITY_START_DATE = "startDate";
  public static final String ACTIVITY_END_DATE = "endDate";

  public static final String STRING_VARIABLES = "stringVariables";
  public static final String INTEGER_VARIABLES = "integerVariables";
  public static final String LONG_VARIABLES = "longVariables";
  public static final String SHORT_VARIABLES = "shortVariables";
  public static final String DOUBLE_VARIABLES = "doubleVariables";
  public static final String DATE_VARIABLES = "dateVariables";
  public static final String BOOLEAN_VARIABLES = "booleanVariables";

  public static final String VARIABLE_ID = "id";
  public static final String VARIABLE_NAME = "name";
  public static final String VARIABLE_TYPE = "type";
  public static final String VARIABLE_VALUE = "value";
  public static final String VARIABLE_VERSION = "version";

  public static final String ENGINE = "engine";

  @Override
  public String getType() {
    return configurationService.getProcessInstanceType();
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder builder) throws IOException {
    XContentBuilder newBuilder =  builder
            .startObject(PROCESS_DEFINITION_KEY)
              .field("type", "keyword")
            .endObject()
            .startObject(PROCESS_DEFINITION_VERSION)
              .field("type", "keyword")
            .endObject()
            .startObject(PROCESS_DEFINITION_ID)
              .field("type", "keyword")
            .endObject()
            .startObject(PROCESS_INSTANCE_ID)
              .field("type", "keyword")
            .endObject()
            .startObject(BUSINESS_KEY)
              .field("type", "keyword")
            .endObject()
            .startObject(START_DATE)
              .field("type", "date")
              .field("format",configurationService.getOptimizeDateFormat())
            .endObject()
            .startObject(END_DATE)
              .field("type", "date")
              .field("format",configurationService.getOptimizeDateFormat())
            .endObject()
            .startObject(DURATION)
              .field("type", "long")
            .endObject()
            .startObject(ENGINE)
              .field("type", "keyword")
            .endObject()
            .startObject(STATE)
              .field("type", "keyword")
            .endObject()
            .startObject(EVENTS)
              .field("type", "nested")
              .field("include_in_all", false)
              .startObject("properties");
                addNestedEventField(newBuilder)
              .endObject()
            .endObject();
            addVariableFields(newBuilder);
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
      .startObject(ACTIVITY_DURATION)
        .field("type", "long")
      .endObject()
      .startObject(ACTIVITY_START_DATE)
        .field("type", "date")
        .field("format",configurationService.getOptimizeDateFormat())
      .endObject()
      .startObject(ACTIVITY_END_DATE)
        .field("type", "date")
        .field("format",configurationService.getOptimizeDateFormat())
      .endObject();
  }

  private XContentBuilder addVariableFields(XContentBuilder builder) throws IOException {
    XContentBuilder newBuilder = builder
        .startObject(STRING_VARIABLES)
          .field("type", "nested")
          .field("include_in_all", false)
          .startObject("properties");
            addNestedVariableField(newBuilder, "keyword")
          .endObject()
        .endObject()
        .startObject(INTEGER_VARIABLES)
          .field("type", "nested")
          .startObject("properties");
            addNestedVariableField(newBuilder, "integer")
          .endObject()
        .endObject()
        .startObject(LONG_VARIABLES)
          .field("type", "nested")
          .startObject("properties");
            addNestedVariableField(newBuilder, "long")
          .endObject()
        .endObject()
        .startObject(SHORT_VARIABLES)
          .field("type", "nested")
          .startObject("properties");
            addNestedVariableField(newBuilder, "short")
          .endObject()
        .endObject()
        .startObject(DOUBLE_VARIABLES)
          .field("type", "nested")
          .startObject("properties");
            addNestedVariableField(newBuilder, "double")
          .endObject()
        .endObject()
        .startObject(DATE_VARIABLES)
          .field("type", "nested")
          .startObject("properties");
            addNestedVariableField(newBuilder, "date")
          .endObject()
        .endObject()
        .startObject(BOOLEAN_VARIABLES)
          .field("type", "nested")
          .startObject("properties");
            addNestedVariableField(newBuilder, "boolean")
          .endObject()
        .endObject();
    return newBuilder;
  }

  private XContentBuilder addNestedVariableField(XContentBuilder builder, String type) throws IOException {
    XContentBuilder newBuilder = builder;
    newBuilder
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
        .field("type", type);
      if (type.equals("keyword")) {
        newBuilder
          .startObject("fields")
            .startObject("nGramField")
              .field("type", "text")
              .field("analyzer", "lowercase_ngram")
            .endObject()
            .startObject("lowercaseField")
              .field("type", "keyword")
              .field("normalizer", "lowercase_normalizer")
            .endObject()
          .endObject();
      }
    newBuilder.endObject()
      .startObject(VARIABLE_VERSION)
        .field("type", "long")
      .endObject();
    return newBuilder;
  }
}
