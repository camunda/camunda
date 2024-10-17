/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.migration.v110;

import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.STATE;
import static io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceState.COMPLETED;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.qa.util.TestContext;
import io.camunda.operate.qa.util.ZeebeTestUtil;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import io.camunda.zeebe.client.ZeebeClient;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** It is considered that Zeebe and Elasticsearch are running. */
@Component
public class BigProcessDataGenerator {

  public static final String PROCESS_BPMN_PROCESS_ID = "sequential-noop";
  private static final Logger LOGGER = LoggerFactory.getLogger(BigProcessDataGenerator.class);
  private ZeebeClient zeebeClient;

  @Autowired private RestHighLevelClient esClient;

  private void init(final TestContext testContext) {
    zeebeClient =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(testContext.getExternalZeebeContactPoint())
            .usePlaintext()
            .build();
  }

  public void createData(final TestContext testContext) throws Exception {
    init(testContext);
    try {
      final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
      LOGGER.info("Starting generating data for process {}", PROCESS_BPMN_PROCESS_ID);

      deployProcess();
      startBigProcessInstance();
      finishEndTask();

      waitUntilAllDataIsImported();

      LOGGER.info(
          "Data generation completed in: {} s",
          ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()));
      testContext.addProcess(PROCESS_BPMN_PROCESS_ID);
    } finally {
      closeClients();
    }
  }

  private void waitUntilAllDataIsImported() throws IOException {
    LOGGER.info("Wait till data is imported.");
    final SearchRequest searchRequest = new SearchRequest(getAliasFor(ListViewTemplate.INDEX_NAME));
    searchRequest
        .source()
        .query(
            joinWithAnd(
                termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION),
                termQuery(BPMN_PROCESS_ID, PROCESS_BPMN_PROCESS_ID),
                termQuery(STATE, COMPLETED)));
    SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    int count = 0;
    final int maxWait = 151;
    while (searchResponse.getHits().getTotalHits().value < 1 && count < maxWait) {
      count++;
      ThreadUtil.sleepFor(2000L);
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    }
    if (count == maxWait) {
      throw new RuntimeException("Waiting for loading process instances failed: Timeout");
    }
  }

  private void finishEndTask() {
    // wait for task "endTask" of long-running process and complete it
    ZeebeTestUtil.completeTask(zeebeClient, "endTask", "data-generator", null, 1);
    LOGGER.info("Task endTask completed.");
  }

  private void deployProcess() {
    final String processDefinitionKey =
        ZeebeTestUtil.deployProcess(zeebeClient, "sequential-noop.bpmn");
    LOGGER.info("Deployed process {} with key {}", PROCESS_BPMN_PROCESS_ID, processDefinitionKey);
  }

  private void startBigProcessInstance() {
    final String payload =
        "{\"items\": ["
            + IntStream.range(1, 1000)
                .boxed()
                .map(Object::toString)
                .collect(Collectors.joining(","))
            + "]}";
    ZeebeTestUtil.startProcessInstance(zeebeClient, PROCESS_BPMN_PROCESS_ID, payload);
    LOGGER.info("Started process instance with id {} ", PROCESS_BPMN_PROCESS_ID);
  }

  private String getAliasFor(final String index) {
    return String.format("operate-%s-*_alias", index);
  }

  private void closeClients() {
    if (zeebeClient != null) {
      zeebeClient.close();
      zeebeClient = null;
    }
  }
}
