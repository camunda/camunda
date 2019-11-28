/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.migration;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.annotation.PreDestroy;

import org.camunda.operate.entities.OperationType;
import org.camunda.operate.qa.util.ZeebeTestUtil;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;

/**
 * It is considered that Zeebe and Elasticsearch are running.
 */
@Component
@Configuration
public class DataGenerator {

  private static final Logger logger = LoggerFactory.getLogger(DataGenerator.class);

  @Autowired
  private MigrationProperties migrationProperties;

  @Autowired
  private ZeebeClient zeebeClient;
  
  @Autowired
  private RestHighLevelClient esClient;
  
  @Autowired
  private RestClient operateRestClient;

  private Random random = new Random();

  private List<Long> workflowInstanceKeys = new ArrayList<>();
  
  public void createData() {
    final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
    logger.info("Starting generating data...");

    deployWorkflows(migrationProperties.getWorkflowCount());
    workflowInstanceKeys = startWorkflowInstances(migrationProperties.getWorkflowInstanceCount());
    completeTasks("task1",migrationProperties.getWorkflowInstanceCount());
    createIncidents("task2",migrationProperties.getIncidentCount());
    
    createOperation(OperationType.CANCEL_WORKFLOW_INSTANCE);
    
    try {
		esClient.indices().refresh(new RefreshRequest("operate-*"), RequestOptions.DEFAULT);
	} catch (IOException e) {
		logger.error("Error in refreshing indices",e);
	}
    logger.info("Data generation completed in: " + ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()) + " s");
  }
  
  private void createOperation(OperationType operationType) {
	 boolean operationStarted = false;
	 int attempts = 0;
	 while(!operationStarted && attempts < workflowInstanceKeys.size() * 2) {
	    Long workflowInstanceKey = chooseKey(workflowInstanceKeys);
	    operationStarted = operateRestClient.createOperation(workflowInstanceKey,operationType);
	    attempts++;
	 }
	 if(operationStarted) {
		 logger.info("Operation {} started",operationType.name());
	 }else {
		 logger.info("Operation {} could not started",operationType.name());
	 }
  }
  
  private void createIncidents(String jobType, int numberOfIncidents) {
	ZeebeTestUtil.failTask(zeebeClient, jobType, "worker", numberOfIncidents);
	logger.info("{} incidents in {} created", numberOfIncidents, jobType);
  }

  private void completeTasks(String jobType, int count) {
    ZeebeTestUtil.completeTask(zeebeClient, jobType, "worker", "{\"varOut\": \"value2\"}", count);
    logger.info("{} tasks {} completed",count,jobType);
  }

  private List<Long> startWorkflowInstances(int numberOfWorkflowInstances) {
	List<Long> workflowInstanceKeys = new ArrayList<>();
	for(int i=0;i<numberOfWorkflowInstances;i++) {
    	String bpmnProcessId = getRandomBpmnProcessId();
    	long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient,bpmnProcessId, "{\"var1\": \"value1\"}");
    	logger.info("Started workflowInstance {} for workflow {}",workflowInstanceKey,bpmnProcessId);
    	workflowInstanceKeys.add(workflowInstanceKey);
    }
    logger.info("{} workflowInstances started",workflowInstanceKeys.size());
    return workflowInstanceKeys;
  }
  
  private String getRandomBpmnProcessId() {
    return getBpmnProcessId(random.nextInt(migrationProperties.getWorkflowCount()));
  }

  @PreDestroy
  public void shutdown(){
    zeebeClient.close();
  }

  protected void deployWorkflows(int numberOfWorkflows) {
    for (int i = 0; i< numberOfWorkflows; i++) {
      String bpmnProcessId = getBpmnProcessId(i);
      String workflowKey = ZeebeTestUtil.deployWorkflow(zeebeClient, createModel(bpmnProcessId), bpmnProcessId + ".bpmn");
      logger.info("Deployed workflow {} with key {}",bpmnProcessId,workflowKey);
    }
    logger.info("{} workflows deployed", numberOfWorkflows);
  }

  private String getBpmnProcessId(int workflowNumber) {
    return "process-" + workflowNumber;
  }

  private BpmnModelInstance createModel(String bpmnProcessId) {
    return Bpmn.createExecutableProcess(bpmnProcessId)
    .startEvent("start")
      .serviceTask("task1").zeebeTaskType("task1")
        .zeebeInput("var1", "varIn")
        .zeebeOutput("varOut", "var2")
      .serviceTask("task2").zeebeTaskType("task2")
      .serviceTask("task3").zeebeTaskType("task3")
      .serviceTask("task4").zeebeTaskType("task4")
      .serviceTask("task5").zeebeTaskType("task5")
    .endEvent()
    .done();
  }
  
  private Long chooseKey(List<Long> keys) {
	return keys.get(random.nextInt(keys.size()));
  }
  
}
