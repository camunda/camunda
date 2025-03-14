/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static io.camunda.config.operate.OperationExecutorProperties.LOCK_TIMEOUT_DEFAULT;
import static io.camunda.operate.schema.SchemaManager.OPERATE_DELETE_ARCHIVED_INDICES;
import static io.camunda.operate.util.OperateAbstractIT.DEFAULT_USER;
import static io.camunda.webapps.schema.entities.AbstractExporterEntity.DEFAULT_TENANT_ID;
import static io.camunda.webapps.schema.entities.operate.ErrorType.JOB_NO_RETRIES;

import io.camunda.operate.store.opensearch.client.sync.OpenSearchIndexOperations;
import io.camunda.operate.store.opensearch.client.sync.OpenSearchTemplateOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.webapps.operate.TreePath;
import io.camunda.webapps.schema.entities.operate.EventEntity;
import io.camunda.webapps.schema.entities.operate.FlowNodeState;
import io.camunda.webapps.schema.entities.operate.FlowNodeType;
import io.camunda.webapps.schema.entities.operate.IncidentEntity;
import io.camunda.webapps.schema.entities.operate.IncidentState;
import io.camunda.webapps.schema.entities.operate.ProcessEntity;
import io.camunda.webapps.schema.entities.operate.VariableEntity;
import io.camunda.webapps.schema.entities.operate.dmn.DecisionInstanceEntity;
import io.camunda.webapps.schema.entities.operate.dmn.DecisionInstanceInputEntity;
import io.camunda.webapps.schema.entities.operate.dmn.DecisionInstanceOutputEntity;
import io.camunda.webapps.schema.entities.operate.dmn.DecisionInstanceState;
import io.camunda.webapps.schema.entities.operate.dmn.DecisionType;
import io.camunda.webapps.schema.entities.operate.listview.FlowNodeInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceState;
import io.camunda.webapps.schema.entities.operate.listview.VariableForListViewEntity;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationState;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indexlifecycle.DeleteLifecyclePolicyRequest;
import org.elasticsearch.client.indices.DeleteComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.GetComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

public abstract class TestUtil {

  public static final String ERROR_MSG = "No more retries left.";
  public static final Integer ERROR_MSG_HASH_CODE = ERROR_MSG.hashCode();
  private static final Logger LOGGER = LoggerFactory.getLogger(TestUtil.class);
  private static final Random RANDOM = new Random();

  public static String createRandomString(final int length) {
    return UUID.randomUUID().toString().substring(0, length);
  }

  public static ProcessInstanceForListViewEntity createProcessInstance() {
    return createProcessInstance(ProcessInstanceState.ACTIVE, null, false, null);
  }

  public static ProcessInstanceForListViewEntity createProcessInstance(
      final ProcessInstanceState state) {
    return createProcessInstance(state, null, false, null);
  }

  public static ProcessInstanceForListViewEntity createProcessInstance(
      final ProcessInstanceState state, final boolean incident, final String tenantId) {
    return createProcessInstance(state, null, incident, tenantId);
  }

  public static ProcessInstanceForListViewEntity createProcessInstance(
      final ProcessInstanceState state, final Long processId, final String tenantId) {
    return createProcessInstance(state, processId, null, null, false, tenantId);
  }

  public static ProcessInstanceForListViewEntity createProcessInstance(
      final ProcessInstanceState state, final Long processId) {
    return createProcessInstance(state, processId, null, null, false, null);
  }

  public static ProcessInstanceForListViewEntity createProcessInstance(
      final ProcessInstanceState state, final Long processId, final boolean incident) {
    return createProcessInstance(state, processId, null, null, incident, null);
  }

  public static ProcessInstanceForListViewEntity createProcessInstance(
      final ProcessInstanceState state,
      final Long processId,
      final boolean incident,
      final String tenantId) {
    return createProcessInstance(state, processId, null, null, incident, tenantId);
  }

  public static ProcessInstanceForListViewEntity createProcessInstance(
      final ProcessInstanceState state,
      final Long processId,
      final Long parentInstanceKey,
      final String treePath,
      final String tenantId) {
    return createProcessInstance(state, processId, parentInstanceKey, treePath, false, tenantId);
  }

