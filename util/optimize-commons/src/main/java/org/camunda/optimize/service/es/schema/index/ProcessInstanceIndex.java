/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.persistence.AssigneeOperationDto;
import org.camunda.optimize.dto.optimize.persistence.CandidateGroupOperationDto;
import org.camunda.optimize.service.es.schema.StrictIndexMappingCreator;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;

@Component
public class ProcessInstanceIndex extends StrictIndexMappingCreator implements DefinitionBasedType, InstanceType {

  public static final int VERSION = 4;

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

  public static final String VARIABLES = "variables";
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

  public static final String USER_TASK_ASSIGNEE = "assignee";
  public static final String USER_TASK_CANDIDATE_GROUPS = "candidateGroups";
  public static final String USER_TASK_ASSIGNEE_OPERATIONS = "assigneeOperations";
  public static final String USER_TASK_CANDIDATE_GROUP_OPERATIONS = "candidateGroupOperations";

  public static final String USER_TASK_DELETE_REASON = "deleteReason";

  public static final String ENGINE = "engine";
  public static final String TENANT_ID = "tenantId";

  public static final String ASSIGNEE_OPERATION_ID = AssigneeOperationDto.Fields.id.name();
  public static final String ASSIGNEE_OPERATION_USER_ID = AssigneeOperationDto.Fields.userId.name();
  public static final String ASSIGNEE_OPERATION_TYPE = AssigneeOperationDto.Fields.operationType.name();
  public static final String ASSIGNEE_OPERATION_TIMESTAMP = AssigneeOperationDto.Fields.timestamp.name();

  public static final String CANDIDATE_GROUP_OPERATION_ID = CandidateGroupOperationDto.Fields.id.name();
  public static final String CANDIDATE_GROUP_OPERATION_GROUP_ID = CandidateGroupOperationDto.Fields.groupId.name();
  public static final String CANDIDATE_GROUP_OPERATION_TYPE = CandidateGroupOperationDto.Fields.operationType.name();
  public static final String CANDIDATE_GROUP_OPERATION_TIMESTAMP = CandidateGroupOperationDto.Fields.timestamp.name();

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.PROCESS_INSTANCE_INDEX_NAME;
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
              .startObject("properties");
                addNestedEventField(newBuilder)
              .endObject()
            .endObject()
            .startObject(USER_TASKS)
              .field("type", "nested")
              .startObject("properties");
                addNestedUserTaskField(newBuilder)
              .endObject()
            .endObject()
            .startObject(VARIABLES)
              .field("type", "nested")
              .startObject("properties");
                addNestedVariableField(newBuilder)
              .endObject()
            .endObject();
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


  private XContentBuilder addNestedVariableField(XContentBuilder builder) throws IOException {
    // @formatter:off
    builder
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
        .field("type", "keyword")
        .startObject("fields");
          addValueMultifields(builder)
        .endObject()
      .endObject()
      .startObject(VARIABLE_VERSION)
        .field("type", "long")
      .endObject();
    return builder;
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
      .startObject(USER_TASK_ASSIGNEE)
        .field("type", "keyword")
      .endObject()
      .startObject(USER_TASK_CANDIDATE_GROUPS)
        .field("type", "keyword")
      .endObject()
      .startObject(USER_TASK_ASSIGNEE_OPERATIONS)
        .field("type", "nested")
        .startObject("properties");
          addNestedAssigneeOperations(builder)
        .endObject()
      .endObject()
      .startObject(USER_TASK_CANDIDATE_GROUP_OPERATIONS)
        .field("type", "nested")
        .startObject("properties");
          addNestedCandidateGroupOperations(builder)
        .endObject()
      .endObject()
      ;
    // @formatter:on
    return builder;
  }

  private XContentBuilder addNestedAssigneeOperations(final XContentBuilder builder) throws IOException {
    // @formatter:off
    builder
      .startObject(ASSIGNEE_OPERATION_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(ASSIGNEE_OPERATION_USER_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(ASSIGNEE_OPERATION_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(ASSIGNEE_OPERATION_TIMESTAMP)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      ;
    return builder;
    // @formatter:on
  }

  private XContentBuilder addNestedCandidateGroupOperations(final XContentBuilder builder) throws IOException {
    // @formatter:off
    builder
      .startObject(CANDIDATE_GROUP_OPERATION_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(CANDIDATE_GROUP_OPERATION_GROUP_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(CANDIDATE_GROUP_OPERATION_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(CANDIDATE_GROUP_OPERATION_TIMESTAMP)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      ;
    return builder;
    // @formatter:on
  }
}
