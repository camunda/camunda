/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static io.camunda.operate.qa.util.RestAPITestUtil.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.operate.webapp.operation.dto.BatchOperationDto;
import io.camunda.operate.webapp.operation.dto.CreateOperationRequestDto;
import io.camunda.operate.webapp.operation.dto.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.reader.dto.activity.*;
import io.camunda.operate.webapp.reader.dto.incidents.IncidentDto;
import io.camunda.operate.webapp.reader.dto.metadata.FlowNodeMetadataDto;
import io.camunda.operate.webapp.reader.dto.metadata.FlowNodeMetadataRequestDto;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import io.camunda.webapps.schema.entities.flownode.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.flownode.FlowNodeState;
import io.camunda.webapps.schema.entities.flownode.FlowNodeType;
import io.camunda.webapps.schema.entities.operation.OperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.*;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

@Component
@Scope(SCOPE_PROTOTYPE)
public class OperateTester {

  protected static final Logger LOGGER = LoggerFactory.getLogger(OperateTester.class);
  @Autowired protected OperationExecutor operationExecutor;
  @Autowired protected io.camunda.operate.webapp.reader.VariableReader variableReader;
  @Autowired protected IncidentReader incidentReader;
  @Autowired protected FlowNodeInstanceReader flowNodeInstanceReader;

  private final CamundaClient camundaClient;
  private final MockMvcTestRule mockMvcTestRule;
  private final SearchTestRule searchTestRule;
  private Long processDefinitionKey;
  private Long processInstanceKey;
  private Long jobKey;
  private JwtDecoder jwtDecoder;

  @Autowired
  @Qualifier("processIsDeployedCheck")
  private Predicate<Object[]> processIsDeployedCheck;

  @Autowired
  @Qualifier("decisionsAreDeployedCheck")
  private Predicate<Object[]> decisionsAreDeployedCheck;

  @Autowired
  @Qualifier("decisionInstancesAreCreated")
  private Predicate<Object[]> decisionInstancesAreCreated;

  @Autowired
  @Qualifier("processInstancesAreStartedCheck")
  private Predicate<Object[]> processInstancesAreStartedCheck;

  @Autowired
  @Qualifier("processInstancesAreFinishedCheck")
  private Predicate<Object[]> processInstancesAreFinishedCheck;

  @Autowired
  @Qualifier("processInstanceIsCompletedCheck")
  private Predicate<Object[]> processInstanceIsCompletedCheck;

  @Autowired
  @Qualifier("incidentIsActiveCheck")
  private Predicate<Object[]> incidentIsActiveCheck;

  @Autowired
  @Qualifier("incidentsInAnyInstanceAreActiveCheck")
  private Predicate<Object[]> incidentsInAnyInstanceAreActiveCheck;

  @Autowired
  @Qualifier("flowNodeIsActiveCheck")
  private Predicate<Object[]> flowNodeIsActiveCheck;

  @Autowired
  @Qualifier("flowNodesAreActiveCheck")
  private Predicate<Object[]> flowNodesAreActiveCheck;

  @Autowired
  @Qualifier("flowNodesExistCheck")
  private Predicate<Object[]> flowNodesExistCheck;

  @Autowired
  @Qualifier("flowNodesInAnyInstanceAreActiveCheck")
  private Predicate<Object[]> flowNodesInAnyInstanceAreActiveCheck;

  @Autowired
  @Qualifier("flowNodeIsCompletedCheck")
  private Predicate<Object[]> flowNodeIsCompletedCheck;

  @Autowired
  @Qualifier("flowNodesAreCompletedCheck")
  private Predicate<Object[]> flowNodesAreCompletedCheck;

  @Autowired
  @Qualifier("flowNodeIsTerminatedCheck")
  private Predicate<Object[]> flowNodeIsTerminatedCheck;

  @Autowired
  @Qualifier("flowNodesAreTerminatedCheck")
  private Predicate<Object[]> flowNodesAreTerminatedCheck;

