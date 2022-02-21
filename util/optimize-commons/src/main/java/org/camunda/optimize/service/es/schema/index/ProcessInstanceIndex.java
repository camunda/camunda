/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index;

import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.AssigneeOperationDto;
import org.camunda.optimize.dto.optimize.persistence.CandidateGroupOperationDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.FIELDS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.FORMAT_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.IGNORE_ABOVE_CHAR_LIMIT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.IGNORE_ABOVE_SETTING;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.MAPPING_PROPERTY_TYPE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.OPTIMIZE_DATE_FORMAT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_BOOLEAN;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_DATE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_KEYWORD;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_LONG;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_NESTED;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_OBJECT;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.TYPE_TEXT;

public class ProcessInstanceIndex extends AbstractInstanceIndex {

  public static final int VERSION = 8;

  public static final String START_DATE = ProcessInstanceDto.Fields.startDate;
  public static final String END_DATE = ProcessInstanceDto.Fields.endDate;
  public static final String DURATION = ProcessInstanceDto.Fields.duration;
  public static final String PROCESS_DEFINITION_KEY = ProcessInstanceDto.Fields.processDefinitionKey;
  public static final String PROCESS_DEFINITION_VERSION = ProcessInstanceDto.Fields.processDefinitionVersion;
  public static final String PROCESS_DEFINITION_ID = ProcessInstanceDto.Fields.processDefinitionId;
  public static final String PROCESS_INSTANCE_ID = ProcessInstanceDto.Fields.processInstanceId;
  public static final String BUSINESS_KEY = ProcessInstanceDto.Fields.businessKey;
  public static final String STATE = ProcessInstanceDto.Fields.state;
  public static final String DATA_SOURCE = ProcessInstanceDto.Fields.dataSource;
  public static final String TENANT_ID = ProcessInstanceDto.Fields.tenantId;

  // FlowNode Instance Fields
  public static final String FLOW_NODE_INSTANCES = ProcessInstanceDto.Fields.flowNodeInstances;
  public static final String FLOW_NODE_INSTANCE_ID = FlowNodeInstanceDto.Fields.flowNodeInstanceId;
  public static final String FLOW_NODE_ID = FlowNodeInstanceDto.Fields.flowNodeId;
  public static final String FLOW_NODE_TYPE = FlowNodeInstanceDto.Fields.flowNodeType;
  // this one is needed for the process part feature. There we can't go up in the nested structured
  // and need to duplicate this data.
  public static final String PROCESS_INSTANCE_ID_FOR_ACTIVITY = FlowNodeInstanceDto.Fields.processInstanceId;
  public static final String FLOW_NODE_START_DATE = FlowNodeInstanceDto.Fields.startDate;
  public static final String FLOW_NODE_END_DATE = FlowNodeInstanceDto.Fields.endDate;
  public static final String FLOW_NODE_CANCELED = FlowNodeInstanceDto.Fields.canceled;
  public static final String FLOW_NODE_TOTAL_DURATION = FlowNodeInstanceDto.Fields.totalDurationInMs;

  public static final String FLOW_NODE_DEFINITION_KEY = FlowNodeInstanceDto.Fields.definitionKey;
  public static final String FLOW_NODE_DEFINITION_VERSION = FlowNodeInstanceDto.Fields.definitionVersion;
  public static final String FLOW_NODE_TENANT_ID = FlowNodeInstanceDto.Fields.tenantId;

  public static final String USER_TASK_INSTANCE_ID = FlowNodeInstanceDto.Fields.userTaskInstanceId;
  public static final String USER_TASK_DUE_DATE = FlowNodeInstanceDto.Fields.dueDate;
  public static final String USER_TASK_DELETE_REASON = FlowNodeInstanceDto.Fields.deleteReason;
  public static final String USER_TASK_IDLE_DURATION = FlowNodeInstanceDto.Fields.idleDurationInMs;
  public static final String USER_TASK_WORK_DURATION = FlowNodeInstanceDto.Fields.workDurationInMs;
  public static final String USER_TASK_ASSIGNEE = FlowNodeInstanceDto.Fields.assignee;
  public static final String USER_TASK_CANDIDATE_GROUPS = FlowNodeInstanceDto.Fields.candidateGroups;
  public static final String USER_TASK_ASSIGNEE_OPERATIONS = FlowNodeInstanceDto.Fields.assigneeOperations;
  public static final String USER_TASK_CANDIDATE_GROUP_OPERATIONS =
    FlowNodeInstanceDto.Fields.candidateGroupOperations;

