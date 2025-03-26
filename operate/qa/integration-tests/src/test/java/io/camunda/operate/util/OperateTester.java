/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static io.camunda.operate.qa.util.RestAPITestUtil.*;
import static io.camunda.operate.util.CollectionUtil.filter;
import static io.camunda.operate.webapp.rest.FlowNodeInstanceRestService.FLOW_NODE_INSTANCE_URL;
import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.reader.IncidentReader;
import io.camunda.operate.webapp.reader.ListViewReader;
import io.camunda.operate.webapp.reader.OperationReader;
import io.camunda.operate.webapp.rest.dto.OperationDto;
import io.camunda.operate.webapp.rest.dto.VariableDto;
import io.camunda.operate.webapp.rest.dto.VariableRequestDto;
import io.camunda.operate.webapp.rest.dto.activity.*;
import io.camunda.operate.webapp.rest.dto.incidents.IncidentDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewProcessInstanceDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewRequestDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewResponseDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto;
import io.camunda.operate.webapp.security.oauth2.IdentityJwt2AuthenticationTokenConverter;
import io.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import io.camunda.operate.zeebeimport.ZeebeImporter;
import io.camunda.webapps.schema.descriptors.operate.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.entities.event.EventType;
import io.camunda.webapps.schema.entities.operate.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.operate.FlowNodeState;
import io.camunda.webapps.schema.entities.operate.FlowNodeType;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.Validate;
import org.apache.http.HttpStatus;
import org.awaitility.Awaitility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@Component
@Scope(SCOPE_PROTOTYPE)
public class OperateTester {

  protected static final Logger LOGGER = LoggerFactory.getLogger(OperateTester.class);
  @Autowired protected OperationExecutor operationExecutor;
  @Autowired protected io.camunda.operate.webapp.reader.VariableReader variableReader;
  @Autowired protected IncidentReader incidentReader;
  @Autowired protected ListViewReader listViewReader;
  @Autowired protected FlowNodeInstanceReader flowNodeInstanceReader;

  @Autowired(required = false)
  protected ZeebeImporter zeebeImporter;

  @Autowired private BeanFactory beanFactory;
  private final CamundaClient camundaClient;
  private final MockMvcTestRule mockMvcTestRule;
  private final SearchTestRule searchTestRule;
  private Long processDefinitionKey;
  private Long processInstanceKey;
  private Long jobKey;
  private JwtDecoder jwtDecoder;

  @Autowired(required = false)
  private IdentityJwt2AuthenticationTokenConverter jwtAuthenticationConverter;

  @Autowired(required = false)
  @Qualifier("importThreadPoolExecutor")
  private ThreadPoolTaskExecutor importExecutor;

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
  @Qualifier("processInstanceExistsCheck")
  private Predicate<Object[]> processInstanceExistsCheck;

  @Autowired
  @Qualifier("processInstancesAreFinishedCheck")
  private Predicate<Object[]> processInstancesAreFinishedCheck;

  @Autowired
  @Qualifier("processInstanceIsCompletedCheck")
  private Predicate<Object[]> processInstanceIsCompletedCheck;

  @Autowired
  @Qualifier("processInstanceIsCanceledCheck")
  private Predicate<Object[]> processInstanceIsCanceledCheck;

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
  @Qualifier("eventIsImportedCheck")
  private Predicate<Object[]> eventIsImportedCheck;

  @Autowired
  @Qualifier("eventIsImportedForFlowNodeCheck")
  private Predicate<Object[]> eventIsImportedForFlowNodeCheck;

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

  @Autowired
  @Qualifier("userTasksAreCreated")
  private Predicate<Object[]> userTasksAreCreated;

  @Autowired
  @Qualifier("operateFlowNodeInstanceTemplate")
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired private OperationReader operationReader;

  @Autowired private ObjectMapper objectMapper;

