/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.performance;

import io.camunda.operate.webapp.rest.dto.UserDto;
import java.time.Duration;
import java.time.Instant;
import io.camunda.operate.Application;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.ElasticsearchUtil;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.webapp.es.writer.BatchOperationWriter;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.security.UserService;
import io.camunda.operate.webapp.zeebe.operation.ExecutionFinishedListener;
import io.camunda.operate.webapp.zeebe.operation.OperationExecutor;
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
import io.camunda.zeebe.client.ZeebeClient;
import static org.assertj.core.api.Assertions.assertThat;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {Application.class})
public class BatchOperationPerformanceTest {

  private static final Logger logger = LoggerFactory.getLogger(BatchOperationPerformanceTest.class);
  public static final long ZEEBE_RESPONSE_TIME = 300L;

  private static long TEST_TIMEOUT_SECONDS = 60 * 60;   //1 hour

  protected static final String USERNAME = "testuser";

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

  @MockBean
  private UserService userService;

  @Before
  public void setup() {
    Answer answerWithDelay = invocation -> {
      sleepFor(ZEEBE_RESPONSE_TIME);
      return null;
    };
    when(zeebeClient.newCancelInstanceCommand(anyLong()).send().join()).thenAnswer(answerWithDelay);
    when(zeebeClient.newUpdateRetriesCommand(anyLong()).retries(1).send().join()).thenAnswer(answerWithDelay);
    when(zeebeClient.newResolveIncidentCommand(anyLong()).send().join()).thenAnswer(answerWithDelay);

    when(userService.getCurrentUser()).thenReturn(new UserDto().setUserId(USERNAME).setUserId(USERNAME));
    createOperations();
  }

  private void createOperations() {
    createResolveIncidentOperations();
    createCancelOperations();
  }

  private void createResolveIncidentOperations() {
    CreateBatchOperationRequestDto resolveIncidentRequest = new CreateBatchOperationRequestDto();
    resolveIncidentRequest.setOperationType(OperationType.RESOLVE_INCIDENT);
    ListViewQueryDto queryForResolveIncident = new ListViewQueryDto();
    queryForResolveIncident.setRunning(true);
    queryForResolveIncident.setIncidents(true);
    queryForResolveIncident.setProcessIds(ElasticsearchUtil.getProcessIds(esClient, getOperateAlias(ProcessIndex.INDEX_NAME), 5));
    resolveIncidentRequest.setQuery(queryForResolveIncident);
    final BatchOperationEntity batchOperationEntity = batchOperationWriter.scheduleBatchOperation(resolveIncidentRequest);
    logger.info("RESOLVE_INCIDENT operations scheduled: {}", batchOperationEntity.getOperationsTotalCount());
  }

  private void createCancelOperations() {
    CreateBatchOperationRequestDto cancelRequest = new CreateBatchOperationRequestDto();
    cancelRequest.setOperationType(OperationType.CANCEL_PROCESS_INSTANCE);
    ListViewQueryDto queryForCancel = new ListViewQueryDto();
    queryForCancel.setRunning(true);
    queryForCancel.setActive(true);
    queryForCancel.setProcessIds(ElasticsearchUtil.getProcessIds(esClient, getOperateAlias(ProcessIndex.INDEX_NAME), 1));
    cancelRequest.setQuery(queryForCancel);
    final BatchOperationEntity batchOperationEntity = batchOperationWriter.scheduleBatchOperation(cancelRequest);
    logger.info("CANCEL_PROCESS_INSTANCE operations scheduled: {}", batchOperationEntity.getOperationsTotalCount());
  }

  @Test
  public void test() {
    BenchmarkingExecutionFinishedListener listener = new BenchmarkingExecutionFinishedListener();
    operationExecutor.registerListener(listener);

    final Instant start = Instant.now();
    listener.setOperationExecutionStart(start);

    operationExecutor.start();

    while (!listener.isFinished()) {
      sleepFor(2000);
    }
  }

  private String getOperateAlias(String indexName) {
    return String.format("%s-%s-*_alias", operateProperties.getElasticsearch().getIndexPrefix(), indexName);
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
