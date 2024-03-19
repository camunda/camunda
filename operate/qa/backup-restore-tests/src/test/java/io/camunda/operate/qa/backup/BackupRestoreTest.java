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
package io.camunda.operate.qa.backup;

import static io.camunda.operate.util.CollectionUtil.asMap;
import static io.camunda.operate.webapp.management.dto.BackupStateDto.COMPLETED;
import static io.camunda.operate.webapp.management.dto.BackupStateDto.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.operate.util.RetryOperation;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.operate.webapp.management.dto.TakeBackupResponseDto;
import io.camunda.zeebe.client.ZeebeClient;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryRequest;
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.repositories.fs.FsRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {TestConfig.class})
public class BackupRestoreTest {

  public static final String ZEEBE_INDEX_PREFIX = "backup-restore-test";
  public static final String VERSION = "SNAPSHOT";
  public static final String REPOSITORY_NAME = "testRepository";
  public static final Long BACKUP_ID = 123L;
  private static final Logger LOGGER = LoggerFactory.getLogger(BackupRestoreTest.class);
  private static final String OPERATE_TEST_DOCKER_IMAGE = "camunda/operate";
  @Autowired private OperateAPICaller operateAPICaller;

  @Autowired private DataGenerator dataGenerator;

  private GenericContainer operateContainer;

  private TestContainerUtil testContainerUtil = new TestContainerUtil();
  private BackupRestoreTestContext testContext;
  private List<String> snapshots;

  @Before
  public void setup() {
    testContext = new BackupRestoreTestContext().setZeebeIndexPrefix(ZEEBE_INDEX_PREFIX);
  }

  @Test
  public void testBackupRestore() throws Exception {
    startAllApps();
    dataGenerator.createData(testContext);
    dataGenerator.assertData();
    createBackup();
    dataGenerator.changeData();
    dataGenerator.assertDataAfterChange();
    stopOperate();
    deleteOperateIndices();
    restoreBackup();
    startOperate();
    dataGenerator.assertData();
  }

  private void deleteOperateIndices() throws Exception {
    try {
      testContext
          .getEsClient()
          .indices()
          .delete(new DeleteIndexRequest("operate*"), RequestOptions.DEFAULT);
      // we need to remove Zeebe indices as otherwise Operate will start importing data at once and
      // we won't be able to assert the older state of data (from backup)
      testContext
          .getEsClient()
          .indices()
          .delete(new DeleteIndexRequest(ZEEBE_INDEX_PREFIX + "*"), RequestOptions.DEFAULT);
      RetryOperation.newBuilder()
          .noOfRetry(10)
          .delayInterval(2000, TimeUnit.MILLISECONDS)
          .retryPredicate(result -> !(boolean) result)
          .retryConsumer(
              () ->
                  !testContext
                      .getEsClient()
                      .indices()
                      .exists(
                          new GetIndexRequest("operate*", ZEEBE_INDEX_PREFIX + "*"),
                          RequestOptions.DEFAULT))
          .build()
          .retry();
      LOGGER.info("************ Operate indices deleted ************");
    } catch (IOException e) {
      throw new OperateRuntimeException(
          "Exception occurred while removing Operate and Zeebe indices: " + e.getMessage(), e);
    }
  }

  private void createBackup() throws Exception {
    final TakeBackupResponseDto backupResponse = operateAPICaller.backup(BACKUP_ID);
    snapshots = backupResponse.getScheduledSnapshots();

    RetryOperation.newBuilder()
        .noOfRetry(10)
        .delayInterval(2000, TimeUnit.MILLISECONDS)
        .retryPredicate(result -> !(boolean) result)
        .retryConsumer(
            () -> {
              final GetBackupStateResponseDto backupState =
                  operateAPICaller.getBackupState(BACKUP_ID);
              assertThat(backupState.getState()).isIn(IN_PROGRESS, COMPLETED);
              return backupState.getState().equals(COMPLETED);
            })
        .build()
        .retry();
    LOGGER.info("************ Backup created ************");
  }

  private void startAllApps() throws IOException {
    testContainerUtil.startElasticsearch(testContext);
    testContainerUtil.checkElasctisearchHealth(testContext);
    testContext.setEsClient(
        new RestHighLevelClient(
            RestClient.builder(
                new HttpHost(testContext.getExternalElsHost(), testContext.getExternalElsPort()))));
    createSnapshotRepository(testContext);

    String zeebeVersion = ZeebeClient.class.getPackage().getImplementationVersion();
    if (zeebeVersion.toLowerCase().contains("snapshot")) {
      zeebeVersion = "SNAPSHOT";
    }
    testContainerUtil.startZeebe(zeebeVersion, testContext);

    operateContainer =
        testContainerUtil
            .createOperateContainer(OPERATE_TEST_DOCKER_IMAGE, VERSION, testContext)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER));
    operateContainer.withEnv("CAMUNDA_OPERATE_BACKUP_REPOSITORYNAME", REPOSITORY_NAME);

    startOperate();
  }

  private void startOperate() {
    LOGGER.info("************ Starting Operate ************");
    testContainerUtil.startOperateContainer(operateContainer, testContext);
    LOGGER.info("************ Operate started  ************");
    testContext.setOperateRestClient(operateAPICaller.createRestTemplate(testContext));
  }

  private void stopOperate() {
    operateContainer.stop();
    LOGGER.info("************ Operate stopped  ************");
  }

  private void restoreBackup() {
    snapshots.stream()
        .forEach(
            snapshot -> {
              try {
                testContext
                    .getEsClient()
                    .snapshot()
                    .restore(
                        new RestoreSnapshotRequest(REPOSITORY_NAME, snapshot)
                            .waitForCompletion(true),
                        RequestOptions.DEFAULT);
              } catch (IOException e) {
                throw new OperateRuntimeException(
                    "Exception occurred while restoring the backup: " + e.getMessage(), e);
              }
            });
    LOGGER.info("************ Backup restored ************");
  }

  private void createSnapshotRepository(BackupRestoreTestContext testContext) throws IOException {
    testContext
        .getEsClient()
        .snapshot()
        .createRepository(
            new PutRepositoryRequest(REPOSITORY_NAME)
                .type(FsRepository.TYPE)
                .settings(asMap("location", REPOSITORY_NAME)),
            RequestOptions.DEFAULT);
  }
}

@Configuration
@ComponentScan(
    basePackages = {
      "io.camunda.operate.util.rest",
      "io.camunda.operate.qa.backup",
      "io.camunda.operate.webapp.rest.dto"
    })
class TestConfig {}
