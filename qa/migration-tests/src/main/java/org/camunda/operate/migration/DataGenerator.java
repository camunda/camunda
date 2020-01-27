/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.migration;

import static org.camunda.operate.es.schema.templates.ListViewTemplate.JOIN_RELATION;
import static org.camunda.operate.es.schema.templates.ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.camunda.operate.entities.OperationType;
import org.camunda.operate.entities.listview.WorkflowInstanceForListViewEntity;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.qa.util.ZeebeTestUtil;
import org.camunda.operate.util.ThreadUtil;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;

/**
 * It is considered that Zeebe and Elasticsearch are running.
 */
@Component
public class DataGenerator {

  private static final Logger logger = LoggerFactory.getLogger(DataGenerator.class);

  @Autowired
  private MigrationProperties migrationProperties;
  
  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ZeebeClient zeebeClient;
  
  @Autowired
  private RestHighLevelClient esClient;
  
  @Autowired
  private RestClient operateRestClient;

  @Autowired
  public BeanFactory beanFactory;
  
  private Random random = new Random();

  private List<Long> workflowInstanceKeys = new ArrayList<>();

  private DateTimeFormatter archiverDateTimeFormatter;

  private EntityReader entityReader;

  @PostConstruct
  public void init() {  
    archiverDateTimeFormatter = DateTimeFormatter.ofPattern(migrationProperties.getArchiverDateFormat()).withZone(ZoneId.systemDefault());
    entityReader = beanFactory.getBean(EntityReader.class,operateProperties.getPreviousSchemaVersion());
  }

  public void createData() throws Exception {
    final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
    logger.info("Starting generating data...");

    deployWorkflows(migrationProperties.getWorkflowCount());
    workflowInstanceKeys = startWorkflowInstances(migrationProperties.getWorkflowInstanceCount());
    completeTasks("task1", migrationProperties.getWorkflowInstanceCount());
    createIncidents("task2", migrationProperties.getIncidentCount());

    for(int i=0;i<migrationProperties.getCountOfCancelOperation();i++) {
      createOperation(OperationType.CANCEL_WORKFLOW_INSTANCE,workflowInstanceKeys.size() * 10);
    }
    for(int i=0;i<migrationProperties.getCountOfResolveOperation();i++) {
      createOperation(OperationType.RESOLVE_INCIDENT,workflowInstanceKeys.size() * 10);
    }

    waitTillSomeInstancesAreArchived();

    try {
      esClient.indices().refresh(new RefreshRequest("operate-*"), RequestOptions.DEFAULT);
    } catch (IOException e) {
      logger.error("Error in refreshing indices", e);
    }
    logger.info("Data generation completed in: " + ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()) + " s");
  }

  private void waitTillSomeInstancesAreArchived() throws IOException {
    waitUntilAllDataAreImported();
    
    int count = 0, maxWait=30;
    logger.info("Waiting for archived data (max: {} sec)",maxWait*10);
    while (!someInstancesAreArchived() && count < maxWait) {
      ThreadUtil.sleepFor(10 * 1000L);
      count++;
    }
    if (count == maxWait ) {
      logger.error("There must be some archived instances");
      throw new RuntimeException("Data generation was not full: no archived instances");
    }
  }

  private void waitUntilAllDataAreImported() throws IOException {
    logger.info("Wait till data are imported.");
    SearchRequest searchRequest = new SearchRequest(entityReader.getAliasFor(ListViewTemplate.INDEX_NAME));  
    searchRequest.source().query(termQuery(JOIN_RELATION, WORKFLOW_INSTANCE_JOIN_RELATION));
    int loadedWorkflowInstances = 0;
    int count = 0, maxWait = 101;
    while(migrationProperties.getWorkflowInstanceCount() > loadedWorkflowInstances && count < maxWait) {
      count++;
      loadedWorkflowInstances = entityReader.searchEntitiesFor(searchRequest, WorkflowInstanceForListViewEntity.class).size();
      ThreadUtil.sleepFor(1000L);
    }
    if(count == maxWait) {
      throw new RuntimeException("Waiting for loading workflow instances failed: Timeout");
    }
  }

  private boolean someInstancesAreArchived() {
    try {
      SearchResponse search = esClient.search(
          new SearchRequest("operate-*_" + archiverDateTimeFormatter.format(Instant.now())), RequestOptions.DEFAULT);
      return search.getHits().totalHits > 0;
    } catch (IOException e) {
      throw new RuntimeException("Exception occurred while checking archived indices: " + e.getMessage(), e);
    }
  }

  private void createOperation(OperationType operationType,int maxAttempts) {
    logger.info("Try to create Operation {} ( {} attempts)", operationType.name(),maxAttempts);
    boolean operationStarted = false;
    int attempts = 0;
    while (!operationStarted && attempts < maxAttempts) {
      Long workflowInstanceKey = chooseKey(workflowInstanceKeys);
      operationStarted = operateRestClient.createOperation(workflowInstanceKey, operationType);
      attempts++;
    }
    if (operationStarted) {
      logger.info("Operation {} started", operationType.name());
    } else {
      throw new RuntimeException(String.format("Operation %s could not started",operationType.name()));
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
    for (int i = 0; i < numberOfWorkflowInstances; i++) {
      String bpmnProcessId = getRandomBpmnProcessId();
      long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, bpmnProcessId, "{\"var1\": \"value1\"}");
      logger.info("Started workflowInstance {} for workflow {}", workflowInstanceKey, bpmnProcessId);
      workflowInstanceKeys.add(workflowInstanceKey);
    }
    logger.info("{} workflowInstances started", workflowInstanceKeys.size());
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
