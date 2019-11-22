/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.migration;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;

import javax.annotation.PreDestroy;

import org.camunda.operate.qa.util.ZeebeTestUtil;
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

private Random random = new Random();

  public void createData() {
    final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
    logger.info("Starting generating data...");

    deployWorkflows();
    startWorkflowInstances();
    completeAllTasks("task1");
    createIncidents("task2");

    logger.info("Data generation completed in: " + ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()) + " s");
  }

  private void createIncidents(String jobType) {
    final int incidentCount = migrationProperties.getIncidentCount();
    ZeebeTestUtil.failTask(zeebeClient, jobType, "worker", incidentCount);
    logger.info("{} incidents created", migrationProperties.getIncidentCount());
  }

  private void completeAllTasks(String jobType) {
    completeTasks(jobType, migrationProperties.getWorkflowInstanceCount());
    logger.info("{} jobs task1 completed", migrationProperties.getWorkflowInstanceCount());
  }

  private void completeTasks(String jobType, int count) {
    ZeebeTestUtil.completeTask(zeebeClient, jobType, "worker", "{\"varOut\": \"value2\"}", count);
  }

  private void startWorkflowInstances() {
    for(int i=0;i<migrationProperties.getWorkflowInstanceCount();i++) {
    	String bpmnProcessId = getRandomBpmnProcessId();
    	long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient,bpmnProcessId, "{\"var1\": \"value1\"}");
    	logger.info("Started workflowInstance {} for workflow {}",workflowInstanceKey,bpmnProcessId);
    }
  }
  
  private String getRandomBpmnProcessId() {
    return getBpmnProcessId(random.nextInt(migrationProperties.getWorkflowCount()));
  }

  @PreDestroy
  public void shutdown(){
    zeebeClient.close();
  }

  private void deployWorkflows() {
    for (int i = 0; i< migrationProperties.getWorkflowCount(); i++) {
      String bpmnProcessId = getBpmnProcessId(i);
      ZeebeTestUtil.deployWorkflow(zeebeClient, createModel(bpmnProcessId), bpmnProcessId + ".bpmn");
    }
    logger.info("{} workflows deployed", migrationProperties.getWorkflowCount());
  }

  private String getBpmnProcessId(int i) {
    return "process" + i;
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
}
