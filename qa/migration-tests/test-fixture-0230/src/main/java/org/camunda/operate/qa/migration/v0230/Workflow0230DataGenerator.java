/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.migration.v0230;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.qa.util.ZeebeTestUtil;
import org.camunda.operate.qa.util.migration.TestContext;
import org.camunda.operate.schema.templates.BatchOperationTemplate;
import org.camunda.operate.schema.templates.ListViewTemplate;
import org.camunda.operate.util.CollectionUtil;
import org.camunda.operate.util.ThreadUtil;
import org.camunda.operate.util.rest.StatefulRestTemplate;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static org.camunda.operate.schema.templates.ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

/**
 * It is considered that Zeebe and Elasticsearch are running.
 */
@Component
public class Workflow0230DataGenerator {

  private static final Logger logger = LoggerFactory.getLogger(Workflow0230DataGenerator.class);
  public static final String WORKFLOW_BPMN_PROCESS_ID = "process0230";
  public static final int WORKFLOW_INSTANCE_COUNT = 51;
  public static final int INCIDENT_COUNT = 32;
  public static final int COUNT_OF_CANCEL_OPERATION = 9;
  public static final int COUNT_OF_RESOLVE_OPERATION = 8;
  private static final DateTimeFormatter ARCHIVER_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

  /**
   * ZeebeClient must not be reused between different test fixtures, as this may be different versions of client in the future.
   */
  private ZeebeClient zeebeClient;

  @Autowired
  private RestHighLevelClient esClient;
  
  private Random random = new Random();

  private List<Long> workflowInstanceKeys = new ArrayList<>();

  private StatefulRestTemplate operateRestClient;

  private void init(TestContext testContext) {
    zeebeClient = ZeebeClient.newClientBuilder().brokerContactPoint(testContext.getExternalZeebeContactPoint()).usePlaintext().build();

    operateRestClient = new StatefulRestTemplate(testContext.getExternalOperateHost(), testContext.getExternalOperatePort(),testContext.getExternalOperateContextPath());
    operateRestClient.loginWhenNeeded();
  }

