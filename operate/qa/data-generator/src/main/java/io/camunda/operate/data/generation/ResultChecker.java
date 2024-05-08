/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.data.generation;

import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static java.lang.Math.abs;

import io.camunda.operate.qa.util.ElasticsearchUtil;
import io.camunda.operate.zeebe.ZeebeESConstants;
import java.io.IOException;
import java.util.function.Supplier;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ResultChecker {

  private static final Logger LOGGER = LoggerFactory.getLogger(ResultChecker.class);

  private static final double PRECISION_RATE = 0.01;

  @Autowired private DataGeneratorProperties dataGeneratorProperties;

  @Autowired private RestHighLevelClient esClient;

  public void assertResults() throws IOException {
    sleepFor(1000L);
    esClient.indices().refresh(new RefreshRequest(), RequestOptions.DEFAULT);
    assertProcessCount();
    assertProcessInstanceCount();
    //    assertIncidentCount();
    LOGGER.info("Assertions passed");
  }

  private boolean assertDocsCountWithRetry(
      int lastCount, int expectedCount, Supplier<Integer> cardinalityCounter) {
    final int newCount = cardinalityCounter.get();
    LOGGER.info("Asserting amount: {}", newCount);
    if (((double) abs(newCount - expectedCount)) / expectedCount <= PRECISION_RATE) {
      return true;
    } else if (newCount > lastCount) { // data is still loading
      LOGGER.info("Not enough. Will wait and retry");
      try {
        esClient.indices().refresh(new RefreshRequest(), RequestOptions.DEFAULT);
      } catch (IOException e) {
        // noop
      }
      sleepFor(5000L);
      return assertDocsCountWithRetry(newCount, expectedCount, cardinalityCounter);
    } else {
      LOGGER.info("Not enough. Will fail");
      return false;
    }
  }

  private void assertProcessCount() {
    final int expectedCount = dataGeneratorProperties.getProcessCount() + 3;
    final Supplier<Integer> processCounter =
        () -> {
          try {
            return ElasticsearchUtil.getFieldCardinality(
                esClient, getAliasName(ZeebeESConstants.PROCESS_INDEX_NAME), "value.bpmnProcessId");
          } catch (IOException e) {
            throw new DataGenerationException("Exception occurred while performing assertions", e);
          }
        };
    if (!assertDocsCountWithRetry(0, expectedCount, processCounter)) {
      throw new DataGenerationException(
          String.format(
              "Expected to have %s processes, but was %s", expectedCount, processCounter.get()));
    }
  }

  private void assertProcessInstanceCount() {
    final int expectedCount =
        dataGeneratorProperties.getProcessInstanceCount()
            + dataGeneratorProperties.getCallActivityProcessInstanceCount() * 3
            + 1;
    final Supplier<Integer> processCounter =
        () -> {
          try {
            return ElasticsearchUtil.getFieldCardinality(
                esClient,
                getAliasName(ZeebeESConstants.PROCESS_INSTANCE_INDEX_NAME),
                "value.processInstanceKey");
          } catch (IOException e) {
            throw new DataGenerationException("Exception occurred while performing assertions", e);
          }
        };
    if (!assertDocsCountWithRetry(0, expectedCount, processCounter)) {
      throw new DataGenerationException(
          String.format(
              "Expected to have %s process instances, but was %s",
              expectedCount, processCounter.get()));
    }
  }

  //  private void assertIncidentCount() {
  //    try {
  //      int processInstanceCount = getFieldCardinality(getAliasName(INCIDENT_INDEX_NAME),
  // "value.processInstanceKey");
  //      final int expectedProcessInstanceCount =
  // dataGeneratorProperties.getProcessInstanceCount();
  //      if (processInstanceCount != expectedProcessInstanceCount) {
  //        throw new DataGenerationException(String.format("Expected to have %s process instances,
  // but was %s", expectedProcessInstanceCount, processInstanceCount));
  //      }
  //    } catch (IOException ex) {
  //      throw new DataGenerationException("Exception occurred while performing assertions", ex);
  //    }
  //  }

  public String getAliasName(String name) {
    return String.format("%s-%s", dataGeneratorProperties.getZeebeElasticsearchPrefix(), name);
  }
}
