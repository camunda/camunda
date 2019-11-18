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

import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Predicate;

import org.apache.http.HttpStatus;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import org.camunda.operate.webapp.rest.dto.operation.OperationRequestDto;
import org.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import org.camunda.operate.zeebeimport.ImportPositionHolder;
import org.camunda.operate.zeebe.PartitionHolder;
import org.camunda.operate.zeebeimport.cache.WorkflowCache;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.test.ClientRule;
import io.zeebe.test.EmbeddedBrokerRule;

public abstract class OperateZeebeIntegrationTest extends OperateIntegrationTest {
  
  private static final String POST_OPERATION_URL = WORKFLOW_INSTANCE_URL + "/%s/operation";
  private static final String POST_BATCH_OPERATION_URL = WORKFLOW_INSTANCE_URL + "/operation";

  @MockBean
  protected ZeebeClient mockedZeebeClient;    //we don't want to create ZeebeClient, we will rather use the one from test rule
  
  protected ZeebeClient zeebeClient;

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
    try {
      FieldSetter.setField(partitionHolder, PartitionHolder.class.getDeclaredField("zeebeClient"), getClient());
    } catch (NoSuchFieldException e) {
      fail("Failed to inject ZeebeClient into some of the beans");
    }
  }

  @After
  public void after() {
    workflowCache.clearCache();
    importPositionHolder.clearCache();
  }

  public OperateZeebeIntegrationTest() {
    this(EmbeddedBrokerRule.DEFAULT_CONFIG_FILE);
  }

  public OperateZeebeIntegrationTest(final String configFileClasspathLocation) {
    zeebeRule = new OperateZeebeRule(configFileClasspathLocation);
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
    final OperationRequestDto op = new OperationRequestDto(OperationType.UPDATE_VARIABLE);
    op.setName(newVarName);
    op.setValue(newVarValue);
    op.setScopeId(ConversionUtils.toStringOrNull(workflowInstanceKey));
    postOperationWithOKResponse(workflowInstanceKey, op);
  }

  protected void postUpdateVariableOperation(Long workflowInstanceKey, Long scopeKey, String newVarName, String newVarValue) throws Exception {
    final OperationRequestDto op = new OperationRequestDto(OperationType.UPDATE_VARIABLE);
    op.setName(newVarName);
    op.setValue(newVarValue);
    op.setScopeId(ConversionUtils.toStringOrNull(scopeKey));
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
  
  protected MvcResult postOperationWithOKResponse(Long workflowInstanceKey, OperationRequestDto operationRequest) throws Exception {
    return postOperation(workflowInstanceKey, operationRequest, HttpStatus.SC_OK);
  }

  protected MvcResult postOperation(Long workflowInstanceKey, OperationRequestDto operationRequest, int expectedStatus) throws Exception {
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
    return postBatchOperation(query, operationType, HttpStatus.SC_OK);
  }

  protected MvcResult postBatchOperation(ListViewQueryDto query, OperationType operationType, int expectedStatus) throws Exception {
    BatchOperationRequestDto batchOperationDto = createBatchOperationDto(operationType, query);
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

  protected BatchOperationRequestDto createBatchOperationDto(OperationType operationType, ListViewQueryDto query) {
    BatchOperationRequestDto batchOperationDto = new BatchOperationRequestDto();
    batchOperationDto.getQueries().add(query);
    batchOperationDto.setOperationType(operationType);
    return batchOperationDto;
  }

  protected void clearMetrics() {
    for (Meter meter: meterRegistry.getMeters()) {
      meterRegistry.remove(meter);
    }
  }
}