  @Autowired
  @Qualifier("operationsByProcessInstanceAreCompletedCheck")
  private Predicate<Object[]> operationsByProcessInstanceAreCompletedCheck;

  @Autowired
  @Qualifier("operationsByProcessInstanceAreFailedCheck")
  private Predicate<Object[]> operationsByProcessInstanceAreFailedCheck;

  @Autowired
  @Qualifier("variableExistsCheck")
  private Predicate<Object[]> variableExistsCheck;

  @Autowired
  @Qualifier("variableExistsInCheck")
  private Predicate<Object[]> variableExistsInCheck;

  @Autowired
  @Qualifier("variableHasValue")
  private Predicate<Object[]> variableHasValue;

  @Autowired private OperationReader operationReader;
  @Autowired private BatchOperationWriter batchOperationWriter;

  @Autowired private ObjectMapper objectMapper;

  private final boolean operationExecutorEnabled = true;
  private BatchOperationDto operation;

  public OperateTester(
      final CamundaClient camundaClient,
      final MockMvcTestRule mockMvcTestRule,
      final SearchTestRule searchTestRule) {
    this.camundaClient = camundaClient;
    this.mockMvcTestRule = mockMvcTestRule;
    this.searchTestRule = searchTestRule;
  }

  public OperateTester(
      final CamundaClient camundaClient,
      final MockMvcTestRule mockMvcTestRule,
      final SearchTestRule searchTestRule,
      final JwtDecoder jwtDecoder) {
    this(camundaClient, mockMvcTestRule, searchTestRule);
    this.jwtDecoder = jwtDecoder;
  }

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public Long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public BatchOperationDto getOperation() {
    return operation;
  }

  public List<OperationEntity> getOperations() {
    return operationReader.getOperationsByProcessInstanceKey(processInstanceKey);
  }

  public OperateTester createAndDeploySimpleProcess(
      final String processId, final String activityId) {
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask(activityId)
            .zeebeJobType(activityId)
            .endEvent()
            .done();
    processDefinitionKey =
        ZeebeTestUtil.deployProcess(camundaClient, null, process, processId + ".bpmn");
    return this;
  }

  public OperateTester deployProcess(final String... classpathResources) {
    Validate.notNull(camundaClient, "CamundaClient should be set.");
    LOGGER.debug("Deploy process(es) {}", List.of(classpathResources));
    processDefinitionKey = ZeebeTestUtil.deployProcess(camundaClient, null, classpathResources);
    return this;
  }

  public OperateTester deployDecision(final String... classpathResources) {
    Validate.notNull(camundaClient, "CamundaClient should be set.");
    ZeebeTestUtil.deployDecision(camundaClient, null, classpathResources);
    return this;
  }

  public OperateTester deployProcess(
      final BpmnModelInstance processModel, final String resourceName) {
    processDefinitionKey =
        ZeebeTestUtil.deployProcess(camundaClient, null, processModel, resourceName);
    return this;
  }

  public OperateTester processIsDeployed() {
    searchTestRule.waitFor(processIsDeployedCheck, processDefinitionKey);
    LOGGER.debug("Process is deployed with key: {}", processDefinitionKey);
    return this;
  }

  public OperateTester decisionsAreDeployed(final int count) {
    searchTestRule.waitFor(decisionsAreDeployedCheck, count);
    return this;
  }

  public OperateTester decisionInstancesAreCreated(final int count) {
    searchTestRule.waitFor(decisionInstancesAreCreated, count);
    return this;
  }

  public OperateTester startProcessInstance(final String bpmnProcessId) {
    LOGGER.debug("Start process instance '{}'", bpmnProcessId);
    return startProcessInstance(bpmnProcessId, null);
  }

  public OperateTester startProcessInstance(final String bpmnProcessId, final String payload) {
    return startProcessInstance(bpmnProcessId, null, payload);
  }

  public OperateTester startProcessInstance(
      final String bpmnProcessId, final Integer processVersion, final String payload) {
    return startProcessInstance(bpmnProcessId, processVersion, payload, null);
  }