  public static ProcessInstanceForListViewEntity createProcessInstance(
      final ProcessInstanceState state,
      final Long processId,
      final Long parentInstanceKey,
      final String treePath,
      final boolean incident,
      final String tenantId) {
    final ProcessInstanceForListViewEntity processInstance = createProcessInstanceEntityWithIds();

    processInstance.setStartDate(DateUtil.getRandomStartDate());
    if (state.equals(ProcessInstanceState.COMPLETED)
        || state.equals(ProcessInstanceState.CANCELED)) {
      final OffsetDateTime endDate = DateUtil.getRandomEndDate();
      processInstance.setEndDate(endDate);
    }
    processInstance.setState(state);
    if (processId != null) {
      processInstance.setProcessDefinitionKey(processId);
      processInstance.setBpmnProcessId("testProcess" + processId);
      // no process name to test sorting
      processInstance.setProcessVersion(RANDOM.nextInt(10));
    } else {
      final int i = RANDOM.nextInt(10);
      processInstance.setProcessDefinitionKey(Long.valueOf(i));
      processInstance.setBpmnProcessId("testProcess" + i);
      processInstance.setProcessName(UUID.randomUUID().toString());
      processInstance.setProcessVersion(i);
    }
    if (StringUtils.isEmpty(processInstance.getProcessName())) {
      processInstance.setProcessName(processInstance.getBpmnProcessId());
    }
    processInstance.setPartitionId(1);
    processInstance.setParentProcessInstanceKey(parentInstanceKey);
    if (treePath != null) {
      processInstance.setTreePath(treePath);
    } else {
      processInstance.setTreePath(new TreePath().startTreePath(processInstance.getId()).toString());
    }
    processInstance.setIncident(incident);
    processInstance.setTenantId(tenantId == null ? DEFAULT_TENANT_ID : tenantId);
    return processInstance;
  }

  public static ProcessInstanceForListViewEntity createProcessInstance(
      final OffsetDateTime startDate, final OffsetDateTime endDate) {
    final ProcessInstanceForListViewEntity processInstance = createProcessInstanceEntityWithIds();
    final int i = RANDOM.nextInt(10);
    processInstance.setBpmnProcessId("testProcess" + i);
    processInstance.setProcessName("Test process" + i);
    processInstance.setProcessVersion(i);
    processInstance.setStartDate(startDate);
    processInstance.setState(ProcessInstanceState.ACTIVE);
    if (endDate != null) {
      processInstance.setEndDate(endDate);
      processInstance.setState(ProcessInstanceState.COMPLETED);
    }
    processInstance.setPartitionId(1);
    return processInstance;
  }

  public static FlowNodeInstanceForListViewEntity createFlowNodeInstanceWithIncident(
      final Long processInstanceKey, final FlowNodeState state, final String errorMsg) {
    final FlowNodeInstanceForListViewEntity activityInstanceForListViewEntity =
        createFlowNodeInstance(processInstanceKey, state);
    createIncident(activityInstanceForListViewEntity, errorMsg);
    return activityInstanceForListViewEntity;
  }

  public static void createIncident(
      final FlowNodeInstanceForListViewEntity activityInstanceForListViewEntity,
      final String errorMsg) {
    activityInstanceForListViewEntity.setIncident(true);
    if (errorMsg != null) {
      activityInstanceForListViewEntity.setErrorMessage(errorMsg);
    } else {
      activityInstanceForListViewEntity.setErrorMessage(ERROR_MSG);
    }
  }

  public static FlowNodeInstanceForListViewEntity createFlowNodeInstance(
      final Long processInstanceKey, final FlowNodeState state) {
    return createFlowNodeInstance(
        processInstanceKey, state, "start", FlowNodeType.SERVICE_TASK, null);
  }

  public static FlowNodeInstanceForListViewEntity createFlowNodeInstance(
      final Long processInstanceKey,
      final FlowNodeState state,
      final String activityId,
      final FlowNodeType activityType) {
    return createFlowNodeInstance(processInstanceKey, state, activityId, activityType, null);
  }