  public static final String ASSIGNEE_OPERATION_ID = AssigneeOperationDto.Fields.id;
  public static final String ASSIGNEE_OPERATION_USER_ID = AssigneeOperationDto.Fields.userId;
  public static final String ASSIGNEE_OPERATION_TYPE = AssigneeOperationDto.Fields.operationType;
  public static final String ASSIGNEE_OPERATION_TIMESTAMP = AssigneeOperationDto.Fields.timestamp;

  public static final String CANDIDATE_GROUP_OPERATION_ID = CandidateGroupOperationDto.Fields.id;
  public static final String CANDIDATE_GROUP_OPERATION_GROUP_ID = CandidateGroupOperationDto.Fields.groupId;
  public static final String CANDIDATE_GROUP_OPERATION_TYPE = CandidateGroupOperationDto.Fields.operationType;
  public static final String CANDIDATE_GROUP_OPERATION_TIMESTAMP = CandidateGroupOperationDto.Fields.timestamp;

  // Variable Fields
  public static final String VARIABLES = ProcessInstanceDto.Fields.variables;
  public static final String VARIABLE_ID = SimpleProcessVariableDto.Fields.id;
  public static final String VARIABLE_NAME = SimpleProcessVariableDto.Fields.name;
  public static final String VARIABLE_TYPE = SimpleProcessVariableDto.Fields.type;
  public static final String VARIABLE_VALUE = SimpleProcessVariableDto.Fields.value;
  public static final String VARIABLE_VERSION = SimpleProcessVariableDto.Fields.version;

  // Incident Fields
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
  public static final String INCIDENT_DEFINITION_KEY = IncidentDto.Fields.definitionKey;
  public static final String INCIDENT_DEFINITION_VERSION = IncidentDto.Fields.definitionVersion;
  public static final String INCIDENT_TENANT_ID = IncidentDto.Fields.tenantId;

  public ProcessInstanceIndex(final String processInstanceIndexKey) {
    indexName = getIndexPrefix() + processInstanceIndexKey.toLowerCase();
  }

