/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.schema.index;

import static io.camunda.optimize.service.db.DatabaseConstants.IGNORE_ABOVE_CHAR_LIMIT;
import static io.camunda.optimize.service.db.DatabaseConstants.OPTIMIZE_DATE_FORMAT;
import static io.camunda.optimize.service.db.DatabaseConstants.PROCESS_INSTANCE_INDEX_PREFIX;

import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.persistence.AssigneeOperationDto;
import io.camunda.optimize.dto.optimize.persistence.CandidateGroupOperationDto;
import io.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import io.camunda.optimize.dto.optimize.query.variable.SimpleProcessVariableDto;
import java.util.Locale;

public abstract class ProcessInstanceIndex<TBuilder> extends AbstractInstanceIndex<TBuilder> {

  public static final int VERSION = 8;

  public static final String START_DATE = ProcessInstanceDto.Fields.startDate;
  public static final String END_DATE = ProcessInstanceDto.Fields.endDate;
  public static final String DURATION = ProcessInstanceDto.Fields.duration;
  public static final String PROCESS_DEFINITION_KEY =
      ProcessInstanceDto.Fields.processDefinitionKey;
  public static final String PROCESS_DEFINITION_VERSION =
      ProcessInstanceDto.Fields.processDefinitionVersion;
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
  public static final String PROCESS_INSTANCE_ID_FOR_ACTIVITY =
      FlowNodeInstanceDto.Fields.processInstanceId;
  public static final String FLOW_NODE_START_DATE = FlowNodeInstanceDto.Fields.startDate;
  public static final String FLOW_NODE_END_DATE = FlowNodeInstanceDto.Fields.endDate;
  public static final String FLOW_NODE_CANCELED = FlowNodeInstanceDto.Fields.canceled;
  public static final String FLOW_NODE_TOTAL_DURATION =
      FlowNodeInstanceDto.Fields.totalDurationInMs;

  public static final String FLOW_NODE_DEFINITION_KEY = FlowNodeInstanceDto.Fields.definitionKey;
  public static final String FLOW_NODE_DEFINITION_VERSION =
      FlowNodeInstanceDto.Fields.definitionVersion;
  public static final String FLOW_NODE_TENANT_ID = FlowNodeInstanceDto.Fields.tenantId;

  public static final String USER_TASK_INSTANCE_ID = FlowNodeInstanceDto.Fields.userTaskInstanceId;
  public static final String USER_TASK_DUE_DATE = FlowNodeInstanceDto.Fields.dueDate;
  public static final String USER_TASK_DELETE_REASON = FlowNodeInstanceDto.Fields.deleteReason;
  public static final String USER_TASK_IDLE_DURATION = FlowNodeInstanceDto.Fields.idleDurationInMs;
  public static final String USER_TASK_WORK_DURATION = FlowNodeInstanceDto.Fields.workDurationInMs;
  public static final String USER_TASK_ASSIGNEE = FlowNodeInstanceDto.Fields.assignee;
  public static final String USER_TASK_CANDIDATE_GROUPS =
      FlowNodeInstanceDto.Fields.candidateGroups;
  public static final String USER_TASK_ASSIGNEE_OPERATIONS =
      FlowNodeInstanceDto.Fields.assigneeOperations;
  public static final String USER_TASK_CANDIDATE_GROUP_OPERATIONS =
      FlowNodeInstanceDto.Fields.candidateGroupOperations;

  public static final String ASSIGNEE_OPERATION_ID = AssigneeOperationDto.Fields.id;
  public static final String ASSIGNEE_OPERATION_USER_ID = AssigneeOperationDto.Fields.userId;
  public static final String ASSIGNEE_OPERATION_TYPE = AssigneeOperationDto.Fields.operationType;
  public static final String ASSIGNEE_OPERATION_TIMESTAMP = AssigneeOperationDto.Fields.timestamp;

