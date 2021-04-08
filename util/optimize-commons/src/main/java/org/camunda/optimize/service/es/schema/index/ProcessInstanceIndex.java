/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import lombok.Setter;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.UserTaskInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.AssigneeOperationDto;
import org.camunda.optimize.dto.optimize.persistence.CandidateGroupOperationDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.camunda.optimize.service.es.schema.DefaultIndexMappingCreator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.FIELDS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.NUMBER_OF_SHARDS_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_PREFIX;

public class ProcessInstanceIndex extends DefaultIndexMappingCreator implements DefinitionBasedType, InstanceType {

  public static final int VERSION = 6;

  public static final String START_DATE = ProcessInstanceDto.Fields.startDate;
  public static final String END_DATE = ProcessInstanceDto.Fields.endDate;
  public static final String DURATION = ProcessInstanceDto.Fields.duration;
  public static final String PROCESS_DEFINITION_KEY = ProcessInstanceDto.Fields.processDefinitionKey;
  public static final String PROCESS_DEFINITION_VERSION = ProcessInstanceDto.Fields.processDefinitionVersion;
  public static final String PROCESS_DEFINITION_ID = ProcessInstanceDto.Fields.processDefinitionId;
  public static final String PROCESS_INSTANCE_ID = ProcessInstanceDto.Fields.processInstanceId;
  public static final String BUSINESS_KEY = ProcessInstanceDto.Fields.businessKey;
  public static final String STATE = ProcessInstanceDto.Fields.state;

  public static final String EVENTS = ProcessInstanceDto.Fields.events;
  public static final String EVENT_ID = FlowNodeInstanceDto.Fields.id;
  public static final String ACTIVITY_ID = FlowNodeInstanceDto.Fields.activityId;
  public static final String ACTIVITY_TYPE = FlowNodeInstanceDto.Fields.activityType;
  // this one is needed for the process part feature. There we can't go up in the nested structured
  // and need to duplicate this data.
  public static final String PROCESS_INSTANCE_ID_FOR_ACTIVITY = FlowNodeInstanceDto.Fields.processInstanceId;
  public static final String ACTIVITY_DURATION = FlowNodeInstanceDto.Fields.durationInMs;
  public static final String ACTIVITY_START_DATE = FlowNodeInstanceDto.Fields.startDate;
  public static final String ACTIVITY_END_DATE = FlowNodeInstanceDto.Fields.endDate;
  public static final String ACTIVITY_CANCELED = FlowNodeInstanceDto.Fields.canceled;

  public static final String VARIABLES = ProcessInstanceDto.Fields.variables;
  public static final String VARIABLE_ID = SimpleProcessVariableDto.Fields.id;
  public static final String VARIABLE_NAME = SimpleProcessVariableDto.Fields.name;
  public static final String VARIABLE_TYPE = SimpleProcessVariableDto.Fields.type;
  public static final String VARIABLE_VALUE = SimpleProcessVariableDto.Fields.value;
  public static final String VARIABLE_VERSION = SimpleProcessVariableDto.Fields.version;

  public static final String USER_TASKS = ProcessInstanceDto.Fields.userTasks;
  public static final String USER_TASK_ID = UserTaskInstanceDto.Fields.id;

  public static final String USER_TASK_ACTIVITY_ID = UserTaskInstanceDto.Fields.activityId;
  public static final String USER_TASK_ACTIVITY_INSTANCE_ID = UserTaskInstanceDto.Fields.activityInstanceId;

  public static final String USER_TASK_TOTAL_DURATION = UserTaskInstanceDto.Fields.totalDurationInMs;
  public static final String USER_TASK_IDLE_DURATION = UserTaskInstanceDto.Fields.idleDurationInMs;
  public static final String USER_TASK_WORK_DURATION = UserTaskInstanceDto.Fields.workDurationInMs;

  public static final String USER_TASK_START_DATE = UserTaskInstanceDto.Fields.startDate;
  public static final String USER_TASK_END_DATE = UserTaskInstanceDto.Fields.endDate;
  public static final String USER_TASK_DUE_DATE = UserTaskInstanceDto.Fields.dueDate;

  public static final String USER_TASK_ASSIGNEE = UserTaskInstanceDto.Fields.assignee;
  public static final String USER_TASK_CANDIDATE_GROUPS = UserTaskInstanceDto.Fields.candidateGroups;
  public static final String USER_TASK_ASSIGNEE_OPERATIONS = UserTaskInstanceDto.Fields.assigneeOperations;
  public static final String USER_TASK_CANDIDATE_GROUP_OPERATIONS =
    UserTaskInstanceDto.Fields.candidateGroupOperations;