  public OperateTester startProcessInstance(
      final String bpmnProcessId,
      final Integer processVersion,
      final String payload,
      final String tenantId) {
    LOGGER.debug(
        "Start process instance '{}' version '{}' with payload '{}' and tenant '{}'",
        bpmnProcessId,
        processVersion,
        payload,
        tenantId);
    processInstanceKey =
        ZeebeTestUtil.startProcessInstance(
            false, camundaClient, tenantId, bpmnProcessId, processVersion, payload);
    return this;
  }

  public OperateTester processInstanceIsStarted() {
    searchTestRule.waitFor(processInstancesAreStartedCheck, Arrays.asList(processInstanceKey));
    return this;
  }

  public OperateTester processInstanceIsFinished() {
    searchTestRule.waitFor(processInstancesAreFinishedCheck, Arrays.asList(processInstanceKey));
    return this;
  }

  public OperateTester processInstanceIsCompleted() {
    searchTestRule.waitFor(processInstanceIsCompletedCheck, processInstanceKey);
    return this;
  }

  public OperateTester failTask(final String taskName, final String errorMessage) {
    jobKey =
        ZeebeTestUtil.failTask(
            camundaClient, taskName, UUID.randomUUID().toString(), 3, errorMessage);
    return this;
  }

  public OperateTester resolveIncident() {
    camundaClient.newUpdateRetriesCommand(jobKey).retries(3).send().join();
    camundaClient
        .newResolveIncidentCommand(
            Long.valueOf(
                getIncidents().stream()
                    .filter(i -> i.getJobId().equals(String.valueOf(jobKey)))
                    .findFirst()
                    .get()
                    .getId()))
        .send()
        .join();
    return this;
  }

  public OperateTester resolveIncident(final long jobKey, final long incidentKey) {
    camundaClient.newUpdateRetriesCommand(jobKey).retries(3).send().join();
    camundaClient.newResolveIncidentCommand(incidentKey).send().join();
    return this;
  }

  public OperateTester incidentIsActive() {
    searchTestRule.waitFor(incidentIsActiveCheck, processInstanceKey);
    return this;
  }

  public OperateTester incidentsInAnyInstanceAreActive(final long count) {
    searchTestRule.waitFor(incidentsInAnyInstanceAreActiveCheck, count);
    return this;
  }

  public OperateTester flowNodeIsActive(final String activityId) {
    searchTestRule.waitFor(flowNodeIsActiveCheck, processInstanceKey, activityId);
    LOGGER.debug("FlowNode {} is active.", activityId);
    return this;
  }

  public OperateTester flowNodesAreActive(final String activityId, final int count) {
    searchTestRule.waitFor(flowNodesAreActiveCheck, processInstanceKey, activityId, count);
    LOGGER.debug("{} FlowNodes {} are active.", count, activityId);
    return this;
  }

  public OperateTester flowNodesInAnyInstanceAreActive(final String activityId, final int count) {
    searchTestRule.waitFor(flowNodesInAnyInstanceAreActiveCheck, activityId, count);
    return this;
  }

  public OperateTester flowNodeIsCompleted(final String activityId) {
    searchTestRule.waitFor(flowNodeIsCompletedCheck, processInstanceKey, activityId);
    LOGGER.debug("FlowNode {} is completed.", activityId);
    return this;
  }

  public Long getFlowNodeInstanceKeyFor(final String flowNodeId) {
    return Long.parseLong(
        flowNodeInstanceReader
            .getFlowNodeMetadata(
                "" + processInstanceKey, new FlowNodeMetadataRequestDto().setFlowNodeId(flowNodeId))
            .getFlowNodeInstanceId());
  }

  public Map<String, FlowNodeStateDto> getFlowNodeStates() {
    return flowNodeInstanceReader.getFlowNodeStates("" + processInstanceKey);
  }

  public FlowNodeStateDto getFlowNodeStateFor(final String flowNodeId) {
    return getFlowNodeStates().get(flowNodeId);
  }

