/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static io.camunda.operate.webapp.rest.ProcessInstanceRestService.PROCESS_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.zeebeimport.post.IncidentPostImportAction;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
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
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.BatchOperationDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import io.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportPositionHolder;
import io.camunda.operate.cache.ProcessCache;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

public abstract class OperateZeebeIntegrationTest extends OperateIntegrationTest {

  protected static final String POST_OPERATION_URL = PROCESS_INSTANCE_URL + "/%s/operation";
  private static final String POST_BATCH_OPERATION_URL = PROCESS_INSTANCE_URL + "/batch-operation";

  @MockBean
  protected ZeebeClient mockedZeebeClient;    //we don't want to create ZeebeClient, we will rather use the one from test rule

  protected ZeebeClient zeebeClient;

  @Autowired
  public BeanFactory beanFactory;

  @Rule
  public final OperateZeebeRule zeebeRule;

  protected ZeebeContainer zeebeContainer;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  protected PartitionHolder partitionHolder;

  @Autowired
  protected ImportPositionHolder importPositionHolder;

  @Autowired
  protected ProcessCache processCache;

  @Autowired
  protected ObjectMapper objectMapper;

  private HttpClient httpClient = HttpClient.newHttpClient();

  /// Predicate checks
  @Autowired
  @Qualifier("incidentIsResolvedCheck")
  protected Predicate<Object[]> incidentIsResolvedCheck;

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
  protected OperateProperties operateProperties;

  private String workerName;

  @Autowired
  protected OperationExecutor operationExecutor;

  @Autowired
  private MeterRegistry meterRegistry;

  protected OperateTester tester;

  @Before
  public void before() {
    super.before();

    zeebeContainer = zeebeRule.getZeebeContainer();
    assertThat(zeebeContainer).as("zeebeContainer is not null").isNotNull();

    zeebeClient = getClient();
    workerName = TestUtil.createRandomString(10);

    tester = beanFactory.getBean(OperateTester.class, zeebeClient, mockMvcTestRule, elasticsearchTestRule);

    processCache.clearCache();
    importPositionHolder.cancelScheduledImportPositionUpdateTask().join();
    importPositionHolder.clearCache();
    importPositionHolder.scheduleImportPositionUpdateTask();
    partitionHolder.setZeebeClient(getClient());
  }

  @After
  public void after() {
    processCache.clearCache();
    importPositionHolder.cancelScheduledImportPositionUpdateTask().join();
    importPositionHolder.clearCache();
  }

  public OperateZeebeIntegrationTest() {
    zeebeRule = new OperateZeebeRule();
  }

  public ZeebeClient getClient() {
    return zeebeRule.getClient();
  }

  public String getWorkerName() {
    return workerName;
  }

