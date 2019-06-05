/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.performance;

import java.time.Duration;
import java.time.Instant;
import org.camunda.operate.Application;
import org.camunda.operate.entities.OperationType;
import org.camunda.operate.es.schema.indices.WorkflowIndex;
import org.camunda.operate.es.writer.BatchOperationWriter;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.qa.util.ElasticsearchUtil;
import org.camunda.operate.rest.dto.listview.ListViewQueryDto;
import org.camunda.operate.rest.dto.operation.BatchOperationRequestDto;
import org.camunda.operate.rest.dto.operation.OperationResponseDto;
import org.camunda.operate.zeebe.operation.ExecutionFinishedListener;
import org.camunda.operate.zeebe.operation.OperationExecutor;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import io.zeebe.client.ZeebeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {Application.class})
public class BatchOperationPerformanceTest {

  private static final Logger logger = LoggerFactory.getLogger(BatchOperationPerformanceTest.class);
  public static final long ZEEBE_RESPONSE_TIME = 300L;

  private static long TEST_TIMEOUT_SECONDS = 60 * 60;   //1 hour

  @Autowired
  private OperateProperties operateProperties;
  @Autowired
  private BatchOperationWriter batchOperationWriter;
  @Autowired
  private RestHighLevelClient esClient;
  @Autowired
  private OperationExecutor operationExecutor;

  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  private ZeebeClient zeebeClient;

  @Before
  public void setup() {
    Answer answerWithDelay = invocation -> {
      Thread.sleep(ZEEBE_RESPONSE_TIME);
      return null;
    };
    when(zeebeClient.newCancelInstanceCommand(anyLong()).send().join()).thenAnswer(answerWithDelay);
    when(zeebeClient.newUpdateRetriesCommand(anyLong()).retries(1).send().join()).thenAnswer(answerWithDelay);
    when(zeebeClient.newResolveIncidentCommand(anyLong()).send().join()).thenAnswer(answerWithDelay);

    createOperations();
  }

  private void createOperations() {
    createResolveIncidentOperations();
    createCancelOperations();
  }

  private void createResolveIncidentOperations() {
    BatchOperationRequestDto cancelRequest = new BatchOperationRequestDto();
    cancelRequest.setOperationType(OperationType.RESOLVE_INCIDENT);
    ListViewQueryDto queryForCancel = new ListViewQueryDto();
    queryForCancel.setRunning(true);
    queryForCancel.setIncidents(true);
    queryForCancel.setWorkflowIds(ElasticsearchUtil.getWorkflowIds(esClient, getOperateAlias(WorkflowIndex.INDEX_NAME), 5));
    cancelRequest.getQueries().add(queryForCancel);
    final OperationResponseDto operationResponseDto = batchOperationWriter.scheduleBatchOperation(cancelRequest);
    logger.info("RESOLVE_INCIDENT operations scheduled: {}", operationResponseDto.getCount());
  }

  private void createCancelOperations() {
    BatchOperationRequestDto cancelRequest = new BatchOperationRequestDto();
    cancelRequest.setOperationType(OperationType.CANCEL_WORKFLOW_INSTANCE);
    ListViewQueryDto queryForCancel = new ListViewQueryDto();
    queryForCancel.setRunning(true);
    queryForCancel.setActive(true);
    queryForCancel.setWorkflowIds(ElasticsearchUtil.getWorkflowIds(esClient, getOperateAlias(WorkflowIndex.INDEX_NAME), 1));
    cancelRequest.getQueries().add(queryForCancel);
    final OperationResponseDto operationResponseDto = batchOperationWriter.scheduleBatchOperation(cancelRequest);
    logger.info("CANCEL_WORKFLOW_INSTANCE operations scheduled: {}", operationResponseDto.getCount());
  }

  @Test
  public void test() {
    BenchmarkingExecutionFinishedListener listener = new BenchmarkingExecutionFinishedListener();
    operationExecutor.registerListener(listener);

    final Instant start = Instant.now();
    listener.setOperationExecutionStart(start);

    operationExecutor.start();

    while (!listener.isFinished()) {
      try {
        Thread.sleep(2000L);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private String getOperateAlias(String indexName) {
    return String.format("%s-%s_alias", operateProperties.getElasticsearch().getIndexPrefix(), indexName);
  }

  private class BenchmarkingExecutionFinishedListener implements ExecutionFinishedListener {

    private Instant operationExecutionStart = Instant.now();

    private boolean finished = false;

    public void setOperationExecutionStart(Instant operationExecutionStart) {
      this.operationExecutionStart = operationExecutionStart;
    }

    @Override
    public void onExecutionFinished() {
      Instant operationExecutionEnd = Instant.now();
      long timeElapsed = Duration.between(operationExecutionStart, operationExecutionEnd).getSeconds();
      logger.info("Batch operation execution finished in {} s", timeElapsed);
      assertThat(timeElapsed).isLessThan(TEST_TIMEOUT_SECONDS);
      finished = true;
    }

    public boolean isFinished() {
      return finished;
    }
  }


}
