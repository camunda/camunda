/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.migration.v121;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.camunda.operate.qa.util.ZeebeTestUtil;
import org.camunda.operate.qa.util.migration.TestContext;
import org.camunda.operate.schema.templates.ListViewTemplate;
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
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static org.camunda.operate.schema.templates.ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

/**
 * It is considered that Zeebe and Elasticsearch are running.
 */
@Component
public class Workflow121DataGenerator {

  private static final Logger logger = LoggerFactory.getLogger(Workflow121DataGenerator.class);
  public static final String WORKFLOW_BPMN_PROCESS_ID = "workflow121";
  public static final int WORKFLOW_INSTANCE_COUNT = 5;
  public static final int INCIDENT_COUNT = 3;

  /**
   * ZeebeClient must not be reused between different test fixtures, as this may be different versions of client in the future.
   */
  private ZeebeClient zeebeClient;

  @Autowired
  private RestHighLevelClient esClient;
  
  private List<Long> workflowInstanceKeys = new ArrayList<>();

  private StatefulRestTemplate operateRestClient;

  private void init(TestContext testContext) {
    zeebeClient = ZeebeClient.newClientBuilder().brokerContactPoint(testContext.getExternalZeebeContactPoint()).usePlaintext().build();
    operateRestClient = new StatefulRestTemplate(testContext.getExternalOperateHost(), testContext.getExternalOperatePort(), testContext.getExternalOperateContextPath());
    operateRestClient.loginWhenNeeded();
  }

  public void createData(TestContext testContext) throws Exception {
    init(testContext);
    try {
      final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
      logger.info("Starting generating data for workflow {}", WORKFLOW_BPMN_PROCESS_ID);

      deployWorkflow();
      workflowInstanceKeys = startWorkflowInstances(WORKFLOW_INSTANCE_COUNT);
      completeTasks("task121_1", WORKFLOW_INSTANCE_COUNT);
      createIncidents("task121_2", INCIDENT_COUNT);

      waitUntilAllDataIsImported();

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

  private void waitUntilAllDataIsImported() throws IOException {
    logger.info("Wait till data is imported.");
    SearchRequest searchRequest = new SearchRequest(getAliasFor(ListViewTemplate.INDEX_NAME));
    searchRequest.source().query(joinWithAnd(termQuery(JOIN_RELATION, WORKFLOW_INSTANCE_JOIN_RELATION),
        termQuery(ListViewTemplate.BPMN_PROCESS_ID, WORKFLOW_BPMN_PROCESS_ID)));
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
      .serviceTask("task121_1").zeebeJobType("task121_1")
        .zeebeInput("var1", "varIn")
        .zeebeOutput("varOut", "var2")
      .serviceTask("task121_2").zeebeJobType("task121_2")
      .serviceTask("task121_3").zeebeJobType("task121_3")
    .endEvent()
    .done();
  }

  private long countEntitiesFor(SearchRequest searchRequest) throws IOException{
    searchRequest.source().size(1000);
    SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    return searchResponse.getHits().getTotalHits();
  }

  private String getAliasFor(String index) {
    return getAliasFor(index, TestFixture.OPERATE_VERSION);
  }

  private String getAliasFor(String index, String version) {
    if(version==null || version.isBlank() || version.equals("1.1.0")) {
      return String.format("operate-%s_alias", index);
    }else {
      return String.format("operate-%s-%s_alias", index, version);
    }
  }
}
