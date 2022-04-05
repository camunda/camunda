/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.migration.v100;

import static io.camunda.operate.entities.listview.ProcessInstanceState.COMPLETED;
import static io.camunda.operate.schema.templates.ListViewTemplate.BPMN_PROCESS_ID;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.STATE;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.zeebe.client.ZeebeClient;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import io.camunda.operate.qa.util.ZeebeTestUtil;
import io.camunda.operate.qa.util.migration.TestContext;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ThreadUtil;
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
public class BigProcessDataGenerator {

  private static final Logger logger = LoggerFactory.getLogger(BigProcessDataGenerator.class);
  public static final String PROCESS_BPMN_PROCESS_ID = "sequential-noop";
  private ZeebeClient zeebeClient;

  @Autowired
  private RestHighLevelClient esClient;

  private void init(TestContext testContext) {
    zeebeClient = ZeebeClient.newClientBuilder().gatewayAddress(testContext.getExternalZeebeContactPoint()).usePlaintext().build();
  }

  public void createData(TestContext testContext) throws Exception {
    init(testContext);
    try {
      final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
      logger.info("Starting generating data for process {}", PROCESS_BPMN_PROCESS_ID);

      deployProcess();
      startBigProcessInstance();
      finishEndTask();

      waitUntilAllDataIsImported();

      logger.info("Data generation completed in: {} s",
          ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime
              .now()));
      testContext.addProcess(PROCESS_BPMN_PROCESS_ID);
    } finally {
      closeClients();
    }
  }

  private void waitUntilAllDataIsImported() throws IOException {
    logger.info("Wait till data is imported.");
    SearchRequest searchRequest = new SearchRequest(getAliasFor(ListViewTemplate.INDEX_NAME));
    searchRequest.source().query(joinWithAnd(
        termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
        termQuery(BPMN_PROCESS_ID, PROCESS_BPMN_PROCESS_ID),
        termQuery(STATE, COMPLETED)
        )
    );
    SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    int count = 0, maxWait = 101;
    while(searchResponse.getHits().getTotalHits().value < 1  && count < maxWait) {
      count++;
      ThreadUtil.sleepFor(2000L);
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    }
    if (count == maxWait) {
      throw new RuntimeException("Waiting for loading process instances failed: Timeout");
    }
  }

  private void finishEndTask() {
    //wait for task "endTask" of long-running process and complete it
    ZeebeTestUtil.completeTask(zeebeClient, "endTask", "data-generator", null, 1);
    logger.info("Task endTask completed.");
  }

  private void deployProcess() {
    String processDefinitionKey = ZeebeTestUtil
        .deployProcess(zeebeClient, "sequential-noop.bpmn");
    logger.info("Deployed process {} with key {}", PROCESS_BPMN_PROCESS_ID, processDefinitionKey);
  }

  private void startBigProcessInstance() {
    String payload =
        "{\"items\": [" + IntStream.range(1, 1000).boxed().map(Object::toString).collect(
            Collectors.joining(",")) + "]}";
    ZeebeTestUtil
        .startProcessInstance(zeebeClient, PROCESS_BPMN_PROCESS_ID, payload);
    logger.info("Started process instance with id {} ", PROCESS_BPMN_PROCESS_ID);
  }

  private String getAliasFor(String index) {
    return String.format("operate-%s-*_alias", index);
  }

  private void closeClients() {
    if (zeebeClient != null) {
      zeebeClient.close();
      zeebeClient = null;
    }
  }

}
