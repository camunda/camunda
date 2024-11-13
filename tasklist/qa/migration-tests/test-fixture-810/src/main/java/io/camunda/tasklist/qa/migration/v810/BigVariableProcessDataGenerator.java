/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.migration.v810;

import static io.camunda.tasklist.property.ImportProperties.DEFAULT_VARIABLE_SIZE_THRESHOLD;
import static io.camunda.tasklist.qa.util.VariablesUtil.createBigVariableWithSuffix;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.tasklist.qa.util.TestContext;
import io.camunda.tasklist.qa.util.ZeebeTestUtil;
import io.camunda.tasklist.schema.v86.templates.TaskTemplate;
import io.camunda.tasklist.util.ThreadUtil;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** It is considered that Zeebe and Elasticsearch are running. */
@Component
public class BigVariableProcessDataGenerator {

  public static final String BIG_VAR_NAME = "bigVar";
  public static final String PROCESS_BPMN_PROCESS_ID = "bigVariableProcess";
  public static final String SMALL_VAR_NAME = "smallVar";
  public static final String SMALL_VAR_VALUE = "\"smallVarValue\"";
  private static final Logger LOGGER =
      LoggerFactory.getLogger(BigVariableProcessDataGenerator.class);

  /**
   * ZeebeClient must not be reused between different test fixtures, as this may be different
   * versions of client in the future.
   */
  private ZeebeClient zeebeClient;

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  private final Random random = new Random();

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
      startProcessInstance();

      waitUntilDataIsImported();

      try {
        esClient.indices().refresh(new RefreshRequest("tasklist-*"), RequestOptions.DEFAULT);
      } catch (final IOException e) {
        LOGGER.error("Error in refreshing indices", e);
      }
      LOGGER.info(
          "Data generation completed in: {} s",
          ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()));
      testContext.addProcess(PROCESS_BPMN_PROCESS_ID);
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
        new SearchRequest(getAliasFor(TaskTemplate.INDEX_NAME))
            .source(
                new SearchSourceBuilder()
                    .query(termQuery(TaskTemplate.BPMN_PROCESS_ID, PROCESS_BPMN_PROCESS_ID)));
    long loadedProcessInstances = 0;
    int count = 0;
    final int maxWait = 101;
    while (loadedProcessInstances < 1 && count < maxWait) {
      count++;
      loadedProcessInstances = countEntitiesFor(searchRequest);
      ThreadUtil.sleepFor(1000L);
    }
    if (count == maxWait) {
      throw new RuntimeException("Waiting for loading process instances failed: Timeout");
    }
  }

  private Long startProcessInstance() {
    final String bpmnProcessId = PROCESS_BPMN_PROCESS_ID;
    final String payload =
        "{\""
            + BIG_VAR_NAME
            + "\": \""
            + createBigVariableWithSuffix(DEFAULT_VARIABLE_SIZE_THRESHOLD)
            + "\","
            + "\""
            + SMALL_VAR_NAME
            + "\": "
            + SMALL_VAR_VALUE
            + "}";

    final long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(zeebeClient, bpmnProcessId, payload);
    LOGGER.debug("Started processInstance {} for process {}", processInstanceKey, bpmnProcessId);
    return processInstanceKey;
  }

  private void deployProcess() {
    final String bpmnProcessId = PROCESS_BPMN_PROCESS_ID;
    final String processDefinitionKey =
        ZeebeTestUtil.deployProcess(
            zeebeClient, createModel(bpmnProcessId), bpmnProcessId + ".bpmn");
    LOGGER.info("Deployed process {} with key {}", bpmnProcessId, processDefinitionKey);
  }

  private BpmnModelInstance createModel(final String bpmnProcessId) {
    return Bpmn.createExecutableProcess(bpmnProcessId)
        .startEvent("start")
        .serviceTask("task1")
        .zeebeJobType("io.camunda.zeebe:userTask")
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
