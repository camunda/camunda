/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.DecisionRestService;
import io.camunda.operate.webapp.rest.ProcessRestService;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import io.camunda.operate.webapp.zeebe.operation.adapter.ClientBasedAdapter;
import io.camunda.operate.webapp.zeebe.operation.adapter.OperateServicesAdapter;
import io.camunda.operate.webapp.zeebe.operation.process.modify.AddTokenHandler;
import io.camunda.operate.webapp.zeebe.operation.process.modify.CancelTokenHandler;
import io.camunda.operate.webapp.zeebe.operation.process.modify.ModifyProcessZeebeWrapper;
import io.camunda.operate.webapp.zeebe.operation.process.modify.MoveTokenHandler;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.webapps.zeebe.StandalonePartitionSupplier;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public abstract class OperateZeebeAbstractIT extends OperateAbstractIT {

  protected static final String POST_OPERATION_URL = PROCESS_INSTANCE_URL + "/%s/operation";
  @Rule public final OperateZeebeRule zeebeRule;

  // test rule
  @Autowired public BeanFactory beanFactory;
  @Rule public SearchTestRule searchTestRule = new SearchTestRule();

  protected CamundaClient camundaClient;
  protected OperateServicesAdapter operateServicesAdapter;
  @Autowired protected FlowNodeInstanceReader flowNodeInstanceReader;
  @Autowired protected PartitionHolder partitionHolder;
  @Autowired protected ImportPositionHolder importPositionHolder;
  @Autowired protected ProcessCache processCache;
  @Autowired protected ObjectMapper objectMapper;

  /// Predicate checks
  @Autowired
  @Qualifier("noActivitiesHaveIncident")
  protected Predicate<Object[]> noActivitiesHaveIncident;

  @Autowired
  @Qualifier("variableExistsCheck")
  protected Predicate<Object[]> variableExistsCheck;

  @Autowired
  @Qualifier("variableEqualsCheck")
  protected Predicate<Object[]> variableEqualsCheck;

  @Autowired
  @Qualifier("processIsDeployedCheck")
  protected Predicate<Object[]> processIsDeployedCheck;

  @Autowired
  @Qualifier("incidentsAreActiveCheck")
  protected Predicate<Object[]> incidentsAreActiveCheck;

  @Autowired
  @Qualifier("incidentsArePresentCheck")
  protected Predicate<Object[]> incidentsArePresentCheck;

  @Autowired
  @Qualifier("incidentWithErrorMessageIsActiveCheck")
  protected Predicate<Object[]> incidentWithErrorMessageIsActiveCheck;

  @Autowired
  @Qualifier("incidentIsActiveCheck")
  protected Predicate<Object[]> incidentIsActiveCheck;

  @Autowired
  @Qualifier("jobWithRetriesCheck")
  protected Predicate<Object[]> jobWithRetriesCheck;

  @Autowired
  @Qualifier("processInstanceIsCreatedCheck")
  protected Predicate<Object[]> processInstanceIsCreatedCheck;

  @Autowired
  @Qualifier("incidentsInAnyInstanceArePresentCheck")
  protected Predicate<Object[]> incidentsInAnyInstanceArePresentCheck;

  @Autowired
  @Qualifier("processInstanceIsCompletedCheck")
  protected Predicate<Object[]> processInstanceIsCompletedCheck;

  @Autowired
  @Qualifier("processInstancesAreFinishedCheck")
  protected Predicate<Object[]> processInstancesAreFinishedCheck;

  @Autowired
  @Qualifier("processInstanceIsCanceledCheck")
  protected Predicate<Object[]> processInstanceIsCanceledCheck;

  @Autowired
  @Qualifier("flowNodeIsTerminatedCheck")
  protected Predicate<Object[]> flowNodeIsTerminatedCheck;

  @Autowired
  @Qualifier("flowNodeIsCompletedCheck")
  protected Predicate<Object[]> flowNodeIsCompletedCheck;

  @Autowired
  @Qualifier("flowNodeIsInIncidentStateCheck")
  protected Predicate<Object[]> flowNodeIsInIncidentStateCheck;

  @Autowired
  @Qualifier("flowNodesAreCompletedCheck")
  protected Predicate<Object[]> flowNodesAreCompletedCheck;

  @Autowired
  @Qualifier("flowNodeIsActiveCheck")
  protected Predicate<Object[]> flowNodeIsActiveCheck;

  @Autowired
  @Qualifier("operationsByProcessInstanceAreCompletedCheck")
  protected Predicate<Object[]> operationsByProcessInstanceAreCompleted;

  @Autowired
  @Qualifier("processInstancesAreStartedByProcessIdCheck")
  protected Predicate<Object[]> processInstancesAreStartedByProcessId;

  @Autowired
  @Qualifier("listenerJobIsCreated")
  protected Predicate<Object[]> listenerJobIsCreated;

  @Autowired protected OperateProperties operateProperties;
  @Autowired protected OperationExecutor operationExecutor;
  protected OperateTester tester;
  private TestStandaloneBroker zeebeBroker;
  @Autowired private TestSearchRepository testSearchRepository;
  private String workerName;
  @Autowired private MeterRegistry meterRegistry;

  public OperateZeebeAbstractIT() {
    zeebeRule = new OperateZeebeRule();
  }

  @Override
  @Before
  public void before() {
    super.before();

    zeebeBroker = zeebeRule.getZeebeBroker();
    assertThat(zeebeBroker).as("zeebeContainer is not null").isNotNull();

    camundaClient = getClient();
    operateServicesAdapter =
        new ClientBasedAdapter(
            camundaClient,
            new ModifyProcessZeebeWrapper(
                camundaClient,
                new AddTokenHandler(),
                new CancelTokenHandler(flowNodeInstanceReader),
                new MoveTokenHandler(flowNodeInstanceReader)));
    workerName = TestUtil.createRandomString(10);

    tester =
        beanFactory.getBean(OperateTester.class, camundaClient, mockMvcTestRule, searchTestRule);

    processCache.clearCache();
    importPositionHolder.cancelScheduledImportPositionUpdateTask().join();
    importPositionHolder.clearCache();
    importPositionHolder.scheduleImportPositionUpdateTask();
    final var partitionSupplier = new StandalonePartitionSupplier(getClient());
    partitionHolder.setPartitionSupplier(partitionSupplier);
  }

  @After
  public void after() {
    processCache.clearCache();
    importPositionHolder.cancelScheduledImportPositionUpdateTask().join();
    importPositionHolder.clearCache();
  }

  public CamundaClient getClient() {
    return zeebeRule.getClient();
  }

  public String getWorkerName() {
    return workerName;
  }

  public Long failTaskWithNoRetriesLeft(
      final String taskName,
      final long processInstanceKey,
      final int numberOfFailure,
      final String errorMessage) {
    final Long jobKey =
        ZeebeTestUtil.failTask(
            getClient(), taskName, getWorkerName(), numberOfFailure, errorMessage);
    searchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);
    return jobKey;
  }

  public Long failTaskWithNoRetriesLeft(
      final String taskName, final long processInstanceKey, final String errorMessage) {
    final Long jobKey =
        ZeebeTestUtil.failTask(getClient(), taskName, getWorkerName(), 3, errorMessage);
    searchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);
    return jobKey;
  }

  public Long failTaskWithRetriesLeft(
      final String taskName, final long processInstanceKey, final String errorMessage) {
    final Long jobKey =
        ZeebeTestUtil.failTaskWithRetriesLeft(
            getClient(), taskName, getWorkerName(), 1, errorMessage);
    searchTestRule.processAllRecordsAndWait(jobWithRetriesCheck, processInstanceKey, jobKey, 1);
    return jobKey;
  }

  protected Long deployProcess(final String... classpathResources) {
    final Long processDefinitionKey =
        ZeebeTestUtil.deployProcess(getClient(), null, classpathResources);
    searchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey);
    return processDefinitionKey;
  }

  protected Long deployProcess(final BpmnModelInstance process, final String resourceName) {
    final Long processId = ZeebeTestUtil.deployProcess(getClient(), null, process, resourceName);
    searchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId);
    return processId;
  }

  protected void cancelProcessInstance(final long processInstanceKey) {
    cancelProcessInstance(processInstanceKey, true);
  }

  protected void cancelProcessInstance(final long processInstanceKey, final boolean waitForData) {
    ZeebeTestUtil.cancelProcessInstance(getClient(), processInstanceKey);
    if (waitForData) {
      searchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);
    }
  }

  protected void completeTask(
      final long processInstanceKey, final String activityId, final String payload) {
    completeTask(processInstanceKey, activityId, payload, true);
  }

  protected void completeTask(
      final long processInstanceKey,
      final String activityId,
      final String payload,
      final boolean waitForData) {
    ZeebeTestUtil.completeTask(getClient(), activityId, getWorkerName(), payload);
    if (waitForData) {
      searchTestRule.processAllRecordsAndWait(
          flowNodeIsCompletedCheck, processInstanceKey, activityId);
    }
  }

  protected void postUpdateVariableOperation(
      final Long processInstanceKey, final String newVarName, final String newVarValue)
      throws Exception {
    final CreateOperationRequestDto op =
        new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
    op.setVariableName(newVarName);
    op.setVariableValue(newVarValue);
    op.setVariableScopeId(ConversionUtils.toStringOrNull(processInstanceKey));
    postOperationWithOKResponse(processInstanceKey, op);
  }

  protected String postAddVariableOperation(
      final Long processInstanceKey, final String newVarName, final String newVarValue)
      throws Exception {
    final CreateOperationRequestDto op = new CreateOperationRequestDto(OperationType.ADD_VARIABLE);
    op.setVariableName(newVarName);
    op.setVariableValue(newVarValue);
    op.setVariableScopeId(ConversionUtils.toStringOrNull(processInstanceKey));
    final MvcResult mvcResult = postOperationWithOKResponse(processInstanceKey, op);
    final BatchOperationDto batchOperationDto =
        objectMapper.readValue(
            mvcResult.getResponse().getContentAsString(), BatchOperationDto.class);
    return batchOperationDto.getId();
  }

  protected void postUpdateVariableOperation(
      final Long processInstanceKey,
      final Long scopeKey,
      final String newVarName,
      final String newVarValue)
      throws Exception {
    final CreateOperationRequestDto op =
        new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
    op.setVariableName(newVarName);
    op.setVariableValue(newVarValue);
    op.setVariableScopeId(ConversionUtils.toStringOrNull(scopeKey));
    postOperationWithOKResponse(processInstanceKey, op);
  }

  protected String postAddVariableOperation(
      final Long processInstanceKey,
      final Long scopeKey,
      final String newVarName,
      final String newVarValue)
      throws Exception {
    final CreateOperationRequestDto op = new CreateOperationRequestDto(OperationType.ADD_VARIABLE);
    op.setVariableName(newVarName);
    op.setVariableValue(newVarValue);
    op.setVariableScopeId(ConversionUtils.toStringOrNull(scopeKey));
    final MvcResult mvcResult = postOperationWithOKResponse(processInstanceKey, op);
    final BatchOperationDto batchOperationDto =
        objectMapper.readValue(
            mvcResult.getResponse().getContentAsString(), BatchOperationDto.class);
    return batchOperationDto.getId();
  }

  protected void executeOneBatch() {
    try {
      final List<Future<?>> futures = operationExecutor.executeOneBatch();
      // wait till all scheduled tasks are executed
      for (final Future f : futures) {
        f.get();
      }
    } catch (final Exception e) {
      fail(e.getMessage(), e);
    }
  }

  protected MvcResult postOperationWithOKResponse(
      final Long processInstanceKey, final CreateOperationRequestDto operationRequest)
      throws Exception {
    return postOperation(processInstanceKey, operationRequest, HttpStatus.SC_OK);
  }

  protected MvcResult postOperation(
      final Long processInstanceKey,
      final CreateOperationRequestDto operationRequest,
      final int expectedStatus)
      throws Exception {
    final MockHttpServletRequestBuilder postOperationRequest =
        post(String.format(POST_OPERATION_URL, processInstanceKey))
            .content(mockMvcTestRule.json(operationRequest))
            .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
        mockMvc.perform(postOperationRequest).andExpect(status().is(expectedStatus)).andReturn();
    searchTestRule.refreshSerchIndexes();
    return mvcResult;
  }

  protected BatchOperationEntity deleteProcessWithOkResponse(final String processId)
      throws Exception {
    final String requestUrl = ProcessRestService.PROCESS_URL + "/" + processId;
    final MockHttpServletRequestBuilder request =
        delete(requestUrl).accept(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
        mockMvc.perform(request).andExpect(status().is(HttpStatus.SC_OK)).andReturn();
    searchTestRule.refreshSerchIndexes();

    final BatchOperationEntity batchOperation =
        objectMapper.readValue(
            mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);
    return batchOperation;
  }

  protected BatchOperationEntity deleteDecisionWithOkResponse(final String decisionDefinitionId)
      throws Exception {
    final String requestUrl = DecisionRestService.DECISION_URL + "/" + decisionDefinitionId;
    final MockHttpServletRequestBuilder request =
        delete(requestUrl).accept(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
        mockMvc.perform(request).andExpect(status().is(HttpStatus.SC_OK)).andReturn();
    searchTestRule.refreshSerchIndexes();

    final BatchOperationEntity batchOperation =
        objectMapper.readValue(
            mvcResult.getResponse().getContentAsString(), BatchOperationEntity.class);
    return batchOperation;
  }

  protected void clearMetrics() {
    for (final Meter meter : meterRegistry.getMeters()) {
      meterRegistry.remove(meter);
    }
  }

  protected <R> List<R> searchAllDocuments(final String index, final Class<R> clazz) {
    try {
      return testSearchRepository.searchAll(index, clazz);
    } catch (final IOException ex) {
      throw new OperateRuntimeException("Search failed for index " + index, ex);
    }
  }
}