  public static final String CANDIDATE_GROUP_OPERATION_ID = CandidateGroupOperationDto.Fields.id;
  public static final String CANDIDATE_GROUP_OPERATION_GROUP_ID =
      CandidateGroupOperationDto.Fields.groupId;
  public static final String CANDIDATE_GROUP_OPERATION_TYPE =
      CandidateGroupOperationDto.Fields.operationType;
  public static final String CANDIDATE_GROUP_OPERATION_TIMESTAMP =
      CandidateGroupOperationDto.Fields.timestamp;

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
  private final String indexName;

  protected ProcessInstanceIndex(final String processInstanceIndexKey) {
    super(processInstanceIndexKey);
    indexName = getIndexPrefix() + processInstanceIndexKey.toLowerCase(Locale.ENGLISH);
  }

  // This needs to be done separately to the logic of the constructor, because the non-static method
  // getIndexPrefix()
  // will get overridden when a subclass such as EventProcessInstanceIndex is being instantiated
  public static String constructIndexName(final String processInstanceIndexKey) {
    return PROCESS_INSTANCE_INDEX_PREFIX + processInstanceIndexKey.toLowerCase(Locale.ENGLISH);
  }

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
  public TypeMapping.Builder addProperties(final TypeMapping.Builder builder) {
    return builder
        .properties(PROCESS_DEFINITION_KEY, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_VERSION, p -> p.keyword(k -> k))
        .properties(PROCESS_DEFINITION_ID, p -> p.keyword(k -> k))
        .properties(PROCESS_INSTANCE_ID, p -> p.keyword(k -> k))
        .properties(BUSINESS_KEY, p -> p.keyword(k -> k))
        .properties(START_DATE, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(END_DATE, p -> p.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
        .properties(DURATION, p -> p.long_(k -> k))
        .properties(DATA_SOURCE, p -> p.object(k -> k.dynamic(DynamicMapping.True)))
        .properties(TENANT_ID, p -> p.keyword(k -> k))
        .properties(STATE, p -> p.keyword(k -> k))
        .properties(
            FLOW_NODE_INSTANCES,
            p ->
                p.nested(
                    k ->
                        k.properties(FLOW_NODE_INSTANCE_ID, pp -> pp.keyword(kk -> kk))
                            .properties(FLOW_NODE_ID, pp -> pp.keyword(kk -> kk))
                            .properties(
                                PROCESS_INSTANCE_ID_FOR_ACTIVITY, pp -> pp.keyword(kk -> kk))
                            .properties(FLOW_NODE_TYPE, pp -> pp.keyword(kk -> kk))
                            .properties(FLOW_NODE_TOTAL_DURATION, pp -> pp.long_(kk -> kk))
                            .properties(
                                FLOW_NODE_START_DATE,
                                pp -> pp.date(kk -> kk.format(OPTIMIZE_DATE_FORMAT)))
                            .properties(
                                FLOW_NODE_END_DATE,
                                pp -> pp.date(kk -> kk.format(OPTIMIZE_DATE_FORMAT)))
                            .properties(FLOW_NODE_CANCELED, pp -> pp.boolean_(kk -> kk))
                            .properties(FLOW_NODE_DEFINITION_KEY, pp -> pp.keyword(kk -> kk))
                            .properties(FLOW_NODE_DEFINITION_VERSION, pp -> pp.keyword(kk -> kk))
                            .properties(FLOW_NODE_TENANT_ID, pp -> pp.keyword(kk -> kk))
                            .properties(USER_TASK_INSTANCE_ID, pp -> pp.keyword(kk -> kk))
                            .properties(USER_TASK_IDLE_DURATION, pp -> pp.long_(kk -> kk))
                            .properties(USER_TASK_WORK_DURATION, pp -> pp.long_(kk -> kk))
                            .properties(
                                USER_TASK_DUE_DATE,
                                pp -> pp.date(kk -> kk.format(OPTIMIZE_DATE_FORMAT)))
                            .properties(USER_TASK_DELETE_REASON, pp -> pp.keyword(kk -> kk))
                            .properties(USER_TASK_ASSIGNEE, pp -> pp.keyword(kk -> kk))
                            .properties(USER_TASK_CANDIDATE_GROUPS, pp -> pp.keyword(kk -> kk))
                            .properties(
                                USER_TASK_ASSIGNEE_OPERATIONS,
                                pp ->
                                    pp.object(
                                        kk ->
                                            kk.properties(
                                                    ASSIGNEE_OPERATION_ID,
                                                    p2 -> p2.keyword(k2 -> k2))
                                                .properties(
                                                    ASSIGNEE_OPERATION_USER_ID,
                                                    p2 -> p2.keyword(k2 -> k2))
                                                .properties(
                                                    ASSIGNEE_OPERATION_TYPE,
                                                    p2 -> p2.keyword(k2 -> k2))
                                                .properties(
                                                    ASSIGNEE_OPERATION_TIMESTAMP,
                                                    p2 ->
                                                        p2.date(
                                                            k2 ->
                                                                k2.format(OPTIMIZE_DATE_FORMAT)))))
                            .properties(
                                USER_TASK_CANDIDATE_GROUP_OPERATIONS,
                                pp ->
                                    pp.object(
                                        kk ->
                                            kk.properties(
                                                    CANDIDATE_GROUP_OPERATION_ID,
                                                    p2 -> p2.keyword(k2 -> k2))
                                                .properties(
                                                    CANDIDATE_GROUP_OPERATION_GROUP_ID,
                                                    p2 -> p2.keyword(k2 -> k2))
                                                .properties(
                                                    CANDIDATE_GROUP_OPERATION_TYPE,
                                                    p2 -> p2.keyword(k2 -> k2))
                                                .properties(
                                                    CANDIDATE_GROUP_OPERATION_TIMESTAMP,
                                                    p2 ->
                                                        p2.date(
                                                            k2 ->
                                                                k2.format(
                                                                    OPTIMIZE_DATE_FORMAT)))))))
        .properties(
            VARIABLES,
            p ->
                p.nested(
                    n ->
                        n.properties(VARIABLE_ID, np -> np.keyword(k -> k))
                            .properties(VARIABLE_NAME, np -> np.keyword(k -> k))
                            .properties(VARIABLE_TYPE, np -> np.keyword(k -> k))
                            .properties(
                                VARIABLE_VALUE,
                                np ->
                                    np.keyword(
                                        k ->
                                            addValueMultifields(
                                                k.ignoreAbove(IGNORE_ABOVE_CHAR_LIMIT))))
                            .properties(VARIABLE_VERSION, np -> np.long_(l -> l))))
        .properties(
            INCIDENTS,
            p ->
                p.nested(
                    n ->
                        n.properties(INCIDENT_ID, np -> np.keyword(k -> k))
                            .properties(
                                INCIDENT_CREATE_TIME,
                                np -> np.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
                            .properties(
                                INCIDENT_END_TIME,
                                np -> np.date(k -> k.format(OPTIMIZE_DATE_FORMAT)))
                            .properties(INCIDENT_DURATION_IN_MS, np -> np.long_(k -> k))
                            .properties(INCIDENT_INCIDENT_TYPE, np -> np.keyword(k -> k))
                            .properties(INCIDENT_ACTIVITY_ID, np -> np.keyword(k -> k))
                            .properties(INCIDENT_FAILED_ACTIVITY_ID, np -> np.keyword(k -> k))
                            .properties(INCIDENT_MESSAGE, np -> np.text(k -> k.index(true)))
                            .properties(INCIDENT_STATUS, np -> np.keyword(k -> k))
                            .properties(INCIDENT_DEFINITION_KEY, np -> np.keyword(k -> k))
                            .properties(INCIDENT_DEFINITION_VERSION, np -> np.keyword(k -> k))
                            .properties(INCIDENT_TENANT_ID, np -> np.keyword(k -> k))));
  }

  protected String getIndexPrefix() {
    return PROCESS_INSTANCE_INDEX_PREFIX;
  }
}