  public OperateTester flowNodeIsTerminated(final String activityId) {
    searchTestRule.waitFor(flowNodeIsTerminatedCheck, processInstanceKey, activityId);
    LOGGER.debug("FlowNode {} is terminated.", activityId);
    return this;
  }

  public OperateTester flowNodesAreTerminated(final String activityId, final int count) {
    searchTestRule.waitFor(flowNodesAreTerminatedCheck, processInstanceKey, activityId, count);
    LOGGER.debug("{} FlowNodes {} are active.", count, activityId);
    return this;
  }

  public OperateTester flowNodesAreCanceled(final String activityId, final int count) {
    return flowNodesAreTerminated(activityId, count);
  }

  public OperateTester completeTask(final String activityId, final String jobKey) {
    return completeTask(activityId, jobKey, null);
  }

  public OperateTester completeTask(final String jobKey) {
    return completeTask(jobKey, jobKey, null);
  }

  public OperateTester completeTask(
      final String activityId, final String jobKey, final String payload) {
    ZeebeTestUtil.completeTask(camundaClient, jobKey, TestUtil.createRandomString(10), payload);
    return flowNodeIsCompleted(activityId);
  }

  public OperateTester and() {
    return this;
  }

  public OperateTester waitUntil() {
    return this;
  }

  public OperateTester updateVariableOperation(final String varName, final String varValue)
      throws Exception {
    final CreateOperationRequestDto op =
        new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
    op.setVariableName(varName);
    op.setVariableValue(varValue);
    op.setVariableScopeId(ConversionUtils.toStringOrNull(processInstanceKey));
    scheduleOperation(op);
    searchTestRule.refreshSerchIndexes();
    return this;
  }

  public OperateTester modifyProcessInstanceOperation(
      final List<ModifyProcessInstanceRequestDto.Modification> modifications) throws Exception {
    final ModifyProcessInstanceRequestDto op =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey(processInstanceKey + "")
            .setModifications(modifications);

    operation =
        BatchOperationDto.createFrom(
            batchOperationWriter.scheduleModifyProcessInstance(op), objectMapper);
    searchTestRule.refreshSerchIndexes();
    return this;
  }

  private BatchOperationDto scheduleOperation(final CreateOperationRequestDto operationRequest) {
    operation =
        BatchOperationDto.createFrom(
            batchOperationWriter.scheduleSingleOperation(processInstanceKey, operationRequest),
            objectMapper);
    return operation;
  }

  public OperateTester cancelProcessInstanceOperation() throws Exception {
    scheduleOperation(new CreateOperationRequestDto(OperationType.CANCEL_PROCESS_INSTANCE));
    searchTestRule.refreshSerchIndexes();
    return this;
  }

  public OperateTester deleteProcessInstance() throws Exception {
    scheduleOperation(new CreateOperationRequestDto(OperationType.DELETE_PROCESS_INSTANCE));
    searchTestRule.refreshSerchIndexes();
    return this;
  }

  public OperateTester operationIsCompleted() throws Exception {
    executeOneBatch();
    searchTestRule.waitFor(operationsByProcessInstanceAreCompletedCheck, processInstanceKey);
    return this;
  }

  public OperateTester operationIsFailed() throws Exception {
    executeOneBatch();
    searchTestRule.waitFor(operationsByProcessInstanceAreFailedCheck, processInstanceKey);
    return this;
  }

  private int executeOneBatch() throws Exception {
    if (!operationExecutorEnabled) {
      return 0;
    }
    final List<Future<?>> futures = operationExecutor.executeOneBatch();
    // wait till all scheduled tasks are executed
    for (final Future f : futures) {
      f.get();
    }
    return 0; // return futures.size()
  }

  public OperateTester then() {
    return this;
  }

  public OperateTester executeOperations() throws Exception {
    executeOneBatch();
    searchTestRule.refreshOperateSearchIndices();
    return this;
  }

  public OperateTester variableExists(final String name) {
    searchTestRule.waitFor(variableExistsCheck, processInstanceKey, name);
    return this;
  }

  public OperateTester variableExistsIn(final String name, final Long scopeKey) {
    searchTestRule.waitFor(variableExistsInCheck, processInstanceKey, name, scopeKey);
    return this;
  }

