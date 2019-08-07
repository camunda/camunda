/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.Predicate;

import org.apache.commons.lang3.Validate;
import org.apache.http.HttpStatus;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.exceptions.OperateRuntimeException;
import org.camunda.operate.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.rest.dto.operation.BatchOperationRequestDto;
import org.camunda.operate.rest.dto.operation.OperationRequestDto;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.zeebe.operation.OperationExecutor;
import org.camunda.operate.zeebeimport.ZeebeImporter;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.IndexNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.BpmnModelInstance;

@Component
public class OperateTester {
  
  Logger logger = LoggerFactory.getLogger(getClass());
  
  @Autowired
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @Autowired
  private ZeebeClient zeebeClient;
  @Autowired
  private ZeebeImporter zeebeImporter;
  private Long workflowKey;
  private Long workflowInstanceKey;
  
  @Autowired
  @Qualifier("workflowIsDeployedCheck")
  private Predicate<Object[]> workflowIsDeployedCheck;
  
  @Autowired
  @Qualifier("workflowInstancesAreStartedCheck")
  private Predicate<Object[]> workflowInstancesAreStartedCheck;
  
  @Autowired
  @Qualifier("workflowInstancesAreFinishedCheck")
  private Predicate<Object[]> workflowInstancesAreFinishedCheck;
  
  @Autowired
  @Qualifier("workflowInstanceIsCompletedCheck")
  private Predicate<Object[]> workflowInstanceIsCompletedCheck;
  
  @Autowired
  @Qualifier("incidentIsActiveCheck")
  private Predicate<Object[]> incidentIsActiveCheck;
  
  @Autowired
  @Qualifier("operationsByWorkflowInstanceAreCompletedCheck")
  private Predicate<Object[]> operationsByWorkflowInstanceAreCompletedCheck;
  
  int waitingRound = 1;

  private Long jobKey;

  private MockMvcTestRule mockMvcTestRule;
  
  @Autowired
  protected OperationExecutor operationExecutor;
  
  public Long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  public OperateTester setMockMvcTestRule(MockMvcTestRule mockMvcTestRule) {
    this.mockMvcTestRule = mockMvcTestRule;
    return this;
  }

  public OperateTester setZeebeClient(ZeebeClient zeebeClient) {
    this.zeebeClient = zeebeClient;
    return this;
  }

  public OperateTester deployWorkflow(String... classpathResources) {
    Validate.notNull(zeebeClient, "ZeebeClient should be set.");
    workflowKey = ZeebeTestUtil.deployWorkflow(zeebeClient, classpathResources);
    return this;
  }
  
  public OperateTester deployWorkflow(BpmnModelInstance workflowModel, String resourceName) {
    workflowKey = ZeebeTestUtil.deployWorkflow(zeebeClient, workflowModel, resourceName);
    return this; 
  }
  
  public OperateTester workflowIsDeployed() {
    processAllRecordsAndWait(workflowIsDeployedCheck, workflowKey);
    return this;
  }

  public OperateTester startWorkflowInstance(String bpmnProcessId) {
   return startWorkflowInstance(bpmnProcessId, null);
  }
  
