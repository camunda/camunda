/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import static io.zeebe.protocol.record.value.ErrorType.JOB_NO_RETRIES;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.ActivityType;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.SequenceFlowEntity;
import org.camunda.operate.entities.WorkflowEntity;
import org.camunda.operate.entities.listview.ActivityInstanceForListViewEntity;
import org.camunda.operate.entities.listview.VariableForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.entities.listview.WorkflowInstanceState;
import org.camunda.operate.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.rest.dto.listview.ListViewRequestDto;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;


public abstract class TestUtil {

  private static final Logger logger = LoggerFactory.getLogger(TestUtil.class);

  public static final String ERROR_MSG = "No more retries left.";
  private static Random random = new Random();

  public static String createRandomString(int length) {
    return UUID.randomUUID().toString().substring(0, length);
  }

  public static WorkflowInstanceForListViewEntity createWorkflowInstance(WorkflowInstanceState state) {
    return createWorkflowInstance(state, null);
  }

  public static WorkflowInstanceForListViewEntity createWorkflowInstance(WorkflowInstanceState state, Long workflowId) {
    WorkflowInstanceForListViewEntity workflowInstance = createWorkflowInstanceEntityWithIds();

    workflowInstance.setStartDate(DateUtil.getRandomStartDate());
    if (state.equals(WorkflowInstanceState.COMPLETED) || state.equals(WorkflowInstanceState.CANCELED)) {
      final OffsetDateTime endDate = DateUtil.getRandomEndDate();
      workflowInstance.setEndDate(endDate);
    }
    workflowInstance.setState(state);
    if (workflowId != null) {
      workflowInstance.setWorkflowKey(workflowId);
      workflowInstance.setBpmnProcessId("testProcess" + workflowId);
      //no workflow name to test sorting
      workflowInstance.setWorkflowVersion(random.nextInt(10));
    } else {
      final int i = random.nextInt(10);
      workflowInstance.setWorkflowKey(Long.valueOf(i));
      workflowInstance.setBpmnProcessId("testProcess" + i);
      workflowInstance.setWorkflowName(UUID.randomUUID().toString());
      workflowInstance.setWorkflowVersion(i);
    }
    if(StringUtils.isEmpty(workflowInstance.getWorkflowName())){
      workflowInstance.setWorkflowName(workflowInstance.getBpmnProcessId());
    }
    return workflowInstance;
  }

  public static WorkflowInstanceForListViewEntity createWorkflowInstance(OffsetDateTime startDate, OffsetDateTime endDate) {
    WorkflowInstanceForListViewEntity workflowInstance = createWorkflowInstanceEntityWithIds();
    final int i = random.nextInt(10);
    workflowInstance.setBpmnProcessId("testProcess" + i);
    workflowInstance.setWorkflowName("Test process" + i);
    workflowInstance.setWorkflowVersion(i);
    workflowInstance.setStartDate(startDate);
    workflowInstance.setState(WorkflowInstanceState.ACTIVE);
    if (endDate != null) {
      workflowInstance.setEndDate(endDate);
      workflowInstance.setState(WorkflowInstanceState.COMPLETED);
    }
    return workflowInstance;
  }

  public static ActivityInstanceForListViewEntity createActivityInstanceWithIncident(Long workflowInstanceId, ActivityState state, String errorMsg, Long incidentKey) {
    ActivityInstanceForListViewEntity activityInstanceForListViewEntity = createActivityInstance(workflowInstanceId, state);
    createIncident(activityInstanceForListViewEntity, errorMsg, incidentKey);
    return activityInstanceForListViewEntity;
  }

  public static void createIncident(ActivityInstanceForListViewEntity activityInstanceForListViewEntity, String errorMsg, Long incidentKey) {
    if (incidentKey != null) {
      activityInstanceForListViewEntity.setIncidentKey(incidentKey);
    } else {
      activityInstanceForListViewEntity.setIncidentKey((long)random.nextInt());
    }
    if (errorMsg != null) {
      activityInstanceForListViewEntity.setErrorMessage(errorMsg);
    } else {
      activityInstanceForListViewEntity.setErrorMessage(ERROR_MSG);
    }
  }

