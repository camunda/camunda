/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.data.generation;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
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
  private DataGeneratorProperties dataGeneratorProperties;

  @Autowired
  private ZeebeClient zeebeClient;

  private Set<String> bpmnProcessIds = new HashSet<>();
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
    final int incidentCount = dataGeneratorProperties.getIncidentCount();
    ZeebeTestUtil.failTask(zeebeClient, jobType, "worker", "Error", incidentCount);
    logger.info("{} incidents created", dataGeneratorProperties.getIncidentCount());
  }

  private void completeAllTasks(String jobType) {
    completeTasks(jobType, dataGeneratorProperties.getWorkflowInstanceCount());
    logger.info("{} jobs task1 completed", dataGeneratorProperties.getWorkflowInstanceCount());
  }

  private void completeTasks(String jobType, int count) {
    ZeebeTestUtil.completeTask(zeebeClient, jobType, "worker", "{\"varOut\": \"value2\"}", count);
  }

  private void startWorkflowInstances() {
    final long workflowInstanceCount = dataGeneratorProperties.getWorkflowInstanceCount();
    for (int i = 0; i< workflowInstanceCount; i++) {
      ZeebeTestUtil.startWorkflowInstance(zeebeClient, getRandomBpmnProcessId(), "{\"var1\": \"value1\"}");
      if (i % 1000 == 0) {
        logger.info("{} workflow instances started", i);
      }
    }
    logger.info("{} workflow instances started", workflowInstanceCount);
  }

  private String getRandomBpmnProcessId() {
    return getBpmnProcessId(random.nextInt(dataGeneratorProperties.getWorkflowCount()));
  }

  private void deployWorkflows() {
    for (int i = 0; i< dataGeneratorProperties.getWorkflowCount(); i++) {
      String bpmnProcessId = getBpmnProcessId(i);
      ZeebeTestUtil.deployWorkflow(zeebeClient, createModel(bpmnProcessId), bpmnProcessId + ".bpmn");
      bpmnProcessIds.add(bpmnProcessId);
    }
    logger.info("{} workflows deployed", dataGeneratorProperties.getWorkflowCount());
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
