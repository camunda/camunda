/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.migration.util;

import io.camunda.tasklist.qa.util.TestContext;
import io.camunda.tasklist.qa.util.ZeebeTestUtil;
import io.camunda.tasklist.util.ThreadUtil;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ServiceTaskBuilder;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.UpdateByQueryRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class BasicProcessDataGenerator {
  public static final String PROCESS_BPMN_PROCESS_ID = "basicProcess";
  public static final int PROCESS_INSTANCE_COUNT = 51;
  private static final Logger LOGGER = LoggerFactory.getLogger(BasicProcessDataGenerator.class);
  private ZeebeClient zeebeClient;

  @Autowired
  @Qualifier("tasklistEsClient")
  private RestHighLevelClient esClient;

  private final Random random = new Random();
  private List<Long> processInstanceKeys = new ArrayList();

  public BasicProcessDataGenerator() {}

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
      LOGGER.info("Starting generating data for process {}", "basicProcess");
      deployProcess();
      processInstanceKeys = startProcessInstances(51);
      waitUntilAllDataAreImported();
      claimAllTasks();

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
      testContext.addProcess("basicProcess");
    } finally {
      closeClients();
    }
  }

  private void claimAllTasks() {
    final UpdateByQueryRequest updateRequest =
        (UpdateByQueryRequest)
            ((UpdateByQueryRequest)
                    (new UpdateByQueryRequest(new String[] {getMainIndexNameFor("task")}))
                        .setQuery(QueryBuilders.matchAllQuery())
                        .setScript(
                            new Script(
                                ScriptType.INLINE,
                                "painless",
                                "ctx._source.assignee = 'demo'",
                                Collections.emptyMap())))
                .setRefresh(true);

    try {
      esClient.updateByQuery(updateRequest, RequestOptions.DEFAULT);
    } catch (final IOException | ElasticsearchException e) {
      throw new RuntimeException(e);
    }
  }

  private void closeClients() {
    if (zeebeClient != null) {
      zeebeClient.close();
      zeebeClient = null;
    }
  }

  private void waitUntilAllDataAreImported() throws IOException {
    LOGGER.info("Wait till data is imported.");
    final SearchRequest searchRequest = new SearchRequest(new String[] {getAliasFor("task")});
    long loadedProcessInstances = 0L;
    int count = 0;
    final int maxWait = 101;

    while (51L > loadedProcessInstances && count < 101) {
      ++count;
      loadedProcessInstances = countEntitiesFor(searchRequest);
      ThreadUtil.sleepFor(1000L);
    }

    if (count == 101) {
      throw new RuntimeException("Waiting for loading process instances failed: Timeout");
    }
  }

  private List<Long> startProcessInstances(final int numberOfProcessInstances) {
    for (int i = 0; i < numberOfProcessInstances; ++i) {
      final String bpmnProcessId = "basicProcess";
      final long processInstanceKey =
          ZeebeTestUtil.startProcessInstance(zeebeClient, "basicProcess", "{\"var1\": \"value1\"}");
      LOGGER.debug("Started processInstance {} for process {}", processInstanceKey, "basicProcess");
      processInstanceKeys.add(processInstanceKey);
    }

    LOGGER.info("{} processInstances started", processInstanceKeys.size());
    return processInstanceKeys;
  }

  private void deployProcess() {
    final String bpmnProcessId = "basicProcess";
    final String processDefinitionKey =
        ZeebeTestUtil.deployProcess(zeebeClient, createModel("basicProcess"), "basicProcess.bpmn");
    LOGGER.info("Deployed process {} with key {}", "basicProcess", processDefinitionKey);
  }

  private BpmnModelInstance createModel(final String bpmnProcessId) {
    return ((ServiceTaskBuilder)
            ((ServiceTaskBuilder)
                    ((ServiceTaskBuilder)
                            ((ServiceTaskBuilder)
                                    ((ServiceTaskBuilder)
                                            ((ServiceTaskBuilder)
                                                    ((ServiceTaskBuilder)
                                                            Bpmn.createExecutableProcess(
                                                                    bpmnProcessId)
                                                                .startEvent("start")
                                                                .serviceTask("task1")
                                                                .zeebeJobType(
                                                                    "io.camunda.zeebe:userTask"))
                                                        .zeebeInput("=var1", "varIn"))
                                                .zeebeOutput("=varOut", "var2"))
                                        .serviceTask("task2")
                                        .zeebeJobType("task2"))
                                .serviceTask("task3")
                                .zeebeJobType("task3"))
                        .serviceTask("task4")
                        .zeebeJobType("task4"))
                .serviceTask("task5")
                .zeebeJobType("task5"))
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

  private String getMainIndexNameFor(final String index) {
    return String.format("tasklist-%s-*_", index);
  }
}
