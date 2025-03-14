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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.operate.cache.ProcessCache;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import io.camunda.operate.webapp.rest.DecisionRestService;
import io.camunda.operate.webapp.rest.ProcessRestService;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.webapps.schema.entities.operation.BatchOperationEntity;
import io.camunda.webapps.schema.entities.operation.OperationType;
import io.camunda.webapps.zeebe.StandalonePartitionSupplier;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.zeebe.containers.ZeebeContainer;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.http.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public abstract class OperateZeebeAbstractIT extends OperateAbstractIT {

  protected static final String POST_OPERATION_URL = PROCESS_INSTANCE_URL + "/%s/operation";
  private static final String POST_BATCH_OPERATION_URL = PROCESS_INSTANCE_URL + "/batch-operation";
  @Rule public final OperateZeebeRule zeebeRule;

  // test rule
  @Autowired public BeanFactory beanFactory;
  @Rule public SearchTestRule searchTestRule = new SearchTestRule();

  // we don't want to create CamundaClient, we will rather use the one from
  @MockBean protected CamundaClient mockedCamundaClient;

  protected CamundaClient camundaClient;
  @Autowired protected PartitionHolder partitionHolder;
  @Autowired protected ImportPositionHolder importPositionHolder;
  @Autowired protected ProcessCache processCache;
  @Autowired protected ObjectMapper objectMapper;

  /// Predicate checks
  @Autowired
  @Qualifier("noActivitiesHaveIncident")
  protected Predicate<Object[]> noActivitiesHaveIncident;

  @Autowired
  @Qualifier("incidentsAreResolved")
  protected Predicate<Object[]> incidentsAreResolved;

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
  @Qualifier("postImporterQueueCountCheck")
  protected Predicate<Object[]> postImporterQueueCountCheck;

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
  @Qualifier("incidentsInAnyInstanceAreActiveCheck")
  protected Predicate<Object[]> incidentsInAnyInstanceAreActiveCheck;

  @Autowired
  @Qualifier("processInstanceIsCreatedCheck")
  protected Predicate<Object[]> processInstanceIsCreatedCheck;

  @Autowired
  @Qualifier("incidentsInAnyInstanceArePresentCheck")
  protected Predicate<Object[]> incidentsInAnyInstanceArePresentCheck;

  @Autowired
  @Qualifier("processInstancesAreStartedCheck")
  protected Predicate<Object[]> processInstancesAreStartedCheck;

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

  @Autowired
  @Qualifier("userTasksAreCreated")
  protected Predicate<Object[]> userTasksAreCreated;

  @Autowired protected OperateProperties operateProperties;
  @Autowired protected OperationExecutor operationExecutor;
  protected OperateTester tester;
  private ZeebeContainer zeebeContainer;
  @Autowired private TestSearchRepository testSearchRepository;
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private String workerName;
  @Autowired private MeterRegistry meterRegistry;

  public OperateZeebeAbstractIT() {
    zeebeRule = new OperateZeebeRule();
  }

  @Override
  @Before
  public void before() {
    super.before();

    zeebeContainer = zeebeRule.getZeebeContainer();
    assertThat(zeebeContainer).as("zeebeContainer is not null").isNotNull();

    camundaClient = getClient();
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

  protected Long deployProcessWithTenant(
      final String tenantId, final String... classpathResources) {
    final Long processDefinitionKey =
        ZeebeTestUtil.deployProcess(getClient(), tenantId, classpathResources);
    searchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey);
    return processDefinitionKey;
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

  protected MvcResult postBatchOperationWithOKResponse(
      final ListViewQueryDto query, final OperationType operationType) throws Exception {
    return postBatchOperationWithOKResponse(query, operationType, null);
  }

  protected MvcResult postBatchOperationWithOKResponse(
      final ListViewQueryDto query, final OperationType operationType, final String name)
      throws Exception {
    return postBatchOperation(query, operationType, name, HttpStatus.SC_OK);
  }

  protected MvcResult postBatchOperation(
      final CreateBatchOperationRequestDto batchOperationDto, final int expectedStatus)
      throws Exception {
    final MockHttpServletRequestBuilder postOperationRequest =
        post(POST_BATCH_OPERATION_URL)
            .content(mockMvcTestRule.json(batchOperationDto))
            .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
        mockMvc.perform(postOperationRequest).andExpect(status().is(expectedStatus)).andReturn();
    searchTestRule.refreshSerchIndexes();
    return mvcResult;
  }

  protected MvcResult postBatchOperation(
      final ListViewQueryDto query,
      final OperationType operationType,
      final String name,
      final int expectedStatus)
      throws Exception {
    final CreateBatchOperationRequestDto batchOperationDto =
        createBatchOperationDto(operationType, name, query);
    return postBatchOperation(batchOperationDto, expectedStatus);
  }

  protected CreateBatchOperationRequestDto createBatchOperationDto(
      final OperationType operationType, final String name, final ListViewQueryDto query) {
    final CreateBatchOperationRequestDto batchOperationDto = new CreateBatchOperationRequestDto();
    batchOperationDto.setQuery(query);
    batchOperationDto.setOperationType(operationType);
    if (name != null) {
      batchOperationDto.setName(name);
    }
    return batchOperationDto;
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

  protected Instant pinZeebeTime() {
    return pinZeebeTime(Instant.now());
  }

  protected Instant pinZeebeTime(final Instant pinAt) {
    final var pinRequest = new ZeebeClockActuatorPinRequest(pinAt.toEpochMilli());
    try {
      final var body =
          HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(pinRequest));
      return zeebeRequest("POST", "actuator/clock/pin", body);
    } catch (final IOException | InterruptedException e) {
      throw new IllegalStateException("Could not pin zeebe clock", e);
    }
  }

  protected Instant offsetZeebeTime(final Duration offsetBy) {
    final var offsetRequest = new ZeebeClockActuatorOffsetRequest(offsetBy.toMillis());
    try {
      final var body =
          HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(offsetRequest));
      return zeebeRequest("POST", "actuator/clock/pin", body);
    } catch (final IOException | InterruptedException e) {
      throw new IllegalStateException("Could not offset zeebe clock", e);
    }
  }

  protected Instant resetZeebeTime() {
    try {
      return zeebeRequest("DELETE", "actuator/clock", HttpRequest.BodyPublishers.noBody());
    } catch (final IOException | InterruptedException e) {
      throw new IllegalStateException("Could not reset zeebe clock", e);
    }
  }

  private Instant zeebeRequest(
      final String method, final String endpoint, final HttpRequest.BodyPublisher bodyPublisher)
      throws IOException, InterruptedException {
    final var fullEndpoint =
        URI.create(
            String.format("http://%s/%s", zeebeContainer.getExternalAddress(9600), endpoint));
    final var httpRequest =
        HttpRequest.newBuilder(fullEndpoint)
            .method(method, bodyPublisher)
            .header("Content-Type", "application/json")
            .build();
    final var httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
    if (httpResponse.statusCode() != 200) {
      throw new IllegalStateException("Pinning time failed: " + httpResponse.body());
    }
    final var result =
        objectMapper.readValue(httpResponse.body(), ZeebeClockActuatorResponse.class);

    return Instant.ofEpochMilli(result.epochMilli);
  }

  protected List<Long> deployProcesses(final String... processResources) {
    return Stream.of(processResources)
        .sequential()
        .map(
            resource ->
                tester
                    .deployProcess(resource)
                    .and()
                    .waitUntil()
                    .processIsDeployed()
                    .getProcessDefinitionKey())
        .collect(Collectors.toList());
  }

  protected List<Long> startProcesses(final String... bpmnProcessIds) {
    return Stream.of(bpmnProcessIds)
        .sequential()
        .map(
            bpmnProcessId ->
                tester
                    .startProcessInstance(bpmnProcessId)
                    .and()
                    .waitUntil()
                    .processInstanceExists()
                    .getProcessInstanceKey())
        .collect(Collectors.toList());
  }

  protected <R> List<R> searchAllDocuments(final String index, final Class<R> clazz) {
    try {
      return testSearchRepository.searchAll(index, clazz);
    } catch (final IOException ex) {
      throw new OperateRuntimeException("Search failed for index " + index, ex);
    }
  }

  private static final class ZeebeClockActuatorPinRequest {
    @JsonProperty long epochMilli;

    ZeebeClockActuatorPinRequest(final long epochMilli) {
      this.epochMilli = epochMilli;
    }
  }

  private static final class ZeebeClockActuatorOffsetRequest {
    @JsonProperty long epochMilli;

    public ZeebeClockActuatorOffsetRequest(final long offsetMilli) {
      epochMilli = offsetMilli;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private static final class ZeebeClockActuatorResponse {
    @JsonProperty long epochMilli;
  }
}