  public static FlowNodeInstanceForListViewEntity createFlowNodeInstance(
      final Long processInstanceKey,
      final FlowNodeState state,
      final String activityId,
      final FlowNodeType activityType,
      final Boolean retriesLeft) {
    final FlowNodeInstanceForListViewEntity activityInstanceEntity =
        new FlowNodeInstanceForListViewEntity();
    activityInstanceEntity.setProcessInstanceKey(processInstanceKey);
    final Long activityInstanceId = RANDOM.nextLong();
    activityInstanceEntity.setId(activityInstanceId.toString());
    activityInstanceEntity.setKey(activityInstanceId);
    activityInstanceEntity.setActivityId(activityId);
    activityInstanceEntity.setActivityType(activityType);
    activityInstanceEntity.setActivityState(state);
    activityInstanceEntity.getJoinRelation().setParent(processInstanceKey);
    activityInstanceEntity.setPartitionId(1);
    if (retriesLeft != null) {
      activityInstanceEntity.setJobFailedWithRetriesLeft(retriesLeft);
    }
    return activityInstanceEntity;
  }

  public static FlowNodeInstanceForListViewEntity createFlowNodeInstance(
      final Long processInstanceKey, final FlowNodeState state, final String activityId) {
    return createFlowNodeInstance(
        processInstanceKey, state, activityId, FlowNodeType.SERVICE_TASK, null);
  }

  public static ProcessInstanceForListViewEntity createProcessInstanceEntity(
      final ProcessInstanceState state,
      final Long processDefinitionKey,
      final String bpmnProcessId) {
    return createProcessInstanceEntity(state, processDefinitionKey, bpmnProcessId, false);
  }

  public static ProcessInstanceForListViewEntity createProcessInstanceEntity(
      final ProcessInstanceState state,
      final Long processDefinitionKey,
      final String bpmnProcessId,
      final boolean incident) {
    final ProcessInstanceForListViewEntity processInstance = createProcessInstanceEntityWithIds();
    final int i = RANDOM.nextInt(10);
    processInstance.setBpmnProcessId(bpmnProcessId);
    processInstance.setProcessName("Test process" + i);
    processInstance.setProcessVersion(i);
    processInstance.setStartDate(DateUtil.getRandomStartDate());
    if (state.equals(ProcessInstanceState.COMPLETED)
        || state.equals(ProcessInstanceState.CANCELED)) {
      final OffsetDateTime endDate = DateUtil.getRandomEndDate();
      processInstance.setEndDate(endDate);
    }
    processInstance.setState(state);
    processInstance.setProcessDefinitionKey(processDefinitionKey);
    processInstance.setPartitionId(1);
    processInstance.setIncident(incident);
    return processInstance;
  }

  public static ProcessInstanceForListViewEntity createProcessInstanceEntityWithIds() {
    final ProcessInstanceForListViewEntity processInstance = new ProcessInstanceForListViewEntity();
    final Long processInstanceKey = Math.abs(RANDOM.nextLong());
    processInstance.setId(processInstanceKey.toString());
    processInstance.setProcessInstanceKey(processInstanceKey);
    processInstance.setKey(processInstanceKey);
    processInstance.setPartitionId(1);
    processInstance.setTreePath(
        new TreePath().startTreePath(processInstanceKey.toString()).toString());
    return processInstance;
  }

  public static ProcessInstanceForListViewEntity createProcessInstanceEntity(
      final OffsetDateTime startDate, final OffsetDateTime endDate) {
    final ProcessInstanceForListViewEntity processInstance = createProcessInstanceEntityWithIds();
    final int i = RANDOM.nextInt(10);
    processInstance.setBpmnProcessId("testProcess" + i);
    processInstance.setProcessName("Test process" + i);
    processInstance.setProcessVersion(i);
    processInstance.setStartDate(startDate);
    processInstance.setState(ProcessInstanceState.ACTIVE);
    if (endDate != null) {
      processInstance.setEndDate(endDate);
      processInstance.setState(ProcessInstanceState.COMPLETED);
    }
    processInstance.setPartitionId(1);
    return processInstance;
  }

  public static IncidentEntity createIncident(final IncidentState state) {
    return createIncident(state, "start", RANDOM.nextLong(), null);
  }

  public static IncidentEntity createIncident(
      final IncidentState state, final Long incidentKey, final Long processInstanceKey) {
    return createIncident(
        state, "start", RANDOM.nextLong(), null, incidentKey, processInstanceKey, null, null);
  }

  public static IncidentEntity createIncident(
      final IncidentState state,
      final Long incidentKey,
      final Long processInstanceKey,
      final Long processDefinitionKey) {
    return createIncident(
        state,
        "start",
        RANDOM.nextLong(),
        null,
        incidentKey,
        processInstanceKey,
        processDefinitionKey,
        null);
  }