  private boolean operationExecutorEnabled = true;
  private BatchOperationDto operation;
  private List<String> processDefinitions;

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

  public List<OperationDto> getOperations() {
    return operationReader.getOperationsByBatchOperationId(operation.getId());
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
    searchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey);
    LOGGER.debug("Process is deployed with key: {}", processDefinitionKey);
    return this;
  }

  public OperateTester decisionsAreDeployed(final int count) {
    searchTestRule.processAllRecordsAndWait(decisionsAreDeployedCheck, count);
    return this;
  }

  public OperateTester decisionInstancesAreCreated(final int count) {
    searchTestRule.processAllRecordsAndWait(decisionInstancesAreCreated, count);
    return this;
  }

  public OperateTester userTasksAreCreated(final int count) {
    searchTestRule.processAllRecordsAndWait(userTasksAreCreated, count);
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

  public OperateTester startProcessInstanceWithVariables(
      final String bpmnProcessId, final Map<String, String> nameValuePairs) {
    try {
      processInstanceKey =
          ZeebeTestUtil.startProcessInstance(
              camundaClient, bpmnProcessId, objectMapper.writeValueAsString(nameValuePairs));
    } catch (final JsonProcessingException e) {
      throw new OperateRuntimeException(e);
    }
    return this;
  }

  public OperateTester processInstanceIsStarted() {
    searchTestRule.processAllRecordsAndWait(
        processInstancesAreStartedCheck, Arrays.asList(processInstanceKey));
    return this;
  }

  public OperateTester processInstanceExists() {
    searchTestRule.processAllRecordsAndWait(
        processInstanceExistsCheck, Arrays.asList(processInstanceKey));
    return this;
  }

  public OperateTester processInstanceIsFinished() {
    searchTestRule.processAllRecordsAndWait(
        processInstancesAreFinishedCheck, Arrays.asList(processInstanceKey));
    return this;
  }

  public OperateTester processInstanceIsCompleted() {
    searchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, processInstanceKey);
    return this;
  }

  public OperateTester processInstanceIsCanceled() {
    searchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);
    return this;
  }

  public OperateTester failTask(final String taskName, final String errorMessage) {
    jobKey =
        ZeebeTestUtil.failTask(
            camundaClient, taskName, UUID.randomUUID().toString(), 3, errorMessage);
    return this;
  }

  public OperateTester throwError(
      final String taskName, final String errorCode, final String errorMessage) {
    ZeebeTestUtil.throwErrorInTask(
        camundaClient, taskName, UUID.randomUUID().toString(), 1, errorCode, errorMessage);
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
    searchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);
    return this;
  }

  public OperateTester incidentsInAnyInstanceAreActive(final long count) {
    searchTestRule.processAllRecordsAndWait(incidentsInAnyInstanceAreActiveCheck, count);
    return this;
  }

  public OperateTester flowNodeIsActive(final String activityId) {
    searchTestRule.processAllRecordsAndWait(flowNodeIsActiveCheck, processInstanceKey, activityId);
    LOGGER.debug("FlowNode {} is active.", activityId);
    return this;
  }

  public OperateTester eventIsImported(final String jobType) {
    searchTestRule.processAllRecordsAndWait(eventIsImportedCheck, processInstanceKey, jobType);
    return this;
  }

  public OperateTester eventIsImportedForFlowNode(
      final String flowNodeId, final EventType eventType) {
    searchTestRule.processAllRecordsAndWait(
        eventIsImportedForFlowNodeCheck, processInstanceKey, flowNodeId, eventType);
    return this;
  }

  public OperateTester flowNodesAreActive(final String activityId, final int count) {
    searchTestRule.processAllRecordsAndWait(
        flowNodesAreActiveCheck, processInstanceKey, activityId, count);
    LOGGER.debug("{} FlowNodes {} are active.", count, activityId);
    return this;
  }

  public OperateTester flowNodesExist(final String activityId, final int count) {
    searchTestRule.processAllRecordsAndWait(
        flowNodesExistCheck, processInstanceKey, activityId, count);
    LOGGER.debug("{} FlowNodes {} exist.", count, activityId);
    return this;
  }

  public OperateTester flowNodesInAnyInstanceAreActive(final String activityId, final int count) {
    searchTestRule.processAllRecordsAndWait(
        flowNodesInAnyInstanceAreActiveCheck, activityId, count);
    return this;
  }

  public OperateTester flowNodeIsCompleted(final String activityId) {
    searchTestRule.processAllRecordsAndWait(
        flowNodeIsCompletedCheck, processInstanceKey, activityId);
    LOGGER.debug("FlowNode {} is completed.", activityId);
    return this;
  }

  public OperateTester flowNodesAreCompleted(final String activityId, final int count) {
    searchTestRule.processAllRecordsAndWait(
        flowNodesAreCompletedCheck, processInstanceKey, activityId, count);
    LOGGER.debug("{} FlowNodes {} is completed.", count, activityId);
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
    searchTestRule.processAllRecordsAndWait(
        flowNodeIsTerminatedCheck, processInstanceKey, activityId);
    LOGGER.debug("FlowNode {} is terminated.", activityId);
    return this;
  }

  public OperateTester flowNodeIsCanceled(final String activityId) {
    return flowNodeIsTerminated(activityId);
  }

  public OperateTester flowNodesAreTerminated(final String activityId, final int count) {
    searchTestRule.processAllRecordsAndWait(
        flowNodesAreTerminatedCheck, processInstanceKey, activityId, count);
    LOGGER.debug("{} FlowNodes {} are active.", count, activityId);
    return this;
  }

  public OperateTester flowNodesAreCanceled(final String activityId, final int count) {
    return flowNodesAreTerminated(activityId, count);
  }

  public OperateTester activateJob(final String type) {
    camundaClient.newActivateJobsCommand().jobType(type).maxJobsToActivate(1).send();
    return this;
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
    postOperation(op);
    searchTestRule.refreshSerchIndexes();
    return this;
  }

  public OperateTester modifyProcessInstanceOperation(
      final List<ModifyProcessInstanceRequestDto.Modification> modifications) throws Exception {
    final ModifyProcessInstanceRequestDto op =
        new ModifyProcessInstanceRequestDto()
            .setProcessInstanceKey(processInstanceKey + "")
            .setModifications(modifications);

    postOperation(op);
    searchTestRule.refreshSerchIndexes();
    return this;
  }

  private MvcResult postOperation(final CreateOperationRequestDto operationRequest)
      throws Exception {
    final MockHttpServletRequestBuilder postOperationRequest =
        post(format("/api/process-instances/%s/operation", processInstanceKey))
            .content(mockMvcTestRule.json(operationRequest))
            .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
        mockMvcTestRule
            .getMockMvc()
            .perform(postOperationRequest)
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn();
    operation = mockMvcTestRule.fromResponse(mvcResult, BatchOperationDto.class);
    return mvcResult;
  }

  private MvcResult postOperation(final ModifyProcessInstanceRequestDto operationRequest)
      throws Exception {
    final MockHttpServletRequestBuilder ope =
        post(format(PROCESS_INSTANCE_URL + "/%s/modify", processInstanceKey))
            .content(mockMvcTestRule.json(operationRequest))
            .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
        mockMvcTestRule
            .getMockMvc()
            .perform(ope)
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn();
    operation = mockMvcTestRule.fromResponse(mvcResult, BatchOperationDto.class);
    return mvcResult;
  }

  public OperateTester cancelProcessInstanceOperation() throws Exception {
    final ListViewQueryDto processInstanceQuery =
        createGetAllProcessInstancesQuery()
            .setIds(Collections.singletonList(processInstanceKey.toString()));

    final CreateBatchOperationRequestDto batchOperationDto =
        new CreateBatchOperationRequestDto(
            processInstanceQuery, OperationType.CANCEL_PROCESS_INSTANCE);

    postOperation(batchOperationDto);
    searchTestRule.refreshSerchIndexes();
    return this;
  }

  public OperateTester deleteProcessInstance() throws Exception {
    postOperation(new CreateOperationRequestDto(OperationType.DELETE_PROCESS_INSTANCE));
    searchTestRule.refreshSerchIndexes();
    return this;
  }

  public OperateTester activateFlowNode(
      final String flowNodeId, final Long ancestorElementInstanceKey) {
    camundaClient
        .newModifyProcessInstanceCommand(processInstanceKey)
        .activateElement(flowNodeId, ancestorElementInstanceKey)
        .send()
        .join();
    return this;
  }

  public OperateTester cancelAllFlowNodesFor(final String flowNodeId) {
    getAllFlowNodeInstances(processInstanceKey).stream()
        .filter(flowNode -> flowNode.getFlowNodeId().equals(flowNodeId))
        .map(flowNode -> flowNode.getKey())
        .forEach(
            key ->
                camundaClient
                    .newModifyProcessInstanceCommand(processInstanceKey)
                    .terminateElement(key)
                    .send()
                    .join());
    return this;
  }

  public OperateTester cancelFlowNodeInstance(final Long flowNodeInstanceId) {
    camundaClient
        .newModifyProcessInstanceCommand(processInstanceKey)
        .terminateElement(flowNodeInstanceId)
        .send()
        .join();
    return this;
  }

  public OperateTester operationIsCompleted() throws Exception {
    executeOneBatch();
    searchTestRule.processAllRecordsAndWait(
        operationsByProcessInstanceAreCompletedCheck, processInstanceKey);
    return this;
  }

  public OperateTester operationIsFailed() throws Exception {
    executeOneBatch();
    searchTestRule.processAllRecordsAndWait(
        operationsByProcessInstanceAreFailedCheck, processInstanceKey);
    return this;
  }

  private MvcResult postOperation(final CreateBatchOperationRequestDto operationRequest)
      throws Exception {
    final MockHttpServletRequestBuilder postOperationRequest =
        post(format("/api/process-instances/%s/operation", processInstanceKey))
            .content(mockMvcTestRule.json(operationRequest))
            .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
        mockMvcTestRule
            .getMockMvc()
            .perform(postOperationRequest)
            .andExpect(status().is(HttpStatus.SC_OK))
            .andReturn();
    operation = mockMvcTestRule.fromResponse(mvcResult, BatchOperationDto.class);
    return mvcResult;
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

  public OperateTester disableOperationExecutor() {
    operationExecutorEnabled = false;
    return this;
  }

  public OperateTester enableOperationExecutor() throws Exception {
    operationExecutorEnabled = true;
    return executeOperations();
  }

  public OperateTester executeOperations() throws Exception {
    executeOneBatch();
    searchTestRule.refreshOperateSearchIndices();
    return this;
  }

  public OperateTester archiveIsDone() {
    searchTestRule.refreshOperateSearchIndices();
    return this;
  }

  public OperateTester variableExists(final String name) {
    searchTestRule.processAllRecordsAndWait(variableExistsCheck, processInstanceKey, name);
    return this;
  }

  public OperateTester variableExistsIn(final String name, final Long scopeKey) {
    searchTestRule.processAllRecordsAndWait(
        variableExistsInCheck, processInstanceKey, name, scopeKey);
    return this;
  }

  public OperateTester variableHasValue(final String name, final Object value) {
    searchTestRule.processAllRecordsAndWait(
        variableHasValue, processInstanceKey, name, value, processInstanceKey);
    return this;
  }

  public OperateTester variableHasValue(
      final String name, final Object value, final Long scopeKey) {
    searchTestRule.processAllRecordsAndWait(
        variableHasValue, processInstanceKey, name, value, scopeKey);
    return this;
  }

  public OperateTester conditionIsMet(
      final Predicate<Object[]> elsCheck, final Object... arguments) {
    searchTestRule.processAllRecordsAndWait(elsCheck, arguments);
    return this;
  }

  public String getVariable(final String name) {
    return getVariable(name, processInstanceKey);
  }

  public String getVariable(final String name, final Long scopeKey) {
    final List<VariableDto> variables = getVariables(processInstanceKey, scopeKey);
    final List<VariableDto> variablesWithGivenName =
        filter(variables, variable -> variable.getName().equals(name));
    if (variablesWithGivenName.isEmpty()) {
      return null;
    }
    return variablesWithGivenName.get(0).getValue();
  }

  private List<VariableDto> getVariables(final Long processInstanceKey, final Long scopeKey) {
    return variableReader.getVariables(
        String.valueOf(processInstanceKey),
        new VariableRequestDto().setScopeId(String.valueOf(scopeKey)));
  }

  public List<VariableDto> getVariablesForScope(final Long scopeKey) {
    return getVariables(processInstanceKey, scopeKey);
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

  public boolean hasIncidentWithErrorMessage(final String errorMessage) {
    return !filter(getIncidents(), incident -> incident.getErrorMessage().equals(errorMessage))
        .isEmpty();
  }

  public List<FlowNodeInstanceDto> getFlowNodeInstanceOneListFromRest(
      final String processInstanceId) throws Exception {
    return getFlowNodeInstanceOneListFromRest(
        new FlowNodeInstanceQueryDto(processInstanceId, processInstanceId));
  }

  public List<FlowNodeInstanceDto> getFlowNodeInstanceOneListFromRest(
      final FlowNodeInstanceQueryDto query) throws Exception {
    final FlowNodeInstanceRequestDto request = new FlowNodeInstanceRequestDto(query);
    final MvcResult mvcResult = postRequest(FLOW_NODE_INSTANCE_URL, request);
    final Map<String, FlowNodeInstanceResponseDto> response =
        mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
    assertThat(response).hasSize(1);
    return response.values().iterator().next().getChildren();
  }

  public FlowNodeMetadataDto getFlowNodeMetadataFromRest(
      final String processInstanceId,
      final String flowNodeId,
      final FlowNodeType flowNodeType,
      final String flowNodeInstanceId)
      throws Exception {
    final FlowNodeMetadataRequestDto request =
        new FlowNodeMetadataRequestDto()
            .setFlowNodeId(flowNodeId)
            .setFlowNodeType(flowNodeType)
            .setFlowNodeInstanceId(flowNodeInstanceId);
    final MvcResult mvcResult =
        postRequest(
            format(PROCESS_INSTANCE_URL + "/%s/flow-node-metadata", processInstanceId), request);
    return mockMvcTestRule.fromResponse(mvcResult, new TypeReference<>() {});
  }

  public ListViewProcessInstanceDto getSingleProcessInstanceByBpmnProcessId(
      final String processId) {
    final ListViewRequestDto request =
        createGetAllProcessInstancesRequest(q -> q.setProcessIds(Arrays.asList(processId)));
    request.setPageSize(100);
    final ListViewResponseDto listViewResponse = listViewReader.queryProcessInstances(request);
    assertThat(listViewResponse.getTotalCount()).isEqualTo(1);
    assertThat(listViewResponse.getProcessInstances()).hasSize(1);
    return listViewResponse.getProcessInstances().get(0);
  }

  public List<ListViewProcessInstanceDto> getProcessInstanceByIds(final List<Long> ids) {
    final ListViewRequestDto request =
        new ListViewRequestDto(createGetProcessInstancesByIdsQuery(ids));
    request.setPageSize(100);
    final ListViewResponseDto listViewResponse = listViewReader.queryProcessInstances(request);
    return listViewResponse.getProcessInstances();
  }

  public void cancelFlowNodeByInstanceKey(
      final Long processInstanceKey, final Long flowNodeInstanceKey) {
    camundaClient
        .newModifyProcessInstanceCommand(processInstanceKey)
        .terminateElement(flowNodeInstanceKey)
        .send()
        .join();
  }

  public OperateTester activateFlowNode(final String flowNodeId) {
    camundaClient
        .newModifyProcessInstanceCommand(processInstanceKey)
        .activateElement(flowNodeId)
        .send()
        .join();
    return this;
  }

  public void moveFlowNodeFromTo(
      final Long sourceFlowNodeInstanceKey, final String targetFlowNodeId) {
    camundaClient
        .newModifyProcessInstanceCommand(processInstanceKey)
        .activateElement(targetFlowNodeId)
        .and()
        .terminateElement(sourceFlowNodeInstanceKey)
        .send()
        .join();
  }

  public void moveFlowNodeFromTo(
      final Long sourceFlowNodeInstanceKey,
      final String targetFlowNodeId,
      final Long ancestorElementInstanceKey) {
    camundaClient
        .newModifyProcessInstanceCommand(processInstanceKey)
        .activateElement(targetFlowNodeId, ancestorElementInstanceKey)
        .and()
        .terminateElement(sourceFlowNodeInstanceKey)
        .send()
        .join();
  }

  public OperateTester sendMessages(
      final String messageName,
      final String correlationKey,
      final String payload,
      final int count) {
    ZeebeTestUtil.sendMessages(camundaClient, messageName, payload, count, correlationKey);
    return this;
  }

  private MvcResult postRequest(final String requestUrl, final Object query) throws Exception {
    final MockHttpServletRequestBuilder request =
        post(requestUrl)
            .content(mockMvcTestRule.json(query))
            .contentType(mockMvcTestRule.getContentType());

    return mockMvcTestRule
        .getMockMvc()
        .perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(mockMvcTestRule.getContentType()))
        .andReturn();
  }

  public String getItemsPayloadFor(final int size) {
    return "{\"items\": ["
        + IntStream.range(0, size).boxed().map(Object::toString).collect(Collectors.joining(","))
        + "]}";
  }

  public List<Long> getFlowNodeInstanceKeysFor(final String flowNodeId) {
    return flowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
        processInstanceKey,
        flowNodeId,
        List.of(FlowNodeState.ACTIVE, FlowNodeState.COMPLETED, FlowNodeState.TERMINATED));
  }

  public void waitIndexDeletion(final String index, final int maxWaitMillis) throws IOException {
    final var start = System.currentTimeMillis();
    var deleted = false;

    assertTrue(format("Index %s doesn't exist!", index), searchTestRule.indexExists(index));
    LOGGER.info(format("Index exists %s", index));

    while (!deleted & System.currentTimeMillis() < start + maxWaitMillis) {
      deleted = !searchTestRule.indexExists(index);
      LOGGER.info(
          format(
              "Index %s is deleted after %s seconds: %s. Expectation is 1000 seconds",
              index, (System.currentTimeMillis() - start) / 1000, deleted));
      try {
        Thread.sleep(10000);
      } catch (final Exception e) {
        throw new RuntimeException(e);
      }
    }

    assertTrue(format("Index %s was not deleted after %s ms!", index, maxWaitMillis), deleted);
  }

  public OperateTester withAuthenticationToken(final String token) {
    final Jwt jwt;
    try {
      jwt = jwtDecoder.decode(token);
    } catch (final JwtException e) {
      throw new RuntimeException(e);
    }
    SecurityContextHolder.getContext().setAuthentication(jwtAuthenticationConverter.convert(jwt));
    return this;
  }

  public void performOneRoundOfImport() {
    zeebeImporter.performOneRoundOfImport();
    Awaitility.await("Waiting for import threads to finish")
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(() -> assertThat(importExecutor.getActiveCount()).isEqualTo(0));
  }
}