  public static ActivityInstanceForListViewEntity createActivityInstance(Long workflowInstanceId, ActivityState state) {
    return createActivityInstance(workflowInstanceId, state, "start", null);
  }

  public static ActivityInstanceForListViewEntity createActivityInstance(Long workflowInstanceId, ActivityState state, String activityId, ActivityType activityType) {
    ActivityInstanceForListViewEntity activityInstanceEntity = new ActivityInstanceForListViewEntity();
    activityInstanceEntity.setWorkflowInstanceKey(workflowInstanceId);
    Long activityInstanceId = random.nextLong();
    activityInstanceEntity.setId(activityInstanceId.toString());
    activityInstanceEntity.setActivityId(activityId);
    activityInstanceEntity.setActivityType(activityType);
    activityInstanceEntity.setActivityState(state);
    activityInstanceEntity.getJoinRelation().setParent(workflowInstanceId);
    return activityInstanceEntity;
  }

  public static ActivityInstanceForListViewEntity createActivityInstance(Long workflowInstanceId, ActivityState state, String activityId) {
    return createActivityInstance(workflowInstanceId, state, activityId, ActivityType.SERVICE_TASK);
  }


  public static WorkflowInstanceForListViewEntity createWorkflowInstanceEntity(WorkflowInstanceState state) {
    return createWorkflowInstanceEntity(state, null);
  }

  public static WorkflowInstanceForListViewEntity createWorkflowInstanceEntity(WorkflowInstanceState state, Long workflowKey) {
    WorkflowInstanceForListViewEntity workflowInstance = createWorkflowInstanceEntityWithIds();
    final int i = random.nextInt(10);
    workflowInstance.setBpmnProcessId("testProcess" + i);
    workflowInstance.setWorkflowName("Test process" + i);
    workflowInstance.setWorkflowVersion(i);
    workflowInstance.setStartDate(DateUtil.getRandomStartDate());
    if (state.equals(WorkflowInstanceState.COMPLETED) || state.equals(WorkflowInstanceState.CANCELED)) {
      final OffsetDateTime endDate = DateUtil.getRandomEndDate();
      workflowInstance.setEndDate(endDate);
    }
    workflowInstance.setState(state);
    workflowInstance.setWorkflowKey(workflowKey);
    return workflowInstance;
  }
  
  public static WorkflowInstanceForListViewEntity createWorkflowInstanceEntityWithIds() {
    WorkflowInstanceForListViewEntity workflowInstance = new WorkflowInstanceForListViewEntity();
    Long workflowInstanceId = random.nextLong();
    workflowInstance.setId(workflowInstanceId.toString());
    workflowInstance.setWorkflowInstanceKey(workflowInstanceId);
    workflowInstance.setKey(workflowInstanceId);
    return workflowInstance;
  }

  public static WorkflowInstanceForListViewEntity createWorkflowInstanceEntity(OffsetDateTime startDate, OffsetDateTime endDate) {
    WorkflowInstanceForListViewEntity workflowInstance = createWorkflowInstanceEntityWithIds();
    final int i = random.nextInt(10);
    workflowInstance.setBpmnProcessId("testProcess" + i);
    workflowInstance.setWorkflowName("Test process" + i);
    workflowInstance.setWorkflowVersion(i);
    workflowInstance.setStartDate(startDate);
    workflowInstance.setState(WorkflowInstanceState.ACTIVE);
    if (endDate != null) {
      workflowInstance.setEndDate(endDate);
      workflowInstance.setState(WorkflowInstanceState.COMPLETED);
    }
    return workflowInstance;
  }

  public static IncidentEntity createIncident(IncidentState state) {
    return createIncident(state, "start", random.nextLong(), null);
  }

  public static IncidentEntity createIncident(IncidentState state, String errorMsg) {
    return createIncident(state, "start", random.nextLong(), errorMsg);
  }

  public static IncidentEntity createIncident(IncidentState state, String activityId, Long activityInstanceId) {
    return createIncident(state, activityId, activityInstanceId, null);
  }

