/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.migration;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.camunda.operate.entities.OperationType;
import org.camunda.operate.qa.util.ZeebeTestUtil;
import org.camunda.operate.util.ThreadUtil;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;

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

  private DateTimeFormatter archiverDateTimeFormatter;

  @PostConstruct
  public void init() {
    archiverDateTimeFormatter = DateTimeFormatter.ofPattern(migrationProperties.getArchiverDateFormat()).withZone(ZoneId.systemDefault());
  }

  public void createData() {
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

  private void waitTillSomeInstancesAreArchived() {
    logger.info("Wait till data are imported.");
    ThreadUtil.sleepFor(60 * 1000L);

    logger.info("Wait till finished instances will be archived.");
    ThreadUtil.sleepFor(100 * 1000L);
    int count = 0;
    while (!someInstancesAreArchived() && count < 10) {
      ThreadUtil.sleepFor(20 * 1000L);
      count++;
    }
    if (count == 10 && !someInstancesAreArchived()) {
      logger.error("There must be some archived instances");
      throw new RuntimeException("Data generation was not full: no archived instances");
    }
  }

//this method may be still needed to create archived data before v.1.2.0
//  private void moveListViewDatesBack() {
//    String script =
//        "def sf = new SimpleDateFormat(\"yyyy-MM-dd'T'HH:mm:ss.SSSZZ\");"
//            + "if (ctx._source.startDate != null) {"
//            + "   def dt = sf.parse(ctx._source.startDate);"
//            + "   def calendar = sf.getCalendar();"
//            + "   calendar.setTime(dt);"
//            + "   calendar.add(Calendar.HOUR, -1);"
//            + "   ctx._source.startDate = calendar.getTime();}"
//            + "if (ctx._source.endDate != null) {"
//            + "   def dt2 = sf.parse(ctx._source.endDate);"
//            + "   def calendar2 = sf.getCalendar();"
//            + "   calendar2.setTime(dt2);"
//            + "   calendar2.add(Calendar.HOUR, -1);"
//            + "   ctx._source.endDate = calendar2.getTime(); }";
//    UpdateByQueryRequest request = new UpdateByQueryRequest("operate-list-view*")
//        .setQuery(matchAllQuery())
//        .setScript(new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, new HashMap<>()))
//        .setAbortOnVersionConflict(false)
//        .setRefresh(true);
//    ;
//    try {
//      esClient.updateByQuery(request, RequestOptions.DEFAULT);
//    } catch (IOException e) {
//      throw new RuntimeException("Exception occurred while moving dates back: " + e.getMessage(), e);
//    }
//  }

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
