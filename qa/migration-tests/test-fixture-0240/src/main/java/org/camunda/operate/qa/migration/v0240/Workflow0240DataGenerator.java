/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.migration.v0240;

import static org.camunda.operate.entities.listview.WorkflowInstanceState.COMPLETED;
import static org.camunda.operate.schema.templates.ListViewTemplate.BPMN_PROCESS_ID;
import static org.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static org.camunda.operate.schema.templates.ListViewTemplate.STATE;
import static org.camunda.operate.schema.templates.ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION;
import static org.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.zeebe.client.ZeebeClient;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.camunda.operate.qa.util.ZeebeTestUtil;
import org.camunda.operate.qa.util.migration.TestContext;
import org.camunda.operate.schema.templates.ListViewTemplate;
import org.camunda.operate.util.ThreadUtil;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * It is considered that Zeebe and Elasticsearch are running.
 */
@Component
public class Workflow0240DataGenerator {

  private static final Logger logger = LoggerFactory.getLogger(Workflow0240DataGenerator.class);
  public static final String WORKFLOW_BPMN_PROCESS_ID = "sequential-noop";
  private ZeebeClient zeebeClient;

  @Autowired
  private RestHighLevelClient esClient;

  private void init(TestContext testContext) {
    zeebeClient = ZeebeClient.newClientBuilder().brokerContactPoint(testContext.getExternalZeebeContactPoint()).usePlaintext().build();
  }

  public void createData(TestContext testContext) throws Exception {
    init(testContext);
    try {
      final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
      logger.info("Starting generating data for workflow {}", WORKFLOW_BPMN_PROCESS_ID);

      deployWorkflow();
      startBigWorkflowInstance();
      finishEndTask();

      waitUntilAllDataIsImported();

      logger.info("Data generation completed in: {} s",
          ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime
              .now()));
      testContext.addWorkflow(WORKFLOW_BPMN_PROCESS_ID);
    } finally {
      closeClients();
    }
  }

  private void waitUntilAllDataIsImported() throws IOException {
    logger.info("Wait till data is imported.");
    SearchRequest searchRequest = new SearchRequest(getAliasFor(ListViewTemplate.INDEX_NAME));
    searchRequest.source().query(joinWithAnd(
        termQuery(JOIN_RELATION, WORKFLOW_INSTANCE_JOIN_RELATION),
        termQuery(BPMN_PROCESS_ID, WORKFLOW_BPMN_PROCESS_ID),
        termQuery(STATE, COMPLETED)
        )
    );
    SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    int count = 0, maxWait = 101;
    while(searchResponse.getHits().getTotalHits() < 1  && count < maxWait) {
      count++;
      ThreadUtil.sleepFor(2000L);
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    }
    if (count == maxWait) {
      throw new RuntimeException("Waiting for loading workflow instances failed: Timeout");
    }
  }

  private void finishEndTask() {
    //wait for task "endTask" of long-running process and complete it
    ZeebeTestUtil.completeTask(zeebeClient, "endTask", "data-generator", null, 1);
    logger.info("Task endTask completed.");
  }

  private void deployWorkflow() {
    String workflowKey = ZeebeTestUtil
        .deployWorkflow(zeebeClient, "sequential-noop.bpmn");
    logger.info("Deployed workflow {} with key {}", WORKFLOW_BPMN_PROCESS_ID, workflowKey);
  }

  private void startBigWorkflowInstance() {
    String payload =
        "{\"items\": [" + IntStream.range(1, 3000).boxed().map(Object::toString).collect(
            Collectors.joining(",")) + "]}";
    ZeebeTestUtil
        .startWorkflowInstance(zeebeClient, WORKFLOW_BPMN_PROCESS_ID, payload);
    logger.info("Started workflow instance with id {} ", WORKFLOW_BPMN_PROCESS_ID);
  }

  private String getAliasFor(String index) {
    return String.format("operate-%s-%s_alias", index, TestFixture.VERSION);
  }

  private void closeClients() {
    if (zeebeClient != null) {
      zeebeClient.close();
      zeebeClient = null;
    }
  }

}