  public static EventEntity createEvent() {
    return createEvent(RANDOM.nextLong(), RANDOM.nextLong());
  }

  public static EventEntity createEvent(
      final long processInstanceKey, final Long flowNodeInstanceKey) {
    return new EventEntity()
        .setId(String.format("%s_%s", processInstanceKey, flowNodeInstanceKey))
        .setProcessInstanceKey(processInstanceKey)
        .setFlowNodeInstanceKey(flowNodeInstanceKey)
        .setPartitionId(1)
        .setTenantId(DEFAULT_TENANT_ID);
  }

  public static IncidentEntity createIncident(final IncidentState state, final String errorMsg) {
    return createIncident(state, "start", RANDOM.nextLong(), errorMsg);
  }

  public static IncidentEntity createIncident(
      final IncidentState state, final String activityId, final Long activityInstanceId) {
    return createIncident(state, activityId, activityInstanceId, null);
  }

  public static IncidentEntity createIncident(
      final IncidentState state,
      final String activityId,
      final Long activityInstanceId,
      final String errorMsg) {
    return createIncident(state, activityId, activityInstanceId, errorMsg, null);
  }

  public static IncidentEntity createIncident(
      final IncidentState state,
      final String activityId,
      final Long activityInstanceId,
      final String errorMsg,
      final Long incidentKey) {
    return createIncident(
        state, activityId, activityInstanceId, errorMsg, incidentKey, null, null, null);
  }

  public static IncidentEntity createIncident(
      final IncidentState state,
      final String activityId,
      final Long activityInstanceId,
      final String errorMsg,
      final Long incidentKey,
      final Long processInstanceKey,
      final Long processDefinitionKey,
      final String bpmnProcessId) {
    final IncidentEntity incidentEntity = new IncidentEntity();
    if (incidentKey == null) {
      incidentEntity.setKey(RANDOM.nextLong());
      incidentEntity.setId(String.valueOf(incidentEntity.getKey()));
    } else {
      incidentEntity.setKey(incidentKey);
      incidentEntity.setId(String.valueOf(incidentKey));
    }
    incidentEntity.setFlowNodeId(activityId);
    incidentEntity.setFlowNodeInstanceKey(activityInstanceId);
    incidentEntity.setErrorType(JOB_NO_RETRIES);
    if (errorMsg == null) {
      incidentEntity.setErrorMessage(ERROR_MSG);
    } else {
      incidentEntity.setErrorMessage(errorMsg);
    }
    incidentEntity.setState(state);
    incidentEntity.setPartitionId(1);
    incidentEntity.setProcessInstanceKey(processInstanceKey);
    incidentEntity.setTreePath(
        new TreePath()
            .startTreePath(String.valueOf(processInstanceKey))
            .appendFlowNode(activityId)
            .appendFlowNodeInstance(String.valueOf(activityInstanceId))
            .toString());
    if (processDefinitionKey != null) {
      incidentEntity.setProcessDefinitionKey(processDefinitionKey);
    }
    incidentEntity.setBpmnProcessId(bpmnProcessId);
    return incidentEntity;
  }

  public static List<ProcessEntity> createProcessVersions(
      final String bpmnProcessId,
      final String name,
      final int versionsCount,
      final String tenantId) {
    final List<ProcessEntity> result = new ArrayList<>();
    final Random processIdGenerator = new Random();
    for (int i = 1; i <= versionsCount; i++) {
      final ProcessEntity processEntity = new ProcessEntity();
      final Long processId = processIdGenerator.nextLong();
      processEntity.setKey(processId);
      processEntity.setId(processId.toString());
      processEntity.setBpmnProcessId(bpmnProcessId);
      processEntity.setTenantId(tenantId);
      processEntity.setName(name + i);
      processEntity.setVersion(i);
      result.add(processEntity);
    }
    return result;
  }

  public static VariableForListViewEntity createVariableForListView(final Long processInstanceKey) {
    final String name = UUID.randomUUID().toString();
    final String value = UUID.randomUUID().toString();
    return createVariableForListView(processInstanceKey, processInstanceKey, name, value);
  }

  public static VariableForListViewEntity createVariableForListView(
      final Long processInstanceKey, final Long scopeKey, final String name, final String value) {
    final VariableForListViewEntity variable = new VariableForListViewEntity();
    variable.setId(VariableForListViewEntity.getIdBy(scopeKey, name));
    variable.setProcessInstanceKey(processInstanceKey);
    variable.setScopeKey(scopeKey);
    variable.setVarName(name);
    variable.setVarValue(value);
    variable.getJoinRelation().setParent(processInstanceKey);
    return variable;
  }

