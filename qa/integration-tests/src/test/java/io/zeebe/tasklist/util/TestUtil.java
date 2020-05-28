/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util;

import java.io.IOException;
import java.util.UUID;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class TestUtil {

  private static final Logger logger = LoggerFactory.getLogger(TestUtil.class);

  //
//  public static final String ERROR_MSG = "No more retries left.";
//  private static Random random = new Random();
//
  public static String createRandomString(int length) {
    return UUID.randomUUID().toString().substring(0, length);
  }
//
//  public static WorkflowInstanceForListViewEntity createWorkflowInstance(WorkflowInstanceState state) {
//    return createWorkflowInstance(state, null);
//  }
//
//  public static WorkflowInstanceForListViewEntity createWorkflowInstance(WorkflowInstanceState state, Long workflowId) {
//    WorkflowInstanceForListViewEntity workflowInstance = createWorkflowInstanceEntityWithIds();
//
//    workflowInstance.setStartDate(DateUtil.getRandomStartDate());
//    if (state.equals(WorkflowInstanceState.COMPLETED) || state.equals(WorkflowInstanceState.CANCELED)) {
//      final OffsetDateTime endDate = DateUtil.getRandomEndDate();
//      workflowInstance.setEndDate(endDate);
//    }
//    workflowInstance.setState(state);
//    if (workflowId != null) {
//      workflowInstance.setWorkflowKey(workflowId);
//      workflowInstance.setBpmnProcessId("testProcess" + workflowId);
//      //no workflow name to test sorting
//      workflowInstance.setWorkflowVersion(random.nextInt(10));
//    } else {
//      final int i = random.nextInt(10);
//      workflowInstance.setWorkflowKey(Long.valueOf(i));
//      workflowInstance.setBpmnProcessId("testProcess" + i);
//      workflowInstance.setWorkflowName(UUID.randomUUID().toString());
//      workflowInstance.setWorkflowVersion(i);
//    }
//    if(StringUtils.isEmpty(workflowInstance.getWorkflowName())){
//      workflowInstance.setWorkflowName(workflowInstance.getBpmnProcessId());
//    }
//    workflowInstance.setPartitionId(1);
//    return workflowInstance;
//  }
//
//  public static WorkflowInstanceForListViewEntity createWorkflowInstance(OffsetDateTime startDate, OffsetDateTime endDate) {
//    WorkflowInstanceForListViewEntity workflowInstance = createWorkflowInstanceEntityWithIds();
//    final int i = random.nextInt(10);
//    workflowInstance.setBpmnProcessId("testProcess" + i);
//    workflowInstance.setWorkflowName("Test process" + i);
//    workflowInstance.setWorkflowVersion(i);
//    workflowInstance.setStartDate(startDate);
//    workflowInstance.setState(WorkflowInstanceState.ACTIVE);
//    if (endDate != null) {
//      workflowInstance.setEndDate(endDate);
//      workflowInstance.setState(WorkflowInstanceState.COMPLETED);
//    }
//    workflowInstance.setPartitionId(1);
//    return workflowInstance;
//  }
//
//  public static ActivityInstanceForListViewEntity createActivityInstanceWithIncident(Long workflowInstanceKey, ActivityState state, String errorMsg, Long incidentKey) {
//    ActivityInstanceForListViewEntity activityInstanceForListViewEntity = createActivityInstance(workflowInstanceKey, state);
//    createIncident(activityInstanceForListViewEntity, errorMsg, incidentKey);
//    return activityInstanceForListViewEntity;
//  }
//
//  public static void createIncident(ActivityInstanceForListViewEntity activityInstanceForListViewEntity, String errorMsg, Long incidentKey) {
//    if (incidentKey != null) {
//      activityInstanceForListViewEntity.setIncidentKey(incidentKey);
//    } else {
//      activityInstanceForListViewEntity.setIncidentKey((long)random.nextInt());
//    }
//    if (errorMsg != null) {
//      activityInstanceForListViewEntity.setErrorMessage(errorMsg);
//    } else {
//      activityInstanceForListViewEntity.setErrorMessage(ERROR_MSG);
//    }
//  }
//
//  public static ActivityInstanceForListViewEntity createActivityInstance(Long workflowInstanceKey, ActivityState state) {
//    return createActivityInstance(workflowInstanceKey, state, "start", null);
//  }
//
//  public static ActivityInstanceForListViewEntity createActivityInstance(Long workflowInstanceKey, ActivityState state, String activityId, ActivityType activityType) {
//    ActivityInstanceForListViewEntity activityInstanceEntity = new ActivityInstanceForListViewEntity();
//    activityInstanceEntity.setWorkflowInstanceKey(workflowInstanceKey);
//    Long activityInstanceId = random.nextLong();
//    activityInstanceEntity.setId(activityInstanceId.toString());
//    activityInstanceEntity.setActivityId(activityId);
//    activityInstanceEntity.setActivityType(activityType);
//    activityInstanceEntity.setActivityState(state);
//    activityInstanceEntity.getJoinRelation().setParent(workflowInstanceKey);
//    activityInstanceEntity.setPartitionId(1);
//    return activityInstanceEntity;
//  }
//
//  public static ActivityInstanceForListViewEntity createActivityInstance(Long workflowInstanceKey, ActivityState state, String activityId) {
//    return createActivityInstance(workflowInstanceKey, state, activityId, ActivityType.SERVICE_TASK);
//  }
//
//
//  public static WorkflowInstanceForListViewEntity createWorkflowInstanceEntity(WorkflowInstanceState state) {
//    return createWorkflowInstanceEntity(state, null);
//  }
//
//  public static WorkflowInstanceForListViewEntity createWorkflowInstanceEntity(WorkflowInstanceState state, Long workflowKey) {
//    WorkflowInstanceForListViewEntity workflowInstance = createWorkflowInstanceEntityWithIds();
//    final int i = random.nextInt(10);
//    workflowInstance.setBpmnProcessId("testProcess" + i);
//    workflowInstance.setWorkflowName("Test process" + i);
//    workflowInstance.setWorkflowVersion(i);
//    workflowInstance.setStartDate(DateUtil.getRandomStartDate());
//    if (state.equals(WorkflowInstanceState.COMPLETED) || state.equals(WorkflowInstanceState.CANCELED)) {
//      final OffsetDateTime endDate = DateUtil.getRandomEndDate();
//      workflowInstance.setEndDate(endDate);
//    }
//    workflowInstance.setState(state);
//    workflowInstance.setWorkflowKey(workflowKey);
//    workflowInstance.setPartitionId(1);
//    return workflowInstance;
//  }
//
//  public static WorkflowInstanceForListViewEntity createWorkflowInstanceEntityWithIds() {
//    WorkflowInstanceForListViewEntity workflowInstance = new WorkflowInstanceForListViewEntity();
//    Long workflowInstanceKey = Math.abs(random.nextLong());
//    workflowInstance.setId(workflowInstanceKey.toString());
//    workflowInstance.setWorkflowInstanceKey(workflowInstanceKey);
//    workflowInstance.setKey(workflowInstanceKey);
//    workflowInstance.setPartitionId(1);
//    return workflowInstance;
//  }
//
//  public static WorkflowInstanceForListViewEntity createWorkflowInstanceEntity(OffsetDateTime startDate, OffsetDateTime endDate) {
//    WorkflowInstanceForListViewEntity workflowInstance = createWorkflowInstanceEntityWithIds();
//    final int i = random.nextInt(10);
//    workflowInstance.setBpmnProcessId("testProcess" + i);
//    workflowInstance.setWorkflowName("Test process" + i);
//    workflowInstance.setWorkflowVersion(i);
//    workflowInstance.setStartDate(startDate);
//    workflowInstance.setState(WorkflowInstanceState.ACTIVE);
//    if (endDate != null) {
//      workflowInstance.setEndDate(endDate);
//      workflowInstance.setState(WorkflowInstanceState.COMPLETED);
//    }
//    workflowInstance.setPartitionId(1);
//    return workflowInstance;
//  }
//
//  public static IncidentEntity createIncident(IncidentState state) {
//    return createIncident(state, "start", random.nextLong(), null);
//  }
//
//  public static IncidentEntity createIncident(IncidentState state, Long incidentKey, Long workflowInstanceKey) {
//    return createIncident(state, "start", random.nextLong(), null, incidentKey, workflowInstanceKey);
//  }
//
//  public static IncidentEntity createIncident(IncidentState state, String errorMsg) {
//    return createIncident(state, "start", random.nextLong(), errorMsg);
//  }
//
//  public static IncidentEntity createIncident(IncidentState state, String activityId, Long activityInstanceId) {
//    return createIncident(state, activityId, activityInstanceId, null);
//  }
//
//  public static IncidentEntity createIncident(IncidentState state, String activityId, Long activityInstanceId, String errorMsg) {
//    return createIncident(state, activityId, activityInstanceId, errorMsg, null);
//  }
//
//  public static IncidentEntity createIncident(IncidentState state, String activityId, Long activityInstanceId, String errorMsg, Long incidentKey) {
//    return createIncident(state, activityId, activityInstanceId, errorMsg, incidentKey, null);
//  }
//
//  public static IncidentEntity createIncident(IncidentState state, String activityId, Long activityInstanceId, String errorMsg, Long incidentKey, Long workflowInstanceKey) {
//    IncidentEntity incidentEntity = new IncidentEntity();
//    if (incidentKey == null) {
//      incidentEntity.setKey(random.nextLong());
//      incidentEntity.setId(String.valueOf(incidentEntity.getKey()));
//    } else {
//      incidentEntity.setKey(incidentKey);
//      incidentEntity.setId(String.valueOf(incidentKey));
//    }
//    incidentEntity.setFlowNodeId(activityId);
//    incidentEntity.setFlowNodeInstanceKey(activityInstanceId);
//    incidentEntity.setErrorType(JOB_NO_RETRIES);
//    if (errorMsg == null) {
//      incidentEntity.setErrorMessage(ERROR_MSG);
//    } else {
//      incidentEntity.setErrorMessage(errorMsg);
//    }
//    incidentEntity.setState(state);
//    incidentEntity.setPartitionId(1);
//    incidentEntity.setWorkflowInstanceKey(workflowInstanceKey);
//    return incidentEntity;
//  }
//
//  public static List<WorkflowEntity> createWorkflowVersions(String bpmnProcessId, String name, int versionsCount) {
//    List<WorkflowEntity> result = new ArrayList<>();
//    Random workflowIdGenerator =  new Random();
//    for (int i = 1; i <= versionsCount; i++) {
//      WorkflowEntity workflowEntity = new WorkflowEntity();
//      Long workflowId = workflowIdGenerator.nextLong();
//      workflowEntity.setKey(workflowId);
//      workflowEntity.setId(workflowId.toString());
//      workflowEntity.setBpmnProcessId(bpmnProcessId);
//      workflowEntity.setName(name + i);
//      workflowEntity.setVersion(i);
//      result.add(workflowEntity);
//    }
//    return result;
//  }
//
//  public static ListViewRequestDto createWorkflowInstanceQuery(Consumer<ListViewRequestDto> filtersSupplier) {
//    ListViewRequestDto query = new ListViewRequestDto();
//    filtersSupplier.accept(query);
//    return query;
//  }
//
//  public static ListViewRequestDto createGetAllWorkflowInstancesQuery() {
//    return
//      createWorkflowInstanceQuery(q -> {
//        q.setRunning(true);
//        q.setActive(true);
//        q.setIncidents(true);
//        q.setFinished(true);
//        q.setCompleted(true);
//        q.setCanceled(true);
//      });
//  }
//
//  public static ListViewRequestDto createGetAllWorkflowInstancesQuery(Consumer<ListViewRequestDto> filtersSupplier) {
//    final ListViewRequestDto workflowInstanceQuery = createGetAllWorkflowInstancesQuery();
//    filtersSupplier.accept(workflowInstanceQuery);
//    return workflowInstanceQuery;
//  }
//
//  public static ListViewRequestDto createGetAllFinishedQuery(Consumer<ListViewRequestDto> filtersSupplier) {
//    final ListViewRequestDto workflowInstanceQuery = createGetAllFinishedQuery();
//    filtersSupplier.accept(workflowInstanceQuery);
//    return workflowInstanceQuery;
//  }
//
//  public static ListViewRequestDto createGetAllFinishedQuery() {
//    return
//      createWorkflowInstanceQuery(q -> {
//        q.setFinished(true);
//        q.setCompleted(true);
//        q.setCanceled(true);
//      });
//  }
//
//  public static ListViewRequestDto createGetAllRunningQuery() {
//    return
//      createWorkflowInstanceQuery(q -> {
//        q.setRunning(true);
//        q.setActive(true);
//        q.setIncidents(true);
//      });
//  }
//
//  public static VariableForListViewEntity createVariableForListView(Long workflowInstanceKey, Long scopeKey, String name, String value) {
//    VariableForListViewEntity variable = new VariableForListViewEntity();
//    variable.setId(scopeKey + "_" + name);
//    variable.setWorkflowInstanceKey(workflowInstanceKey);
//    variable.setScopeKey(scopeKey);
//    variable.setVarName(name);
//    variable.setVarValue(value);
//    variable.getJoinRelation().setParent(workflowInstanceKey);
//    return variable;
//  }
//
//  public static VariableEntity createVariable(Long workflowInstanceKey, Long scopeKey, String name, String value) {
//    VariableEntity variable = new VariableEntity();
//    variable.setId(scopeKey + "_" + name);
//    variable.setWorkflowInstanceKey(workflowInstanceKey);
//    variable.setScopeKey(scopeKey);
//    variable.setName(name);
//    variable.setName(value);
//    return variable;
//  }
//
//
  public static void removeAllIndices(RestHighLevelClient esClient, String prefix) {
    try {
      logger.info("Removing indices");
      esClient.indices().delete(new DeleteIndexRequest(prefix + "*"), RequestOptions.DEFAULT);
      esClient.indices().deleteTemplate(new DeleteIndexTemplateRequest(prefix + "*"), RequestOptions.DEFAULT);
    } catch (ElasticsearchStatusException | IOException ex) {
      //do nothing
    }
  }
//
//  public static OperationEntity createOperationEntity(Long workflowInstanceKey, Long incidentKey, String varName, String username) {
//    return createOperationEntity(workflowInstanceKey, incidentKey, varName, OperationState.SCHEDULED, username, false);
//  }
//
//  public static OperationEntity createOperationEntity(Long workflowInstanceKey, Long incidentKey, String varName, OperationState state, String username, boolean lockExpired) {
//    OperationEntity oe = new OperationEntity();
//    oe.generateId();
//    oe.setWorkflowInstanceKey(workflowInstanceKey);
//    oe.setIncidentKey(incidentKey);
//    oe.setVariableName(varName);
//    oe.setType(OperationType.RESOLVE_INCIDENT);
//    if (username != null) {
//      oe.setUsername(username);
//    } else {
//      oe.setUsername(TasklistIntegrationTest.DEFAULT_USER);
//    }
//    oe.setState(state);
//    if (state.equals(OperationState.LOCKED)) {
//      if (lockExpired) {
//        oe.setLockExpirationTime(OffsetDateTime.now().minus(1, ChronoUnit.MILLIS));
//      } else {
//        oe.setLockExpirationTime(OffsetDateTime.now().plus(LOCK_TIMEOUT_DEFAULT, ChronoUnit.MILLIS));
//      }
//      oe.setLockOwner("otherWorkerId");
//    }
//    return oe;
//  }
//
//  public static OperationEntity createOperationEntity(Long workflowInstanceKey, OperationState state, boolean lockExpired) {
//    return createOperationEntity(workflowInstanceKey, null, null, state, null, lockExpired);
//  }
//
//  public static OperationEntity createOperationEntity(Long workflowInstanceKey, OperationState state) {
//    return createOperationEntity(workflowInstanceKey, null, null, state, null, false);
//  }
//
//  public static BatchOperationEntity createBatchOperationEntity(OffsetDateTime startDate, OffsetDateTime endDate, String username) {
//    return new BatchOperationEntity()
//        .setId(UUID.randomUUID().toString())
//        .setStartDate(startDate)
//        .setEndDate(endDate)
//        .setUsername(username)
//        .setType(OperationType.CANCEL_WORKFLOW_INSTANCE);
//  }

}
