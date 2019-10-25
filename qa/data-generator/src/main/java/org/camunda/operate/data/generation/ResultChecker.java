/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.data.generation;

import java.io.IOException;
import java.util.function.Supplier;
import org.camunda.operate.qa.util.ElasticsearchUtil;
import org.camunda.operate.zeebe.ZeebeESConstants;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static java.lang.Math.abs;

@Component
public class ResultChecker {

  private static final Logger logger = LoggerFactory.getLogger(DataGenerator.class);

  private static final double PRECISION_RATE = 0.01;

  @Autowired
  private DataGeneratorProperties dataGeneratorProperties;

  @Autowired
  private RestHighLevelClient esClient;

  public void assertResults() throws IOException {
    sleep(1000L);
    esClient.indices().refresh(new RefreshRequest(), RequestOptions.DEFAULT);
    assertWorkflowCount();
    assertWorkflowInstanceCount();
//    assertIncidentCount();
    logger.info("Assertions passed");
  }

  private boolean assertDocsCountWithRetry(int lastCount, int expectedCount, Supplier<Integer> cardinalityCounter) {
    int newCount = cardinalityCounter.get();
    logger.info("Asserting amount: {}", newCount);
    if (((double)abs(newCount - expectedCount)) / expectedCount <= PRECISION_RATE) {
      return true;
    } else if (newCount > lastCount){   //data is still loading
      logger.info("Not enough. Will wait and retry");
      try {
        esClient.indices().refresh(new RefreshRequest(), RequestOptions.DEFAULT);
      } catch (IOException e) {
        //
      }
      sleep(2000L);
      return assertDocsCountWithRetry(newCount, expectedCount, cardinalityCounter);
    } else {
      logger.info("Not enough. Will fail");
      return false;
    }
  }

  private void sleep(long time) {
    try {
      Thread.sleep(time);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void assertWorkflowCount() {
    final int expectedCount = dataGeneratorProperties.getWorkflowCount();
    final Supplier<Integer> workflowCounter = () -> {
      try {
        return ElasticsearchUtil.getFieldCardinality(esClient, getAliasName(ZeebeESConstants.DEPLOYMENT_INDEX_NAME), "value.deployedWorkflows.bpmnProcessId");
      } catch (IOException e) {
        throw new DataGenerationException("Exception occurred while performing assertions", e);
      }
    };
    if (!assertDocsCountWithRetry(0, expectedCount, workflowCounter)) {
      throw new DataGenerationException(String.format("Expected to have %s workflows, but was %s", expectedCount, workflowCounter.get()));
    }
  }

  private void assertWorkflowInstanceCount() {
    final int expectedCount = dataGeneratorProperties.getWorkflowInstanceCount();
    final Supplier<Integer> workflowCounter = () -> {
      try {
        return ElasticsearchUtil.getFieldCardinality(esClient, getAliasName(ZeebeESConstants.WORKFLOW_INSTANCE_INDEX_NAME), "value.workflowInstanceKey");
      } catch (IOException e) {
        throw new DataGenerationException("Exception occurred while performing assertions", e);
      }
    };
    if (!assertDocsCountWithRetry(0, expectedCount, workflowCounter)) {
      throw new DataGenerationException(String.format("Expected to have %s workflow instances, but was %s", expectedCount, workflowCounter.get()));
    }
  }

//  private void assertIncidentCount() {
//    try {
//      int workflowInstanceCount = getFieldCardinality(getAliasName(INCIDENT_INDEX_NAME), "value.workflowInstanceKey");
//      final int expectedWorkflowInstanceCount = dataGeneratorProperties.getWorkflowInstanceCount();
//      if (workflowInstanceCount != expectedWorkflowInstanceCount) {
//        throw new DataGenerationException(String.format("Expected to have %s workflow instances, but was %s", expectedWorkflowInstanceCount, workflowInstanceCount));
//      }
//    } catch (IOException ex) {
//      throw new DataGenerationException("Exception occurred while performing assertions", ex);
//    }
//  }

  public String getAliasName(String name) {
    return String.format("%s-%s", dataGeneratorProperties.getZeebeElasticsearchPrefix(), name);
  }

}