  public static IncidentEntity createIncident(IncidentState state, String activityId, Long activityInstanceId, String errorMsg) {
    IncidentEntity incidentEntity = new IncidentEntity();
    incidentEntity.setKey(random.nextLong());
    incidentEntity.setId(String.valueOf(incidentEntity.getKey()));
    incidentEntity.setFlowNodeId(activityId);
    incidentEntity.setFlowNodeInstanceKey(activityInstanceId);
    incidentEntity.setErrorType(JOB_NO_RETRIES);
    if (errorMsg == null) {
      incidentEntity.setErrorMessage(ERROR_MSG);
    } else {
      incidentEntity.setErrorMessage(errorMsg);
    }
    incidentEntity.setState(state);
    return incidentEntity;
  }

  public static List<WorkflowEntity> createWorkflowVersions(String bpmnProcessId, String name, int versionsCount) {
    List<WorkflowEntity> result = new ArrayList<>();
    Random workflowIdGenerator =  new Random();
    for (int i = 1; i <= versionsCount; i++) {
      WorkflowEntity workflowEntity = new WorkflowEntity();
      Long workflowId = workflowIdGenerator.nextLong();
      workflowEntity.setKey(workflowId);
      workflowEntity.setId(workflowId.toString());
      workflowEntity.setBpmnProcessId(bpmnProcessId);
      workflowEntity.setName(name + i);
      workflowEntity.setVersion(i);
      result.add(workflowEntity);
    }
    return result;
  }

  public static SequenceFlowEntity createSequenceFlow() {
    SequenceFlowEntity sequenceFlowEntity = new SequenceFlowEntity();
    sequenceFlowEntity.setId(UUID.randomUUID().toString());
    sequenceFlowEntity.setActivityId("SequenceFlow_" + random.nextInt());
    return sequenceFlowEntity;
  }

  public static ListViewRequestDto createWorkflowInstanceQuery(Consumer<ListViewQueryDto> filtersSupplier) {
    ListViewRequestDto request = new ListViewRequestDto();
    ListViewQueryDto query = new ListViewQueryDto();
    filtersSupplier.accept(query);
    request.getQueries().add(query);
    return request;
  }

  public static ListViewRequestDto createGetAllWorkflowInstancesQuery() {
    return
      createWorkflowInstanceQuery(q -> {
        q.setRunning(true);
        q.setActive(true);
        q.setIncidents(true);
        q.setFinished(true);
        q.setCompleted(true);
        q.setCanceled(true);
      });
  }

  public static ListViewRequestDto createGetAllWorkflowInstancesQuery(Consumer<ListViewQueryDto> filtersSupplier) {
    final ListViewRequestDto workflowInstanceQuery = createGetAllWorkflowInstancesQuery();
    filtersSupplier.accept(workflowInstanceQuery.getQueries().get(0));

    return workflowInstanceQuery;
  }

  public static ListViewRequestDto createGetAllFinishedQuery(Consumer<ListViewQueryDto> filtersSupplier) {
    final ListViewRequestDto workflowInstanceQuery = createGetAllFinishedQuery();
    filtersSupplier.accept(workflowInstanceQuery.getQueries().get(0));
    return workflowInstanceQuery;
  }

  public static ListViewRequestDto createGetAllFinishedQuery() {
    return
      createWorkflowInstanceQuery(q -> {
        q.setFinished(true);
        q.setCompleted(true);
        q.setCanceled(true);
      });
  }

  public static VariableForListViewEntity createVariable(Long workflowInstanceId, Long scopeKey, String name, String value) {
    VariableForListViewEntity variable = new VariableForListViewEntity();
    variable.setId(scopeKey + "_" + name);
    variable.setWorkflowInstanceKey(workflowInstanceId);
    variable.setScopeKey(scopeKey);
    variable.setVarName(name);
    variable.setVarValue(value);
    variable.getJoinRelation().setParent(workflowInstanceId);
    return variable;
  }


  public static void removeAllIndices(RestHighLevelClient esClient, String prefix) {
    try {
      logger.info("Removing indices");
      esClient.indices().delete(new DeleteIndexRequest(prefix + "*"), RequestOptions.DEFAULT);
    } catch (IOException ex) {
      //do nothing
    }
  }
}