  public static VariableEntity createVariable(
      final Long processInstanceKey, final Long scopeKey, final String name, final String value) {
    return createVariable(processInstanceKey, null, null, scopeKey, name, value);
  }

  public static VariableEntity createVariable(
      final Long processInstanceKey,
      final Long processDefinitionKey,
      final String bpmnProcessId,
      final Long scopeKey,
      final String name,
      final String value) {
    final VariableEntity variable = new VariableEntity();
    variable.setId(scopeKey + "-" + name);
    variable.setProcessInstanceKey(processInstanceKey);
    variable.setProcessDefinitionKey(processDefinitionKey);
    variable.setBpmnProcessId(bpmnProcessId);
    variable.setScopeKey(scopeKey);
    variable.setName(name);
    variable.setValue(value);
    variable.setFullValue(value);
    return variable;
  }

  public static void removeAllIndices(final RestHighLevelClient esClient, final String prefix) {
    try {
      LOGGER.info("Removing indices");
      final var indexResponses =
          esClient.indices().get(new GetIndexRequest(prefix + "*"), RequestOptions.DEFAULT);
      for (final String index : indexResponses.getIndices()) {
        esClient.indices().delete(new DeleteIndexRequest(index), RequestOptions.DEFAULT);
      }
      final var templateResponses =
          esClient
              .indices()
              .getIndexTemplate(
                  new GetComposableIndexTemplateRequest(prefix + "*"), RequestOptions.DEFAULT);
      for (final String template : templateResponses.getIndexTemplates().keySet()) {
        esClient
            .indices()
            .deleteIndexTemplate(
                new DeleteComposableIndexTemplateRequest(template), RequestOptions.DEFAULT);
      }
    } catch (final ElasticsearchStatusException | IOException ex) {
      LOGGER.error(ex.getMessage(), ex);
    }
  }

  public static void removeAllIndices(
      final OpenSearchIndexOperations indexOperations,
      final OpenSearchTemplateOperations templateOperations,
      final String prefix) {
    try {
      LOGGER.info("Removing indices");
      indexOperations.deleteIndicesWithRetries(prefix + "*");
      templateOperations.deleteTemplatesWithRetries(prefix + "*");
    } catch (final Exception ex) {
      LOGGER.error(ex.getMessage(), ex);
    }
  }

  public static void removeIlmPolicy(final RestHighLevelClient esClient) {
    try {
      LOGGER.info("Removing ILM policy " + OPERATE_DELETE_ARCHIVED_INDICES);
      final var request = new DeleteLifecyclePolicyRequest(OPERATE_DELETE_ARCHIVED_INDICES);
      esClient.indexLifecycle().deleteLifecyclePolicy(request, RequestOptions.DEFAULT);
    } catch (final ElasticsearchStatusException | IOException ex) {
      LOGGER.error(ex.getMessage(), ex);
    }
  }

  public static void removeIlmPolicy(final RichOpenSearchClient richOpenSearchClient) {
    try {
      LOGGER.info("Removing ILM policy " + OPERATE_DELETE_ARCHIVED_INDICES);
      richOpenSearchClient.ism().deletePolicy(OPERATE_DELETE_ARCHIVED_INDICES);
    } catch (final Exception ex) {
      LOGGER.error(ex.getMessage(), ex);
    }
  }

  public static OperationEntity createOperationEntity(
      final Long processInstanceKey,
      final Long incidentKey,
      final String varName,
      final String username) {
    return createOperationEntity(
        processInstanceKey, incidentKey, varName, OperationState.SCHEDULED, username, false);
  }

  public static OperationEntity createOperationEntity(
      final Long processInstanceKey,
      final Long incidentKey,
      final String varName,
      final OperationState state,
      final String username,
      final boolean lockExpired) {
    return createOperationEntity(
        processInstanceKey, null, null, incidentKey, varName, state, username, lockExpired);
  }