  private final String indexName;

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
    XContentBuilder newBuilder = builder
      .startObject(PROCESS_DEFINITION_KEY)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(PROCESS_DEFINITION_VERSION)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(PROCESS_DEFINITION_ID)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(PROCESS_INSTANCE_ID)
       .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(BUSINESS_KEY)
       .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(START_DATE)
       .field(MAPPING_PROPERTY_TYPE, TYPE_DATE)
        .field(FORMAT_PROPERTY_TYPE, OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(END_DATE)
        .field(MAPPING_PROPERTY_TYPE, TYPE_DATE)
        .field(FORMAT_PROPERTY_TYPE, OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(DURATION)
        .field(MAPPING_PROPERTY_TYPE, TYPE_LONG)
      .endObject()
      .startObject(DATA_SOURCE)
        .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
        .field("dynamic", true)
      .endObject()
      .startObject(TENANT_ID)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(STATE)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(FLOW_NODE_INSTANCES)
        .field(MAPPING_PROPERTY_TYPE, TYPE_NESTED)
      .startObject("properties");

    addNestedFlowNodeInstancesField(newBuilder)
      .endObject()
      .endObject()
      .startObject(VARIABLES)
        .field(MAPPING_PROPERTY_TYPE, TYPE_NESTED)
      .startObject("properties");

    addNestedVariableField(newBuilder)
      .endObject()
      .endObject()
      .startObject(INCIDENTS)
        .field(MAPPING_PROPERTY_TYPE, TYPE_NESTED)
      .startObject("properties");

    addNestedIncidentField(newBuilder)
      .endObject()
      .endObject();
    return newBuilder;
    // @formatter:on
  }

  protected String getIndexPrefix() {
    return PROCESS_INSTANCE_INDEX_PREFIX;
  }

  private XContentBuilder addNestedFlowNodeInstancesField(XContentBuilder builder) throws IOException {
    // @formatter:off
    builder
      .startObject(FLOW_NODE_INSTANCE_ID)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(FLOW_NODE_ID)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(PROCESS_INSTANCE_ID_FOR_ACTIVITY)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(FLOW_NODE_TYPE)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(FLOW_NODE_TOTAL_DURATION)
        .field(MAPPING_PROPERTY_TYPE, TYPE_LONG)
      .endObject()
      .startObject(FLOW_NODE_START_DATE)
        .field(MAPPING_PROPERTY_TYPE, TYPE_DATE)
        .field(FORMAT_PROPERTY_TYPE, OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(FLOW_NODE_END_DATE)
        .field(MAPPING_PROPERTY_TYPE, TYPE_DATE)
        .field(FORMAT_PROPERTY_TYPE, OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(FLOW_NODE_CANCELED)
        .field(MAPPING_PROPERTY_TYPE, TYPE_BOOLEAN)
      .endObject()
      .startObject(FLOW_NODE_DEFINITION_KEY)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(FLOW_NODE_DEFINITION_VERSION)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(FLOW_NODE_TENANT_ID)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(USER_TASK_INSTANCE_ID)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(USER_TASK_IDLE_DURATION)
        .field(MAPPING_PROPERTY_TYPE, TYPE_LONG)
      .endObject()
      .startObject(USER_TASK_WORK_DURATION)
        .field(MAPPING_PROPERTY_TYPE, TYPE_LONG)
      .endObject()
      .startObject(USER_TASK_DUE_DATE)
        .field(MAPPING_PROPERTY_TYPE, TYPE_DATE)
        .field(FORMAT_PROPERTY_TYPE, OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(USER_TASK_DELETE_REASON)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(USER_TASK_ASSIGNEE)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(USER_TASK_CANDIDATE_GROUPS)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(USER_TASK_ASSIGNEE_OPERATIONS)
       .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
       .startObject("properties");

    addAssigneeOperationProperties(builder)
      .endObject()
      .endObject()
      .startObject(USER_TASK_CANDIDATE_GROUP_OPERATIONS)
       .field(MAPPING_PROPERTY_TYPE, TYPE_OBJECT)
      .startObject("properties");

    addCandidateGroupOperationProperties(builder)
      .endObject()
      .endObject();
    // @formatter:on
    return builder;
  }

  private XContentBuilder addNestedVariableField(XContentBuilder builder) throws IOException {
    // @formatter:off
    builder
      .startObject(VARIABLE_ID)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(VARIABLE_NAME)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(VARIABLE_TYPE)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(VARIABLE_VALUE)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
        .field(IGNORE_ABOVE_SETTING, IGNORE_ABOVE_CHAR_LIMIT)
      .startObject(FIELDS);
    addValueMultifields(builder)
      .endObject()
      .endObject()
      .startObject(VARIABLE_VERSION)
        .field(MAPPING_PROPERTY_TYPE, TYPE_LONG)
      .endObject();
    return builder;
    // @formatter:on
  }

  private XContentBuilder addAssigneeOperationProperties(final XContentBuilder builder) throws IOException {
    // @formatter:off
    builder
      .startObject(ASSIGNEE_OPERATION_ID)
       .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(ASSIGNEE_OPERATION_USER_ID)
       .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(ASSIGNEE_OPERATION_TYPE)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(ASSIGNEE_OPERATION_TIMESTAMP)
        .field(MAPPING_PROPERTY_TYPE, TYPE_DATE)
        .field(FORMAT_PROPERTY_TYPE, OPTIMIZE_DATE_FORMAT)
      .endObject();
    return builder;
    // @formatter:on
  }

  private XContentBuilder addCandidateGroupOperationProperties(final XContentBuilder builder) throws IOException {
    // @formatter:off
    builder
      .startObject(CANDIDATE_GROUP_OPERATION_ID)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(CANDIDATE_GROUP_OPERATION_GROUP_ID)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(CANDIDATE_GROUP_OPERATION_TYPE)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(CANDIDATE_GROUP_OPERATION_TIMESTAMP)
        .field(MAPPING_PROPERTY_TYPE, TYPE_DATE)
        .field(FORMAT_PROPERTY_TYPE, OPTIMIZE_DATE_FORMAT)
      .endObject();
    return builder;
    // @formatter:on
  }

  private XContentBuilder addNestedIncidentField(XContentBuilder builder) throws IOException {
    // @formatter:off
    return builder
      .startObject(INCIDENT_ID)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(INCIDENT_CREATE_TIME)
        .field(MAPPING_PROPERTY_TYPE, TYPE_DATE)
        .field(FORMAT_PROPERTY_TYPE, OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(INCIDENT_END_TIME)
        .field(MAPPING_PROPERTY_TYPE, TYPE_DATE)
        .field(FORMAT_PROPERTY_TYPE, OPTIMIZE_DATE_FORMAT)
      .endObject()
      .startObject(INCIDENT_DURATION_IN_MS)
        .field(MAPPING_PROPERTY_TYPE, TYPE_LONG)
      .endObject()
      .startObject(INCIDENT_INCIDENT_TYPE)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(INCIDENT_ACTIVITY_ID)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(INCIDENT_FAILED_ACTIVITY_ID)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(INCIDENT_MESSAGE)
        .field(MAPPING_PROPERTY_TYPE, TYPE_TEXT)
        .field("index", true)
      .endObject()
      .startObject(INCIDENT_STATUS)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(INCIDENT_DEFINITION_KEY)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(INCIDENT_DEFINITION_VERSION)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject()
      .startObject(INCIDENT_TENANT_ID)
        .field(MAPPING_PROPERTY_TYPE, TYPE_KEYWORD)
      .endObject();
    // @formatter:on
  }

}
