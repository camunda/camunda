/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.qa.performance;

import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import io.camunda.operate.StandaloneOperate;
import io.camunda.operate.entities.BatchOperationEntity;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.ElasticsearchUtil;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.webapp.rest.dto.UserDto;
import io.camunda.operate.webapp.rest.dto.listview.ListViewQueryDto;
import io.camunda.operate.webapp.rest.dto.operation.CreateBatchOperationRequestDto;
import io.camunda.operate.webapp.security.UserService;
import io.camunda.operate.webapp.writer.BatchOperationWriter;
import io.camunda.operate.webapp.zeebe.operation.ExecutionFinishedListener;
import io.camunda.operate.webapp.zeebe.operation.OperationExecutor;
import io.camunda.zeebe.client.ZeebeClient;
import java.time.Duration;
import java.time.Instant;
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

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {StandaloneOperate.class},
    properties = {"spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER"})
public class BatchOperationPerformanceIT {

  public static final long ZEEBE_RESPONSE_TIME = 300L;
  protected static final String USERNAME = "testuser";
  private static final Logger LOGGER = LoggerFactory.getLogger(BatchOperationPerformanceIT.class);
  private static final long TEST_TIMEOUT_SECONDS = 60 * 60; // 1 hour
  @Autowired private OperateProperties operateProperties;
  @Autowired private BatchOperationWriter batchOperationWriter;
  @Autowired private RestHighLevelClient esClient;
  @Autowired private OperationExecutor operationExecutor;

  @MockBean(answer = Answers.RETURNS_DEEP_STUBS)
  private ZeebeClient zeebeClient;

  @MockBean private UserService userService;

  @Before
  public void setup() {
    final Answer answerWithDelay =
        invocation -> {
          sleepFor(ZEEBE_RESPONSE_TIME);
          return null;
        };
    when(zeebeClient.newCancelInstanceCommand(anyLong()).send().join()).thenAnswer(answerWithDelay);
    when(zeebeClient.newUpdateRetriesCommand(anyLong()).retries(1).send().join())
        .thenAnswer(answerWithDelay);
    when(zeebeClient.newResolveIncidentCommand(anyLong()).send().join())
        .thenAnswer(answerWithDelay);

    when(userService.getCurrentUser())
        .thenReturn(new UserDto().setUserId(USERNAME).setUserId(USERNAME));
    createOperations();
  }

  private void createOperations() {
    createResolveIncidentOperations();
    createCancelOperations();
  }

  private void createResolveIncidentOperations() {
    final CreateBatchOperationRequestDto resolveIncidentRequest =
        new CreateBatchOperationRequestDto();
    resolveIncidentRequest.setOperationType(OperationType.RESOLVE_INCIDENT);
    final ListViewQueryDto queryForResolveIncident = new ListViewQueryDto();
    queryForResolveIncident.setRunning(true);
    queryForResolveIncident.setIncidents(true);
    queryForResolveIncident.setProcessIds(
        ElasticsearchUtil.getProcessIds(esClient, getOperateAlias(ProcessIndex.INDEX_NAME), 5));
    resolveIncidentRequest.setQuery(queryForResolveIncident);
    final BatchOperationEntity batchOperationEntity =
        batchOperationWriter.scheduleBatchOperation(resolveIncidentRequest);
    LOGGER.info(
        "RESOLVE_INCIDENT operations scheduled: {}",
        batchOperationEntity.getOperationsTotalCount());
  }

  private void createCancelOperations() {
    final CreateBatchOperationRequestDto cancelRequest = new CreateBatchOperationRequestDto();
    cancelRequest.setOperationType(OperationType.CANCEL_PROCESS_INSTANCE);
    final ListViewQueryDto queryForCancel = new ListViewQueryDto();
    queryForCancel.setRunning(true);
    queryForCancel.setActive(true);
    queryForCancel.setProcessIds(
        ElasticsearchUtil.getProcessIds(esClient, getOperateAlias(ProcessIndex.INDEX_NAME), 1));
    cancelRequest.setQuery(queryForCancel);
    final BatchOperationEntity batchOperationEntity =
        batchOperationWriter.scheduleBatchOperation(cancelRequest);
    LOGGER.info(
        "CANCEL_PROCESS_INSTANCE operations scheduled: {}",
        batchOperationEntity.getOperationsTotalCount());
  }

  @Test
  public void test() {
    final BenchmarkingExecutionFinishedListener listener =
        new BenchmarkingExecutionFinishedListener();
    operationExecutor.registerListener(listener);

    final Instant start = Instant.now();
    listener.setOperationExecutionStart(start);

    operationExecutor.start();

    while (!listener.isFinished()) {
      sleepFor(2000);
    }
  }

  private String getOperateAlias(String indexName) {
    return String.format(
        "%s-%s-*_alias", operateProperties.getElasticsearch().getIndexPrefix(), indexName);
  }

  private final class BenchmarkingExecutionFinishedListener implements ExecutionFinishedListener {

    private Instant operationExecutionStart = Instant.now();

    private boolean finished = false;

    public void setOperationExecutionStart(Instant operationExecutionStart) {
      this.operationExecutionStart = operationExecutionStart;
    }

    @Override
    public void onExecutionFinished() {
      final Instant operationExecutionEnd = Instant.now();
      final long timeElapsed =
          Duration.between(operationExecutionStart, operationExecutionEnd).getSeconds();
      LOGGER.info("Batch operation execution finished in {} s", timeElapsed);
      assertThat(timeElapsed).isLessThan(TEST_TIMEOUT_SECONDS);
      finished = true;
    }

    public boolean isFinished() {
      return finished;
    }
  }
}