  public OperateTester variableHasValue(final String name, final Object value) {
    searchTestRule.waitFor(variableHasValue, processInstanceKey, name, value, processInstanceKey);
    return this;
  }

  public OperateTester variableHasValue(
      final String name, final Object value, final Long scopeKey) {
    searchTestRule.waitFor(variableHasValue, processInstanceKey, name, value, scopeKey);
    return this;
  }

  public OperateTester conditionIsMet(
      final Predicate<Object[]> elsCheck, final Object... arguments) {
    searchTestRule.waitFor(elsCheck, arguments);
    return this;
  }

  public String getVariable(final String name) {
    return getVariable(name, processInstanceKey);
  }

  public String getVariable(final String name, final Long scopeKey) {
    final var variable =
        variableReader.getVariableByName(
            String.valueOf(processInstanceKey), String.valueOf(scopeKey), name);
    if (variable == null) {
      return null;
    }
    return variable.getValue();
  }

  public boolean hasVariable(final String name, final String value) {
    final String variableValue = getVariable(name);
    return value == null ? (variableValue == null) : value.contains(variableValue);
  }

  public List<IncidentDto> getIncidents() {
    return getIncidentsFor(processInstanceKey);
  }

  public List<IncidentDto> getIncidentsFor(final Long processInstanceKey) {
    return incidentReader
        .getIncidentsByProcessInstanceId(String.valueOf(processInstanceKey))
        .getIncidents();
  }

  public List<FlowNodeInstanceEntity> getAllFlowNodeInstances() {
    return getAllFlowNodeInstances(processInstanceKey);
  }

  public List<FlowNodeInstanceEntity> getAllFlowNodeInstances(final Long processInstanceKey) {
    return flowNodeInstanceReader.getAllFlowNodeInstances(processInstanceKey);
  }

  public FlowNodeInstanceEntity getFlowNodeInstanceEntityFor(final Long flowNodeInstanceKey) {
    return getAllFlowNodeInstances(processInstanceKey).stream()
        .filter(i -> i.getKey() == flowNodeInstanceKey)
        .findFirst()
        .orElseThrow();
  }

  public List<FlowNodeInstanceDto> getFlowNodeInstanceOneListFromRest(
      final String processInstanceId) throws Exception {
    return getFlowNodeInstanceOneListFromRest(
        new FlowNodeInstanceQueryDto(processInstanceId, processInstanceId));
  }

  public List<FlowNodeInstanceDto> getFlowNodeInstanceOneListFromRest(
      final FlowNodeInstanceQueryDto query) throws Exception {
    final FlowNodeInstanceRequestDto request = new FlowNodeInstanceRequestDto(query);
    final Map<String, FlowNodeInstanceResponseDto> response =
        flowNodeInstanceReader.getFlowNodeInstances(request);
    assertThat(response).hasSize(1);
    return response.values().iterator().next().getChildren();
  }

  public FlowNodeMetadataDto getFlowNodeMetadataFromRest(
      final String processInstanceId,
      final String flowNodeId,
      final FlowNodeType flowNodeType,
      final String flowNodeInstanceId)
      throws Exception {
    return flowNodeInstanceReader.getFlowNodeMetadata(
        processInstanceId,
        new FlowNodeMetadataRequestDto()
            .setFlowNodeId(flowNodeId)
            .setFlowNodeType(flowNodeType)
            .setFlowNodeInstanceId(flowNodeInstanceId));
  }

  public OperateTester sendMessages(
      final String messageName,
      final String correlationKey,
      final String payload,
      final int count) {
    ZeebeTestUtil.sendMessages(camundaClient, messageName, payload, count, correlationKey);
    return this;
  }

  public List<Long> getFlowNodeInstanceKeysFor(final String flowNodeId) {
    return flowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
        processInstanceKey,
        flowNodeId,
        List.of(FlowNodeState.ACTIVE, FlowNodeState.COMPLETED, FlowNodeState.TERMINATED));
  }
}
