/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.camunda.operate.webapp.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.test.ClientRule;
import io.zeebe.test.EmbeddedBrokerRule;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import org.apache.http.HttpStatus;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import org.camunda.operate.webapp.rest.dto.operation.CreateOperationRequestDto;
import org.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import org.camunda.operate.zeebe.PartitionHolder;
import org.camunda.operate.zeebeimport.ImportPositionHolder;
import org.camunda.operate.zeebeimport.cache.WorkflowCache;
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

  protected static final String POST_OPERATION_URL = WORKFLOW_INSTANCE_URL + "/%s/operation";
  private static final String POST_BATCH_OPERATION_URL = WORKFLOW_INSTANCE_URL + "/batch-operation";

  @MockBean
  protected ZeebeClient mockedZeebeClient;    //we don't want to create ZeebeClient, we will rather use the one from test rule

  protected ZeebeClient zeebeClient;

  @Autowired
  public BeanFactory beanFactory;

  @Rule
  public final OperateZeebeRule zeebeRule;

  protected ClientRule clientRule;

  protected EmbeddedBrokerRule brokerRule;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule();

  @Autowired
  protected PartitionHolder partitionHolder;

  @Autowired
  protected ImportPositionHolder importPositionHolder;

  @Autowired
  protected WorkflowCache workflowCache;

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
  @Qualifier("workflowIsDeployedCheck")
  protected Predicate<Object[]> workflowIsDeployedCheck;

  @Autowired
  @Qualifier("incidentsAreActiveCheck")
  protected Predicate<Object[]> incidentsAreActiveCheck;

  @Autowired
  @Qualifier("incidentIsActiveCheck")
  protected Predicate<Object[]> incidentIsActiveCheck;

  @Autowired
  @Qualifier("workflowInstanceIsCreatedCheck")
  protected Predicate<Object[]> workflowInstanceIsCreatedCheck;

  @Autowired
  @Qualifier("workflowInstancesAreStartedCheck")
  protected Predicate<Object[]> workflowInstancesAreStartedCheck;

  @Autowired
  @Qualifier("workflowInstanceIsCompletedCheck")
  protected Predicate<Object[]> workflowInstanceIsCompletedCheck;

  @Autowired
  @Qualifier("workflowInstancesAreFinishedCheck")
  protected Predicate<Object[]> workflowInstancesAreFinishedCheck;

  @Autowired
  @Qualifier("workflowInstanceIsCanceledCheck")
  protected Predicate<Object[]> workflowInstanceIsCanceledCheck;

  @Autowired
  @Qualifier("activityIsTerminatedCheck")
  protected Predicate<Object[]> activityIsTerminatedCheck;

  @Autowired
  @Qualifier("activityIsCompletedCheck")
  protected Predicate<Object[]> activityIsCompletedCheck;

  @Autowired
  @Qualifier("activityIsActiveCheck")
  protected Predicate<Object[]> activityIsActiveCheck;

  @Autowired
  @Qualifier("operationsByWorkflowInstanceAreCompletedCheck")
  protected Predicate<Object[]> operationsByWorkflowInstanceAreCompleted;

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
    clientRule = zeebeRule.getClientRule();
    assertThat(clientRule).as("clientRule is not null").isNotNull();
    brokerRule = zeebeRule.getBrokerRule();
    assertThat(brokerRule).as("brokerRule is not null").isNotNull();

    zeebeClient = getClient();
    workerName = TestUtil.createRandomString(10);

    tester = beanFactory.getBean(OperateTester.class, zeebeClient, mockMvcTestRule, elasticsearchTestRule);

    workflowCache.clearCache();
    importPositionHolder.clearCache();
    partitionHolder.setZeebeClient(getClient());

  }

  @After
  public void after() {
    workflowCache.clearCache();
    importPositionHolder.clearCache();
  }

  public OperateZeebeIntegrationTest() {
    zeebeRule = new OperateZeebeRule();
  }

  public ZeebeClient getClient() {
    return clientRule.getClient();
  }

  public BrokerCfg getBrokerCfg() {
    return brokerRule.getBrokerCfg();
  }

  public String getWorkerName() {
    return workerName;
  }

  public Long failTaskWithNoRetriesLeft(String taskName, long workflowInstanceKey, String errorMessage) {
    Long jobKey = ZeebeTestUtil.failTask(getClient(), taskName, getWorkerName(), 3, errorMessage);
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);
    return jobKey;
  }

  protected Long deployWorkflow(String... classpathResources) {
    final Long workflowKey = ZeebeTestUtil.deployWorkflow(getClient(), classpathResources);
    elasticsearchTestRule.processAllRecordsAndWait(workflowIsDeployedCheck, workflowKey);
    return workflowKey;
  }

  protected Long deployWorkflow(BpmnModelInstance workflow, String resourceName) {
    final Long workflowId = ZeebeTestUtil.deployWorkflow(getClient(), workflow, resourceName);
    elasticsearchTestRule.processAllRecordsAndWait(workflowIsDeployedCheck, workflowId);
    return workflowId;
  }

  protected void cancelWorkflowInstance(long workflowInstanceKey) {
    cancelWorkflowInstance(workflowInstanceKey, true);
  }

  protected void cancelWorkflowInstance(long workflowInstanceKey, boolean waitForData) {
    ZeebeTestUtil.cancelWorkflowInstance(getClient(), workflowInstanceKey);
    if (waitForData) {
      elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCanceledCheck, workflowInstanceKey);
    }
  }

  protected void completeTask(long workflowInstanceKey, String activityId, String payload) {
    completeTask(workflowInstanceKey, activityId, payload, true);
  }

  protected void completeTask(long workflowInstanceKey, String activityId, String payload, boolean waitForData) {
    ZeebeTestUtil.completeTask(getClient(), activityId, getWorkerName(), payload);
    if (waitForData) {
      elasticsearchTestRule.processAllRecordsAndWait(activityIsCompletedCheck, workflowInstanceKey, activityId);
    }
  }

  protected void postUpdateVariableOperation(Long workflowInstanceKey, String newVarName, String newVarValue) throws Exception {
    final CreateOperationRequestDto op = new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
    op.setVariableName(newVarName);
    op.setVariableValue(newVarValue);
    op.setVariableScopeId(ConversionUtils.toStringOrNull(workflowInstanceKey));
    postOperationWithOKResponse(workflowInstanceKey, op);
  }

  protected void postUpdateVariableOperation(Long workflowInstanceKey, Long scopeKey, String newVarName, String newVarValue) throws Exception {
    final CreateOperationRequestDto op = new CreateOperationRequestDto(OperationType.UPDATE_VARIABLE);
    op.setVariableName(newVarName);
    op.setVariableValue(newVarValue);
    op.setVariableScopeId(ConversionUtils.toStringOrNull(scopeKey));
    postOperationWithOKResponse(workflowInstanceKey, op);
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

  protected MvcResult postOperationWithOKResponse(Long workflowInstanceKey, CreateOperationRequestDto operationRequest) throws Exception {
    return postOperation(workflowInstanceKey, operationRequest, HttpStatus.SC_OK);
  }

  protected MvcResult postOperation(Long workflowInstanceKey, CreateOperationRequestDto operationRequest, int expectedStatus) throws Exception {
    MockHttpServletRequestBuilder postOperationRequest =
      post(String.format(POST_OPERATION_URL, workflowInstanceKey))
        .content(mockMvcTestRule.json(operationRequest))
        .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
      mockMvc.perform(postOperationRequest)
        .andExpect(status().is(expectedStatus))
        .andReturn();
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    return mvcResult;
  }

  protected long startDemoWorkflowInstance() {
    String processId = "demoProcess";
    final Long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(getClient(), processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.processAllRecordsAndWait(activityIsActiveCheck, workflowInstanceKey, "taskA");
    //elasticsearchTestRule.refreshIndexesInElasticsearch();
    return workflowInstanceKey;
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
}