  public static final String USER_TASK_DELETE_REASON = UserTaskInstanceDto.Fields.deleteReason;
  public static final String USER_TASK_CANCELED = UserTaskInstanceDto.Fields.canceled;

  public static final String ENGINE = ProcessInstanceDto.Fields.engine;
  public static final String TENANT_ID = ProcessInstanceDto.Fields.tenantId;

  public static final String ASSIGNEE_OPERATION_ID = AssigneeOperationDto.Fields.id;
  public static final String ASSIGNEE_OPERATION_USER_ID = AssigneeOperationDto.Fields.userId;
  public static final String ASSIGNEE_OPERATION_TYPE = AssigneeOperationDto.Fields.operationType;
  public static final String ASSIGNEE_OPERATION_TIMESTAMP = AssigneeOperationDto.Fields.timestamp;

  public static final String CANDIDATE_GROUP_OPERATION_ID = CandidateGroupOperationDto.Fields.id;
  public static final String CANDIDATE_GROUP_OPERATION_GROUP_ID = CandidateGroupOperationDto.Fields.groupId;
  public static final String CANDIDATE_GROUP_OPERATION_TYPE = CandidateGroupOperationDto.Fields.operationType;
  public static final String CANDIDATE_GROUP_OPERATION_TIMESTAMP = CandidateGroupOperationDto.Fields.timestamp;

  public static final String INCIDENTS = ProcessInstanceDto.Fields.incidents;
  public static final String INCIDENT_ID = IncidentDto.Fields.id;
  public static final String INCIDENT_CREATE_TIME = IncidentDto.Fields.createTime;
  public static final String INCIDENT_END_TIME = IncidentDto.Fields.endTime;
  public static final String INCIDENT_DURATION_IN_MS = IncidentDto.Fields.durationInMs;
  public static final String INCIDENT_INCIDENT_TYPE = IncidentDto.Fields.incidentType;
  public static final String INCIDENT_ACTIVITY_ID = IncidentDto.Fields.activityId;
  public static final String INCIDENT_FAILED_ACTIVITY_ID = IncidentDto.Fields.failedActivityId;
  public static final String INCIDENT_MESSAGE = IncidentDto.Fields.incidentMessage;
  public static final String INCIDENT_STATUS = IncidentDto.Fields.incidentStatus;

  public ProcessInstanceIndex(final String instanceIndexKey) {
    indexName = getIndexPrefix() + instanceIndexKey.toLowerCase();
  }

  @Setter
  private String indexName;

  @Override
  public String getIndexName() {
    return indexName;
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
            .endObject()
            .startObject(INCIDENTS)
              .field("type", "nested")
              .startObject("properties");
                addNestedIncidentField(newBuilder)
              .endObject()
            .endObject();
    return newBuilder;
    // @formatter:on
  }

  protected String getIndexPrefix(){
    return PROCESS_INSTANCE_INDEX_PREFIX;
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
      .startObject(PROCESS_INSTANCE_ID_FOR_ACTIVITY)
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
      .startObject(ACTIVITY_CANCELED)
        .field("type", "boolean")
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
        .startObject(FIELDS);
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
      .startObject(USER_TASK_CANCELED)
        .field("type", "boolean")
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

  private XContentBuilder addNestedIncidentField(XContentBuilder builder) throws IOException {
    // @formatter:off
    return builder
      .startObject(INCIDENT_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(INCIDENT_CREATE_TIME)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(INCIDENT_END_TIME)
        .field("type", "date")
        .field("format", OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(INCIDENT_DURATION_IN_MS)
        .field("type", "long")
      .endObject()
      .startObject(INCIDENT_INCIDENT_TYPE)
        .field("type", "keyword")
      .endObject()
      .startObject(INCIDENT_ACTIVITY_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(INCIDENT_FAILED_ACTIVITY_ID)
        .field("type", "keyword")
      .endObject()
      .startObject(INCIDENT_MESSAGE)
        .field("type", "text")
        .field("index", true)
      .endObject()
      .startObject(INCIDENT_STATUS)
        .field("type", "keyword")
      .endObject()
      ;
    // @formatter:on
  }

  @Override
  public XContentBuilder getStaticSettings(XContentBuilder xContentBuilder,
                                           ConfigurationService configurationService) throws IOException {
    return xContentBuilder.field(NUMBER_OF_SHARDS_SETTING, configurationService.getEsNumberOfShards());
  }

}