  public Long failTaskWithNoRetriesLeft(String taskName, long processInstanceKey, String errorMessage) {
    Long jobKey = ZeebeTestUtil.failTask(getClient(), taskName, getWorkerName(), 3, errorMessage);
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, processInstanceKey);
    return jobKey;
  }

  protected Long deployProcess(String... classpathResources) {
    final Long processDefinitionKey = ZeebeTestUtil.deployProcess(getClient(), classpathResources);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processDefinitionKey);
    return processDefinitionKey;
  }

  protected Long deployProcess(BpmnModelInstance process, String resourceName) {
    final Long processId = ZeebeTestUtil.deployProcess(getClient(), process, resourceName);
    elasticsearchTestRule.processAllRecordsAndWait(processIsDeployedCheck, processId);
    return processId;
  }

  protected void cancelProcessInstance(long processInstanceKey) {
    cancelProcessInstance(processInstanceKey, true);
  }

  protected void cancelProcessInstance(long processInstanceKey, boolean waitForData) {
    ZeebeTestUtil.cancelProcessInstance(getClient(), processInstanceKey);
    if (waitForData) {
      elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);
    }
  }

  protected void completeTask(long processInstanceKey, String activityId, String payload) {
    completeTask(processInstanceKey, activityId, payload, true);
  }

  protected void completeTask(long processInstanceKey, String activityId, String payload, boolean waitForData) {
    ZeebeTestUtil.completeTask(getClient(), activityId, getWorkerName(), payload);
    if (waitForData) {
      elasticsearchTestRule.processAllRecordsAndWait(flowNodeIsCompletedCheck, processInstanceKey, activityId);
    }
  }

  protected void postUpdateVariableOperation(Long processInstanceKey, String newVarName, String newVarValue) throws Exception {
    final CreateOperationRequestDto op = new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
    op.setVariableName(newVarName);
    op.setVariableValue(newVarValue);
    op.setVariableScopeId(ConversionUtils.toStringOrNull(processInstanceKey));
    postOperationWithOKResponse(processInstanceKey, op);
  }

  protected String postAddVariableOperation(Long processInstanceKey, String newVarName, String newVarValue) throws Exception {
    final CreateOperationRequestDto op = new CreateOperationRequestDto(OperationType.ADD_VARIABLE);
    op.setVariableName(newVarName);
    op.setVariableValue(newVarValue);
    op.setVariableScopeId(ConversionUtils.toStringOrNull(processInstanceKey));
    final MvcResult mvcResult = postOperationWithOKResponse(processInstanceKey, op);
    final BatchOperationDto batchOperationDto = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), BatchOperationDto.class);
    return batchOperationDto.getId();
  }

  protected void postUpdateVariableOperation(Long processInstanceKey, Long scopeKey, String newVarName, String newVarValue) throws Exception {
    final CreateOperationRequestDto op = new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
    op.setVariableName(newVarName);
    op.setVariableValue(newVarValue);
    op.setVariableScopeId(ConversionUtils.toStringOrNull(scopeKey));
    postOperationWithOKResponse(processInstanceKey, op);
  }

  protected String postAddVariableOperation(Long processInstanceKey, Long scopeKey, String newVarName, String newVarValue) throws Exception {
    final CreateOperationRequestDto op = new CreateOperationRequestDto(OperationType.ADD_VARIABLE);
    op.setVariableName(newVarName);
    op.setVariableValue(newVarValue);
    op.setVariableScopeId(ConversionUtils.toStringOrNull(scopeKey));
    final MvcResult mvcResult = postOperationWithOKResponse(processInstanceKey, op);
    final BatchOperationDto batchOperationDto = objectMapper
        .readValue(mvcResult.getResponse().getContentAsString(), BatchOperationDto.class);
    return batchOperationDto.getId();
  }

  protected void executeOneBatch() {
    try {
      List<Future<?>> futures = operationExecutor.executeOneBatch();
      //wait till all scheduled tasks are executed
      for(Future f: futures) { f.get(); }
    } catch (Exception e) {
      fail(e.getMessage(), e);
    }
  }

  protected MvcResult postOperationWithOKResponse(Long processInstanceKey, CreateOperationRequestDto operationRequest) throws Exception {
    return postOperation(processInstanceKey, operationRequest, HttpStatus.SC_OK);
  }

  protected MvcResult postOperation(Long processInstanceKey, CreateOperationRequestDto operationRequest, int expectedStatus) throws Exception {
    MockHttpServletRequestBuilder postOperationRequest =
      post(String.format(POST_OPERATION_URL, processInstanceKey))
        .content(mockMvcTestRule.json(operationRequest))
        .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
      mockMvc.perform(postOperationRequest)
        .andExpect(status().is(expectedStatus))
        .andReturn();
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    return mvcResult;
  }

  protected MvcResult postBatchOperationWithOKResponse(ListViewQueryDto query, OperationType operationType) throws Exception {
    return postBatchOperationWithOKResponse(query, operationType, null);
  }

  protected MvcResult postBatchOperationWithOKResponse(ListViewQueryDto query, OperationType operationType, String name) throws Exception {
    return postBatchOperation(query, operationType, name, HttpStatus.SC_OK);
  }

  protected MvcResult postBatchOperation(ListViewQueryDto query, OperationType operationType, String name, int expectedStatus) throws Exception {
    CreateBatchOperationRequestDto batchOperationDto = createBatchOperationDto(operationType, name, query);
    MockHttpServletRequestBuilder postOperationRequest =
      post(POST_BATCH_OPERATION_URL)
        .content(mockMvcTestRule.json(batchOperationDto))
        .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
      mockMvc.perform(postOperationRequest)
        .andExpect(status().is(expectedStatus))
        .andReturn();
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    return mvcResult;
  }

  protected CreateBatchOperationRequestDto createBatchOperationDto(OperationType operationType, String name, ListViewQueryDto query) {
    CreateBatchOperationRequestDto batchOperationDto = new CreateBatchOperationRequestDto();
    batchOperationDto.setQuery(query);
    batchOperationDto.setOperationType(operationType);
    if (name != null) {
      batchOperationDto.setName(name);
    }
    return batchOperationDto;
  }

  protected void clearMetrics() {
    for (Meter meter: meterRegistry.getMeters()) {
      meterRegistry.remove(meter);
    }
  }

  protected Instant pinZeebeTime() {
      return pinZeebeTime(Instant.now());
  }

  protected Instant pinZeebeTime(Instant pinAt) {
      final var pinRequest = new ZeebeClockActuatorPinRequest(pinAt.toEpochMilli());
      try {
          final var body = HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(pinRequest));
          return zeebeRequest("POST", "actuator/clock/pin", body);
      } catch (IOException | InterruptedException e) {
          throw new IllegalStateException("Could not pin zeebe clock", e);
      }
  }

  protected Instant offsetZeebeTime(Duration offsetBy) {
      final var offsetRequest = new ZeebeClockActuatorOffsetRequest(offsetBy.toMillis());
      try {
          final var body = HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(offsetRequest));
          return zeebeRequest("POST", "actuator/clock/pin", body);
      } catch (IOException | InterruptedException e) {
          throw new IllegalStateException("Could not offset zeebe clock", e);
      }
  }

  protected Instant resetZeebeTime() {
      try {
          return zeebeRequest("DELETE", "actuator/clock", HttpRequest.BodyPublishers.noBody());
      } catch (IOException | InterruptedException e) {
          throw new IllegalStateException("Could not reset zeebe clock", e);
      }
  }

  private Instant zeebeRequest(String method, String endpoint, HttpRequest.BodyPublisher bodyPublisher) throws IOException, InterruptedException {
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
      final var result = objectMapper.readValue(httpResponse.body(),ZeebeClockActuatorResponse.class);

      return Instant.ofEpochMilli(result.epochMilli);
  }

  private final static class ZeebeClockActuatorPinRequest {
      @JsonProperty
      long epochMilli;
      ZeebeClockActuatorPinRequest(long epochMilli) {
          this.epochMilli = epochMilli;
      }
  }

  private final static class ZeebeClockActuatorOffsetRequest {
      @JsonProperty
      long epochMilli;

      public ZeebeClockActuatorOffsetRequest(long offsetMilli) {
          this.epochMilli = offsetMilli;
      }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private final static class ZeebeClockActuatorResponse {
    @JsonProperty
    long epochMilli;
  }

  protected List<Long> deployProcesses(String... processResources) {
    return Stream.of(processResources).sequential()
        .map(resource ->
            tester
                .deployProcess(resource)
                .and()
                .waitUntil()
                .processIsDeployed()
                .getProcessDefinitionKey()
        ).collect(Collectors.toList());
  }

  protected List<Long> startProcesses(String... bpmnProcessIds) {
     return Stream.of(bpmnProcessIds).sequential()
        .map(bpmnProcessId ->
            tester.startProcessInstance(bpmnProcessId)
                .and()
                .waitUntil()
                .processInstanceExists()
                .getProcessInstanceKey())
        .collect(Collectors.toList());
  }
}
