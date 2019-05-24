/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.type;

import org.camunda.optimize.service.es.schema.StrictTypeMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@Component
public class ProcessInstanceType extends StrictTypeMappingCreator implements DefinitionBasedType {

  public static final int VERSION = 2;

  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String DURATION = "durationInMs";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String PROCESS_DEFINITION_VERSION = "processDefinitionVersion";
  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String PROCESS_INSTANCE_ID = "processInstanceId";
  public static final String BUSINESS_KEY = "businessKey";
  public static final String STATE = "state";

  public static final String EVENTS = "events";

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

  public static final String USER_TASKS = "userTasks";


  public static final String USER_TASK_ID = "id";

  public static final String USER_TASK_ACTIVITY_ID = "activityId";
  public static final String USER_TASK_ACTIVITY_INSTANCE_ID = "activityInstanceId";

  public static final String USER_TASK_TOTAL_DURATION = "totalDurationInMs";
  public static final String USER_TASK_IDLE_DURATION = "idleDurationInMs";
  public static final String USER_TASK_WORK_DURATION = "workDurationInMs";

  public static final String USER_TASK_START_DATE = "startDate";
  public static final String USER_TASK_END_DATE = "endDate";
  public static final String USER_TASK_DUE_DATE = "dueDate";
  public static final String USER_TASK_CLAIM_DATE = "claimDate";

  public static final String USER_TASK_DELETE_REASON = "deleteReason";

  public static final String USER_OPERATIONS = "userOperations";
  public static final String USER_OPERATION_ID = "id";
  public static final String USER_OPERATION_USER_ID = "userId";
  public static final String USER_OPERATION_TIMESTAMP = "timestamp";
  public static final String USER_OPERATION_TYPE = "type";
  public static final String USER_OPERATION_PROPERTY = "property";
  public static final String USER_OPERATION_ORIGINAL_VALUE = "originalValue";
  public static final String USER_OPERATION_NEW_VALUE = "newValue";

  public static final String ENGINE = "engine";
  public static final String TENANT_ID = "tenantId";

  @Override
  public String getType() {
    return ElasticsearchConstants.PROC_INSTANCE_TYPE;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public String getDefinitionKeyFieldName() {
    return PROCESS_DEFINITION_KEY;
  }

  @Override
  public String getDefinitionVersionFieldName() {
    return PROCESS_DEFINITION_VERSION;
  }

  @Override
  public String getTenantIdFieldName() {
    return TENANT_ID;
  }

  @Override
  public XContentBuilder addProperties(XContentBuilder builder) throws IOException {
    // @formatter:off
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
              .field("format", OPTIMIZE_DATE_FORMAT)
            .endObject()
            .startObject(END_DATE)
              .field("type", "date")
              .field("format", OPTIMIZE_DATE_FORMAT)
            .endObject()
            .startObject(DURATION)
              .field("type", "long")
            .endObject()
            .startObject(ENGINE)
              .field("type", "keyword")
            .endObject()
            .startObject(TENANT_ID)
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
            .endObject()
            .startObject(USER_TASKS)
              .field("type", "nested")
              .field("include_in_all", false)
              .startObject("properties");
                addNestedUserTaskField(newBuilder)
              .endObject()
           .endObject();
            addVariableFields(newBuilder);
    return newBuilder;
    // @formatter:on
  }

  private XContentBuilder addNestedEventField(XContentBuilder builder) throws IOException {
    // @formatter:off
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
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(ACTIVITY_END_DATE)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      ;
    // @formatter:on
  }

  private XContentBuilder addVariableFields(XContentBuilder builder) throws IOException {
    // @formatter:off
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
    // @formatter:on
  }

  private XContentBuilder addNestedVariableField(XContentBuilder builder, String type) throws IOException {
    // @formatter:off
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
    // @formatter:on
  }

  private XContentBuilder addNestedUserTaskField(XContentBuilder builder) throws IOException {
    // @formatter:off
    builder
      .startObject(USER_TASK_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(USER_TASK_ACTIVITY_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(USER_TASK_ACTIVITY_INSTANCE_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(USER_TASK_TOTAL_DURATION)
        .field("type", "long")
      .endObject()
      .startObject(USER_TASK_IDLE_DURATION)
        .field("type", "long")
      .endObject()
      .startObject(USER_TASK_WORK_DURATION)
        .field("type", "long")
      .endObject()
      .startObject(USER_TASK_START_DATE)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(USER_TASK_CLAIM_DATE)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(USER_TASK_END_DATE)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(USER_TASK_DUE_DATE)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(USER_TASK_DELETE_REASON)
        .field("type", "keyword")
      .endObject()
      .startObject(USER_OPERATIONS)
        .field("type", "nested")
        .field("include_in_all", false)
        .startObject("properties");
          addNestedUserOperationsField(builder)
        .endObject()
      .endObject()
      ;
    // @formatter:on
    return builder;
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