  public void createData(TestContext testContext) throws Exception {
    init(testContext);
    try {
      final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
      logger.info("Starting generating data for workflow {}", WORKFLOW_BPMN_PROCESS_ID);

      deployWorkflow();
      workflowInstanceKeys = startWorkflowInstances(WORKFLOW_INSTANCE_COUNT);
      completeTasks("task0230_1", WORKFLOW_INSTANCE_COUNT);
      createIncidents("task0230_2", INCIDENT_COUNT);

      //TODO: How is it possible to to determine when to start create operations?
      ThreadUtil.sleepFor(5000);

      for (int i = 0; i < COUNT_OF_CANCEL_OPERATION; i++) {
        createOperation(OperationType.CANCEL_WORKFLOW_INSTANCE, workflowInstanceKeys.size() * 10);
      }
      logger.info("{} operations of type {} started", COUNT_OF_CANCEL_OPERATION, OperationType.CANCEL_WORKFLOW_INSTANCE);

      for (int i = 0; i < COUNT_OF_RESOLVE_OPERATION; i++) {
        createOperation(OperationType.RESOLVE_INCIDENT, workflowInstanceKeys.size() * 10);
      }
      logger.info("{} operations of type {} started", COUNT_OF_RESOLVE_OPERATION, OperationType.RESOLVE_INCIDENT);

      waitTillSomeInstancesAreArchived();

      try {
        esClient.indices().refresh(new RefreshRequest("operate-*"), RequestOptions.DEFAULT);
      } catch (IOException e) {
        logger.error("Error in refreshing indices", e);
      }
      logger.info("Data generation completed in: {} s", ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()));
      testContext.addWorkflow(WORKFLOW_BPMN_PROCESS_ID);
    } finally {
      closeClients();
    }
  }

  private void closeClients() {
    if (zeebeClient != null) {
      zeebeClient.close();
      zeebeClient = null;
    }
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
    logger.info("Wait till data is imported.");
    SearchRequest searchRequest = new SearchRequest(getAliasFor(ListViewTemplate.INDEX_NAME));
    searchRequest.source().query(termQuery(JOIN_RELATION, WORKFLOW_INSTANCE_JOIN_RELATION));
    long loadedWorkflowInstances = 0;
    int count = 0, maxWait = 101;
    while(WORKFLOW_INSTANCE_COUNT > loadedWorkflowInstances && count < maxWait) {
      count++;
      loadedWorkflowInstances = countEntitiesFor(searchRequest);
      ThreadUtil.sleepFor(1000L);
    }
    if(count == maxWait) {
      throw new RuntimeException("Waiting for loading workflow instances failed: Timeout");
    }
  }

  private boolean someInstancesAreArchived() {
    try {
      SearchResponse search = esClient.search(
          new SearchRequest("operate-*_" + ARCHIVER_DATE_TIME_FORMATTER.format(Instant.now())), RequestOptions.DEFAULT);
      return search.getHits().totalHits > 0;
    } catch (IOException e) {
      throw new RuntimeException("Exception occurred while checking archived indices: " + e.getMessage(), e);
    }
  }

  private void createOperation(OperationType operationType, int maxAttempts) {
    logger.debug("Try to create Operation {} ( {} attempts)", operationType.name(), maxAttempts);
    boolean operationStarted = false;
    int attempts = 0;
    while (!operationStarted && attempts < maxAttempts) {
      Long workflowInstanceKey = chooseKey(workflowInstanceKeys);
      operationStarted = createOperation(workflowInstanceKey, operationType);
      attempts++;
    }
    if (operationStarted) {
      logger.debug("Operation {} started", operationType.name());
    } else {
      throw new RuntimeException(String.format("Operation %s could not started", operationType.name()));
    }
  }

  public boolean createOperation(Long workflowInstanceKey, OperationType operationType) {
    Map<String, Object> operationRequest = CollectionUtil.asMap("operationType", operationType.name());
    final URI url = operateRestClient.getURL("/api/workflow-instances/" + workflowInstanceKey + "/operation");
    ResponseEntity<Map> operationResponse = operateRestClient.postForEntity(url, operationRequest, Map.class);
    return operationResponse.getStatusCode().equals(HttpStatus.OK) && operationResponse.getBody().get(BatchOperationTemplate.ID) != null;
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
    for (int i = 0; i < numberOfWorkflowInstances; i++) {
      String bpmnProcessId = WORKFLOW_BPMN_PROCESS_ID;
      long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, bpmnProcessId, "{\"var1\": \"value1\"}");
      logger.debug("Started workflowInstance {} for workflow {}", workflowInstanceKey, bpmnProcessId);
      workflowInstanceKeys.add(workflowInstanceKey);
    }
    logger.info("{} workflowInstances started", workflowInstanceKeys.size());
    return workflowInstanceKeys;
  }

  private void deployWorkflow() {
    String bpmnProcessId = WORKFLOW_BPMN_PROCESS_ID;
    String workflowKey = ZeebeTestUtil.deployWorkflow(zeebeClient, createModel(bpmnProcessId), bpmnProcessId + ".bpmn");
    logger.info("Deployed workflow {} with key {}", bpmnProcessId, workflowKey);
  }

  private BpmnModelInstance createModel(String bpmnProcessId) {
    return Bpmn.createExecutableProcess(bpmnProcessId)
    .startEvent("start")
      .serviceTask("task0230_1").zeebeJobType("task0230_1")
        .zeebeInput("=var1", "varIn")
        .zeebeOutput("=varOut", "var2")
      .serviceTask("task0230_2").zeebeJobType("task0230_2")
      .serviceTask("task0230_3").zeebeJobType("task0230_3")
      .serviceTask("task0230_4").zeebeJobType("task0230_4")
      .serviceTask("task0230_5").zeebeJobType("task0230_5")
    .endEvent()
    .done();
  }
  
  private Long chooseKey(List<Long> keys) {
    return keys.get(random.nextInt(keys.size()));
  }

  private long countEntitiesFor(SearchRequest searchRequest) throws IOException{
    searchRequest.source().size(1000);
    SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    return searchResponse.getHits().getTotalHits();
  }

  private String getAliasFor(String index) {
    return getAliasFor(index, TestFixture.VERSION);
  }

  private String getAliasFor(String index, String version) {
    if(version==null || version.isBlank() || version.equals("1.1.0")) {
      return String.format("operate-%s_alias", index);
    }else {
      return String.format("operate-%s-%s_alias", index, version);
    }
  }
}
