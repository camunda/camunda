/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.it;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import org.apache.commons.lang3.Validate;
import org.apache.http.HttpStatus;
import org.camunda.operate.archiver.Archiver;
import org.camunda.operate.archiver.ArchiverJob;
import org.camunda.operate.archiver.ArchiverJob.ArchiveBatch;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.exceptions.ReindexException;
import org.camunda.operate.util.ConversionUtils;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.webapp.rest.dto.operation.BatchOperationRequestDto;
import org.camunda.operate.webapp.rest.dto.operation.OperationRequestDto;
import org.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import org.camunda.operate.zeebe.ImportValueType;
import org.camunda.operate.zeebeimport.RecordsReader;
import org.camunda.operate.zeebeimport.ZeebeImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Component
@Scope(SCOPE_PROTOTYPE)
public class OperateTester {

  protected static final Logger logger = LoggerFactory.getLogger(OperateTester.class);

  private ZeebeClient zeebeClient;
  private MockMvcTestRule mockMvcTestRule;
  private ElasticsearchTestRule elasticsearchTestRule;
  
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

  @Autowired
  private Archiver archiver;

  @Autowired
  private ZeebeImporter zeebeImporter;

  @Autowired
  protected OperationExecutor operationExecutor;

  private boolean operationExecutorEnabled = true;

  public OperateTester(ZeebeClient zeebeClient, MockMvcTestRule mockMvcTestRule, ElasticsearchTestRule elasticsearchTestRule) {
    this.zeebeClient = zeebeClient;
    this.mockMvcTestRule = mockMvcTestRule;
    this.elasticsearchTestRule = elasticsearchTestRule;
  }

  public Long getWorkflowInstanceKey() {
    return workflowInstanceKey;
  }

  public OperateTester createAndDeploySimpleWorkflow(String processId,String activityId) {
    BpmnModelInstance workflow = Bpmn.createExecutableProcess(processId)
        .startEvent("start")
        .serviceTask(activityId).zeebeTaskType(activityId)
        .endEvent()
        .done();
    workflowKey = ZeebeTestUtil.deployWorkflow(zeebeClient, workflow,processId+".bpmn");
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
    elasticsearchTestRule.processAllRecordsAndWait(workflowIsDeployedCheck, workflowKey);
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
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstancesAreStartedCheck, Arrays.asList(workflowInstanceKey));
    return this;
  }
  
  public OperateTester workflowInstanceIsFinished() {
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstancesAreFinishedCheck,Arrays.asList(workflowInstanceKey));
    return this;
  }
  
  public OperateTester workflowInstanceIsCompleted() {
    elasticsearchTestRule.processAllRecordsAndWait(workflowInstanceIsCompletedCheck, workflowInstanceKey);
    return this;
  }
  
  public OperateTester failTask(String taskName, String errorMessage) {
    /*jobKey =*/ ZeebeTestUtil.failTask(zeebeClient, taskName, UUID.randomUUID().toString(), 3,errorMessage);
    return this;
  }
 
  public OperateTester incidentIsActive() {
    elasticsearchTestRule.processAllRecordsAndWait(incidentIsActiveCheck, workflowInstanceKey);
    return this;
  }
  
  public OperateTester and() {
    return this;
  }
  
  public OperateTester waitUntil() {
    return this;
  }

  public OperateTester updateVariableOperation(String varName, String varValue) throws Exception {
    final OperationRequestDto op = new OperationRequestDto(OperationType.UPDATE_VARIABLE);
    op.setName(varName);
    op.setValue(varValue);
    op.setScopeId(ConversionUtils.toStringOrNull(workflowInstanceKey));
    postOperation(op);
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    return this;
  }
  
  public OperateTester cancelWorkflowInstanceOperation() throws Exception {
    final ListViewQueryDto workflowInstanceQuery = ListViewQueryDto.createAll();
    workflowInstanceQuery.setIds(Collections.singletonList(workflowInstanceKey.toString()));
    
    BatchOperationRequestDto batchOperationDto = new BatchOperationRequestDto();
    batchOperationDto.getQueries().add(workflowInstanceQuery);
    batchOperationDto.setOperationType(OperationType.CANCEL_WORKFLOW_INSTANCE);
    
    postOperation(batchOperationDto);
    elasticsearchTestRule.refreshIndexesInElasticsearch();
    return this;
  }
  
  public OperateTester operationIsCompleted() throws Exception {
    executeOneBatch();
    elasticsearchTestRule.processAllRecordsAndWait(operationsByWorkflowInstanceAreCompletedCheck, workflowInstanceKey);
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

  private int executeOneBatch() throws Exception {
    if(!operationExecutorEnabled) return 0;
      List<Future<?>> futures = operationExecutor.executeOneBatch();
      //wait till all scheduled tasks are executed
      for(Future f: futures) { f.get();}
      return 0;//return futures.size()
  }
  
  public int importOneType(ImportValueType importValueType) throws IOException {
    List<RecordsReader> readers = elasticsearchTestRule.getRecordsReaders(importValueType);
    int count = 0;
    for (RecordsReader reader: readers) {
      count += zeebeImporter.importOneBatch(reader);
    }
    return count;
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

  public OperateTester archive() {
    try {
      ArchiverJob.ArchiveBatch finishedAtDateIds = new ArchiveBatch("_test_archived", Arrays.asList(workflowInstanceKey));
      archiver.archiveNextBatch(finishedAtDateIds);
    } catch (ReindexException e) {
      return this;
    }
    return this;
  }

  public OperateTester executeOperations() throws Exception {
     executeOneBatch();
     return this;
  }

  public OperateTester archiveIsDone() {
    elasticsearchTestRule.refreshOperateESIndices();
    return this;
  }
}