  public OperateTester startWorkflowInstance(String bpmnProcessId, String payload) {
    workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, bpmnProcessId, payload);
    return this;
  }
  
  public OperateTester workflowInstanceIsStarted() {
    processAllRecordsAndWait(workflowInstancesAreStartedCheck, Arrays.asList(workflowInstanceKey));
    return this;
  }
  
  public OperateTester workflowInstanceIsFinished() {
    processAllRecordsAndWait(workflowInstancesAreFinishedCheck,Arrays.asList(workflowInstanceKey));
    return this;
  }
  
  public OperateTester workflowInstanceIsCompleted() {
    processAllRecordsAndWait(workflowInstanceIsCompletedCheck, workflowInstanceKey);
    return this;
  }
  
  public OperateTester failTask(String taskName, String errorMessage) {
    jobKey = ZeebeTestUtil.failTask(zeebeClient, taskName, UUID.randomUUID().toString(), 3,errorMessage);
    return this;
  }
 
  public OperateTester incidentIsActive() {
    processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);
    return this;
  }
  
  public OperateTester and() {
    return this;
  }
  
  public OperateTester waitUntil() {
    return this;
  }

  private void processAllRecordsAndWait(Predicate<Object[]> waitTill, Object... arguments) {
    int maxRounds = 500;
    boolean found = waitTill.test(arguments);
    long start = System.currentTimeMillis();
    while (!found && waitingRound < maxRounds) {
      zeebeImporter.resetCounters();
      try {
        zeebeImporter.performOneRoundOfImport();
      } catch (Exception e) {
        logger.error(e.getMessage(), e);
      }
      long shouldImportCount = zeebeImporter.getScheduledImportCount();
      long imported = zeebeImporter.getImportedCount();
      //long failed = zeebeImporter.getFailedCount();
      int waitForImports = 0;
      while (shouldImportCount != 0 && imported < shouldImportCount && waitForImports < 10) {
        waitForImports++;
        try {
          Thread.sleep(500L);
          zeebeImporter.performOneRoundOfImport();
        } catch (Exception e) {
          waitingRound = 1;
          zeebeImporter.resetCounters();
          logger.error(e.getMessage(), e);
        }
        shouldImportCount = zeebeImporter.getScheduledImportCount();
        imported = zeebeImporter.getImportedCount();
      }
      if(shouldImportCount!=0) {
        logger.debug("Imported {} of {} records", imported, shouldImportCount);
      }
      found = waitTill.test(arguments);
      waitingRound++;
    }
    if(found) {
      logger.debug("Conditions met in round {} ({} ms).", waitingRound--, System.currentTimeMillis()-start);
    }
    waitingRound = 1;
    zeebeImporter.resetCounters();
    if (waitingRound >=  maxRounds) {
      throw new OperateRuntimeException("Timeout exception");
    }
  }

  public OperateTester updateVariableOperation(String varName, String varValue) throws Exception {
    final OperationRequestDto op = new OperationRequestDto(OperationType.UPDATE_VARIABLE);
    op.setName(varName);
    op.setValue(varValue);
    op.setScopeId(ConversionUtils.toStringOrNull(workflowInstanceKey));
    postOperation(op);
    refreshIndexesInElasticsearch();
    return this;
  }
  
  public OperateTester cancelWorkflowInstanceOperation() throws Exception {
    final ListViewQueryDto workflowInstanceQuery = createAllQuery();
    workflowInstanceQuery.setIds(Collections.singletonList(workflowInstanceKey.toString()));
    
    BatchOperationRequestDto batchOperationDto = new BatchOperationRequestDto();
    batchOperationDto.getQueries().add(workflowInstanceQuery);
    batchOperationDto.setOperationType(OperationType.CANCEL_WORKFLOW_INSTANCE);
    
    MockHttpServletRequestBuilder postOperationRequest =
      post("/api/workflow-instances/operation")
        .content(mockMvcTestRule.json(batchOperationDto))
        .contentType(mockMvcTestRule.getContentType());
     
    mockMvcTestRule.getMockMvc().perform(postOperationRequest)
        .andExpect(status().is(HttpStatus.SC_OK))
        .andReturn();
    
    refreshIndexesInElasticsearch();
    executeOneBatch();
    return this;
  }
  
  protected ListViewQueryDto createAllQuery() {
    ListViewQueryDto query = new ListViewQueryDto();
    query.setRunning(true);
    query.setActive(true);
    query.setIncidents(true);
    query.setFinished(true);
    query.setCanceled(true);
    query.setCompleted(true);
    return query;
  }

  public OperateTester operationIsCompleted() {
    executeOneBatch();
    processAllRecordsAndWait(operationsByWorkflowInstanceAreCompletedCheck, workflowInstanceKey);
    return this;
  }
  
  private MvcResult postOperation(OperationRequestDto operationRequest) throws Exception {
    MockHttpServletRequestBuilder postOperationRequest =
      post(String.format( "/api/workflow-instances/%s/operation", workflowInstanceKey))
        .content(mockMvcTestRule.json(operationRequest))
        .contentType(mockMvcTestRule.getContentType());

    final MvcResult mvcResult =
      mockMvcTestRule.getMockMvc().perform(postOperationRequest)
        .andExpect(status().is(HttpStatus.SC_OK))
        .andReturn();
    return mvcResult;
  }
  
  private void refreshIndexesInElasticsearch() {
    try {
     // esClient.indices().refresh(new RefreshRequest(), RequestOptions.DEFAULT);
      zeebeEsClient.indices().refresh(new RefreshRequest(), RequestOptions.DEFAULT);
    } catch (IndexNotFoundException | IOException e) {
      logger.error(e.getMessage(), e);
    }
  }

  private void executeOneBatch() {
    try {
      List<Future<?>> futures = operationExecutor.executeOneBatch();
      //wait till all scheduled tasks are executed
      for(Future f: futures) { f.get(); }
    } catch (Exception e) {
      fail(e.getMessage(), e);
    }
  }
 
}