  public static OperationEntity createOperationEntity(
      final Long processInstanceKey,
      final Long processDefinitionKey,
      final String bpmnProcessId,
      final Long incidentKey,
      final String varName,
      final OperationState state,
      final String username,
      final boolean lockExpired) {
    final OperationEntity oe =
        new OperationEntity()
            .withGeneratedId()
            .setProcessInstanceKey(processInstanceKey)
            .setScopeKey(processInstanceKey)
            .setProcessDefinitionKey(processDefinitionKey)
            .setBpmnProcessId(bpmnProcessId)
            .setIncidentKey(incidentKey)
            .setVariableName(varName);
    if (varName != null) {
      oe.setType(OperationType.UPDATE_VARIABLE);
      oe.setVariableValue(varName);
    } else {
      oe.setType(OperationType.RESOLVE_INCIDENT);
    }
    if (username != null) {
      oe.setUsername(username);
    } else {
      oe.setUsername(DEFAULT_USER);
    }
    oe.setState(state);
    if (state.equals(OperationState.LOCKED)) {
      if (lockExpired) {
        oe.setLockExpirationTime(OffsetDateTime.now().minus(1, ChronoUnit.MILLIS));
      } else {
        oe.setLockExpirationTime(
            OffsetDateTime.now().plus(LOCK_TIMEOUT_DEFAULT, ChronoUnit.MILLIS));
      }
      oe.setLockOwner("otherWorkerId");
    }
    return oe;
  }

  public static OperationEntity createOperationEntity(
      final Long processInstanceKey, final OperationState state, final boolean lockExpired) {
    return createOperationEntity(processInstanceKey, null, null, state, null, lockExpired);
  }

  public static OperationEntity createOperationEntity(
      final Long processInstanceKey, final OperationState state) {
    return createOperationEntity(processInstanceKey, null, null, state, null, false);
  }

  public static BatchOperationEntity createBatchOperationEntity(
      final OffsetDateTime startDate, final OffsetDateTime endDate, final String username) {
    return new BatchOperationEntity()
        .withGeneratedId()
        .setStartDate(startDate)
        .setEndDate(endDate)
        .setUsername(username)
        .setType(OperationType.CANCEL_PROCESS_INSTANCE);
  }

  public static DecisionInstanceEntity createDecisionInstanceEntity() {
    final DecisionInstanceEntity decisionInstance = new DecisionInstanceEntity();
    final long key = Math.abs(RANDOM.nextLong());
    decisionInstance
        .setId(String.valueOf(key))
        .setKey(key)
        .setDecisionId(UUID.randomUUID().toString())
        .setDecisionDefinitionId(String.valueOf(Math.abs(RANDOM.nextLong())))
        .setDecisionId("decisionId")
        .setDecisionName("Decision Name")
        .setDecisionRequirementsId(UUID.randomUUID().toString())
        .setDecisionRequirementsKey(Math.abs(RANDOM.nextLong()))
        .setDecisionType(DecisionType.DECISION_TABLE)
        .setElementId("businessTask")
        .setElementInstanceKey(Math.abs(RANDOM.nextLong()))
        .setEvaluationDate(OffsetDateTime.now())
        .setPosition(Math.abs(RANDOM.nextLong()))
        .setProcessDefinitionKey(Math.abs(RANDOM.nextLong()))
        .setProcessInstanceKey(Math.abs(RANDOM.nextLong()))
        .setResult("someJSON")
        .setState(DecisionInstanceState.EVALUATED)
        .setEvaluatedInputs(createDecisionInstanceInputs())
        .setEvaluatedOutputs(createDecisionOutputs());
    return decisionInstance;
  }

  private static List<DecisionInstanceOutputEntity> createDecisionOutputs() {
    final List<DecisionInstanceOutputEntity> outputs = new ArrayList<>();
    outputs.add(
        new DecisionInstanceOutputEntity()
            .setId("output1")
            .setName("Output 1")
            .setValue("output1")
            .setRuleId("rule1")
            .setRuleIndex(1));
    outputs.add(
        new DecisionInstanceOutputEntity()
            .setId("output2")
            .setName("Output 2")
            .setValue("output2")
            .setRuleId("rule2")
            .setRuleIndex(2));
    return outputs;
  }

  private static List<DecisionInstanceInputEntity> createDecisionInstanceInputs() {
    final List<DecisionInstanceInputEntity> inputs = new ArrayList<>();
    inputs.add(
        new DecisionInstanceInputEntity().setId("input1").setName("Input 1").setValue("value1"));
    inputs.add(
        new DecisionInstanceInputEntity().setId("input2").setName("Input 2").setValue("value2"));
    return inputs;
  }
}
