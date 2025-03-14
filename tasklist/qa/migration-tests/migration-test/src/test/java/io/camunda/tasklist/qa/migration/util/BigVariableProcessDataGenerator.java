/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.migration.util;

import io.camunda.tasklist.qa.util.TestContext;
import io.camunda.tasklist.qa.util.VariablesUtil;
import io.camunda.tasklist.qa.util.ZeebeTestUtil;
import io.camunda.tasklist.util.ThreadUtil;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class BigVariableProcessDataGenerator {
  public static final String BIG_VAR_NAME = "bigVar";
  public static final String PROCESS_BPMN_PROCESS_ID = "bigVariableProcess";
  public static final String SMALL_VAR_NAME = "smallVar";
  public static final String SMALL_VAR_VALUE = "\"smallVarValue\"";
  private static final Logger LOGGER =
      LoggerFactory.getLogger(BigVariableProcessDataGenerator.class);
  private ZeebeClient zeebeClient;

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  private final Random random = new Random();

  public BigVariableProcessDataGenerator() {}

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
      LOGGER.info("Starting generating data for process {}", "bigVariableProcess");
      deployProcess();
      startProcessInstance();
      waitUntilDataIsImported();

      try {
        esClient
            .indices()
            .refresh(new RefreshRequest(new String[] {"tasklist-*"}), RequestOptions.DEFAULT);
      } catch (final IOException e) {
        LOGGER.error("Error in refreshing indices", e);
      }

      LOGGER.info(
          "Data generation completed in: {} s",
          ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()));
      testContext.addProcess("bigVariableProcess");
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

  private void waitUntilDataIsImported() throws IOException {
    LOGGER.info("Wait till data is imported.");
    final SearchRequest searchRequest =
        (new SearchRequest(new String[] {getAliasFor("task")}))
            .source(
                (new SearchSourceBuilder())
                    .query(QueryBuilders.termQuery("bpmnProcessId", "bigVariableProcess")));
    long loadedProcessInstances = 0L;
    int count = 0;
    final int maxWait = 101;

    while (loadedProcessInstances < 1L && count < 101) {
      ++count;
      loadedProcessInstances = countEntitiesFor(searchRequest);
      ThreadUtil.sleepFor(1000L);
    }

    if (count == 101) {
      throw new RuntimeException("Waiting for loading process instances failed: Timeout");
    }
  }

  private Long startProcessInstance() {
    final String bpmnProcessId = "bigVariableProcess";
    final String payload =
        "{\"bigVar\": \""
            + VariablesUtil.createBigVariableWithSuffix(8191)
            + "\",\"smallVar\": \"smallVarValue\"}";
    final long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(zeebeClient, "bigVariableProcess", payload);
    LOGGER.debug(
        "Started processInstance {} for process {}", processInstanceKey, "bigVariableProcess");
    return processInstanceKey;
  }

  private void deployProcess() {
    final String bpmnProcessId = "bigVariableProcess";
    final String processDefinitionKey =
        ZeebeTestUtil.deployProcess(
            zeebeClient, createModel("bigVariableProcess"), "bigVariableProcess.bpmn");
    LOGGER.info("Deployed process {} with key {}", "bigVariableProcess", processDefinitionKey);
  }

  private BpmnModelInstance createModel(final String bpmnProcessId) {
    return ((ServiceTaskBuilder)
            Bpmn.createExecutableProcess(bpmnProcessId)
                .startEvent("start")
                .serviceTask("task1")
                .zeebeJobType("io.camunda.zeebe:userTask"))
        .endEvent()
        .done();
  }

  private long countEntitiesFor(final SearchRequest searchRequest) throws IOException {
    searchRequest.source().size(1000);
    final SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    return searchResponse.getHits().getTotalHits().value;
  }

  private String getAliasFor(final String index) {
    return String.format("tasklist-%s-*_alias", index);
  }
}
