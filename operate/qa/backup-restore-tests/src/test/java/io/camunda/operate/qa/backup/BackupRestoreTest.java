/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.qa.backup;

import static io.camunda.operate.qa.util.ContainerVersionsUtil.ZEEBE_CURRENTVERSION_PROPERTY_NAME;
import static io.camunda.operate.schema.SchemaManager.OPERATE_DELETE_ARCHIVED_INDICES;
import static io.camunda.operate.util.CollectionUtil.asMap;
import static io.camunda.operate.webapp.management.dto.BackupStateDto.COMPLETED;
import static io.camunda.operate.webapp.management.dto.BackupStateDto.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.qa.util.ContainerVersionsUtil;
import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.operate.schema.templates.TemplateDescriptor;
import io.camunda.operate.util.RetryOperation;
import io.camunda.operate.webapp.management.dto.GetBackupStateResponseDto;
import io.camunda.operate.webapp.management.dto.TakeBackupResponseDto;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpHost;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryRequest;
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indexlifecycle.DeleteLifecyclePolicyRequest;
import org.elasticsearch.client.indexlifecycle.GetLifecyclePolicyRequest;
import org.elasticsearch.client.indices.DeleteComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.GetComponentTemplatesRequest;
import org.elasticsearch.client.indices.GetComposableIndexTemplateRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.repositories.fs.FsRepository;
import org.junit.After;
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
  public static final String VERSION = "current-test";
  public static final String REPOSITORY_NAME = "testRepository";
  public static final Long BACKUP_ID = 123L;
  public static final String OPERATE_TEMPLATE = "operate_template";
  private static final Logger LOGGER = LoggerFactory.getLogger(BackupRestoreTest.class);
  private static final String OPERATE_TEST_DOCKER_IMAGE = "localhost:5000/camunda/operate";
  @Autowired private OperateAPICaller operateAPICaller;

  @Autowired private DataGenerator dataGenerator;

  @Autowired private List<TemplateDescriptor> indexTemplateDescriptors;

  private GenericContainer operateContainer;

  private final TestContainerUtil testContainerUtil = new TestContainerUtil();
  private BackupRestoreTestContext testContext;
  private List<String> snapshots;

  @Before
  public void setup() {
    testContext = new BackupRestoreTestContext().setZeebeIndexPrefix(ZEEBE_INDEX_PREFIX);
  }

  @After
  public void tearDown() {
    // Each test method runs its own set of containers; stop them so the two scenarios don't run
    // their (heavyweight) Elasticsearch/Zeebe/Operate containers side by side. Stopping is best
    // effort: a failure here must not mask the actual test outcome.
    stopQuietly("Operate", () -> stopOperate());
    stopQuietly("Zeebe", () -> testContainerUtil.stopZeebe((File) null));
    stopQuietly("Elasticsearch", () -> testContainerUtil.stopElasticsearch());
  }

  /**
   * The global cluster state carries the index templates, the component template and the ILM
   * policy. Restoring a snapshot <em>with</em> global state therefore brings them all back, whereas
   * restoring <em>without</em> global state only brings back the indices, leaving the templates and
   * ILM policy to be recreated by Operate on startup
   * (https://github.com/camunda/camunda/issues/32806).
   */
  @Test
  public void testBackupRestoreWithGlobalState() throws Exception {
    backupRestore(true);
  }

  @Test
  public void testBackupRestoreWithoutGlobalState() throws Exception {
    backupRestore(false);
  }

  private void backupRestore(final boolean restoreGlobalState) throws Exception {
    startAllApps();
    dataGenerator.createData(testContext);
    dataGenerator.assertData();
    createBackup();
    dataGenerator.changeData();
    dataGenerator.assertDataAfterChange();
    stopOperate();
    deleteOperateIndices();
    deleteComponentTemplate();
    deleteIlmPolicy();
    restoreBackup(restoreGlobalState);

    if (restoreGlobalState) {
      // The global cluster state includes the index templates, the component template and the ILM
      // policy, so restoring it brings them all back as part of the snapshot restore.
      assertIndexTemplatesPresent();
      assertComponentTemplatePresent();
      assertIlmPolicyPresent();
    } else {
      // Index templates and ILM policies live in the global state, not in the index snapshots.
      // When the global state is not restored they stay missing until Operate recreates them on
      // startup (https://github.com/camunda/camunda/issues/32806).
      assertIndexTemplatesAbsent();
      assertComponentTemplateAbsent();
      assertIlmPolicyAbsent();
    }

    startOperate();

    // In both cases the schema must be complete once Operate has started: either the templates and
    // ILM policy were restored from the global state, or they were recreated on schema startup.
    assertIndexTemplatesPresent();
    assertComponentTemplatePresent();
    assertIlmPolicyPresent();

    dataGenerator.assertData();
  }

  private void assertIndexTemplatesPresent() throws IOException {
    for (final TemplateDescriptor descriptor : indexTemplateDescriptors) {
      final String templateName = descriptor.getTemplateName();
      assertThat(indexTemplateExists(templateName))
          .as("Index template '%s' should be present", templateName)
          .isTrue();
    }
    LOGGER.info("************ Index templates present ************");
  }

  private void assertIndexTemplatesAbsent() throws IOException {
    for (final TemplateDescriptor descriptor : indexTemplateDescriptors) {
      final String templateName = descriptor.getTemplateName();
      assertThat(indexTemplateExists(templateName))
          .as("Index template '%s' should be absent before Operate startup", templateName)
          .isFalse();
    }
    LOGGER.info("************ Index templates absent ************");
  }

  private boolean indexTemplateExists(final String templateName) throws IOException {
    try {
      return !testContext
          .getEsClient()
          .indices()
          .getIndexTemplate(
              new GetComposableIndexTemplateRequest(templateName), RequestOptions.DEFAULT)
          .getIndexTemplates()
          .isEmpty();
    } catch (final ElasticsearchStatusException e) {
      if (e.status().getStatus() == 404) {
        return false;
      }
      throw e;
    }
  }

  private void deleteComponentTemplate() throws Exception {
    final var request =
        new org.elasticsearch.client.Request("DELETE", "/_component_template/" + OPERATE_TEMPLATE);
    testContext.getEsClient().getLowLevelClient().performRequest(request);
    RetryOperation.newBuilder()
        .noOfRetry(10)
        .delayInterval(2000, TimeUnit.MILLISECONDS)
        .retryPredicate(result -> !(boolean) result)
        .retryConsumer(() -> !componentTemplateExists())
        .build()
        .retry();
    LOGGER.info("************ Component template deleted ************");
  }

  private void assertComponentTemplatePresent() throws IOException {
    assertThat(componentTemplateExists())
        .as("Component template '%s' should be present", OPERATE_TEMPLATE)
        .isTrue();
    LOGGER.info("************ Component template present ************");
  }

  private void assertComponentTemplateAbsent() throws IOException {
    assertThat(componentTemplateExists())
        .as("Component template '%s' should be absent before Operate startup", OPERATE_TEMPLATE)
        .isFalse();
    LOGGER.info("************ Component template absent ************");
  }

  private boolean componentTemplateExists() throws IOException {
    try {
      return testContext
              .getEsClient()
              .cluster()
              .getComponentTemplate(
                  new GetComponentTemplatesRequest(OPERATE_TEMPLATE), RequestOptions.DEFAULT)
              .getComponentTemplates()
              .get(OPERATE_TEMPLATE)
          != null;
    } catch (final ElasticsearchStatusException e) {
      if (e.status().getStatus() == 404) {
        return false;
      }
      throw e;
    }
  }

  private void deleteIlmPolicy() throws Exception {
    testContext
        .getEsClient()
        .indexLifecycle()
        .deleteLifecyclePolicy(
            new DeleteLifecyclePolicyRequest(OPERATE_DELETE_ARCHIVED_INDICES),
            RequestOptions.DEFAULT);
    RetryOperation.newBuilder()
        .noOfRetry(10)
        .delayInterval(2000, TimeUnit.MILLISECONDS)
        .retryPredicate(result -> !(boolean) result)
        .retryConsumer(() -> !ilmPolicyExists())
        .build()
        .retry();
    LOGGER.info("************ ILM policy deleted ************");
  }

  private void assertIlmPolicyPresent() throws IOException {
    assertThat(ilmPolicyExists())
        .as("ILM policy '%s' should be present", OPERATE_DELETE_ARCHIVED_INDICES)
        .isTrue();
    LOGGER.info("************ ILM policy present ************");
  }

  private void assertIlmPolicyAbsent() throws IOException {
    assertThat(ilmPolicyExists())
        .as(
            "ILM policy '%s' should be absent before Operate startup",
            OPERATE_DELETE_ARCHIVED_INDICES)
        .isFalse();
    LOGGER.info("************ ILM policy absent ************");
  }

  private boolean ilmPolicyExists() throws IOException {
    try {
      return testContext
              .getEsClient()
              .indexLifecycle()
              .getLifecyclePolicy(
                  new GetLifecyclePolicyRequest(OPERATE_DELETE_ARCHIVED_INDICES),
                  RequestOptions.DEFAULT)
              .getPolicies()
              .get(OPERATE_DELETE_ARCHIVED_INDICES)
          != null;
    } catch (final ElasticsearchStatusException e) {
      if (e.status().getStatus() == 404) {
        return false;
      }
      throw e;
    }
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
      // indices().exists() on a wildcard always returns true, even with no matches - the
      // client hardcodes allow_no_indices=true on that request regardless of IndicesOptions.
      // Listing the indices and checking for emptiness is the only way to tell "gone" apart
      // from "still there".
      final boolean indicesGone =
          RetryOperation.<Boolean>newBuilder()
              .noOfRetry(10)
              .delayInterval(2000, TimeUnit.MILLISECONDS)
              .retryPredicate(result -> !result)
              .retryConsumer(
                  () ->
                      testContext
                              .getEsClient()
                              .indices()
                              .get(
                                  new GetIndexRequest("operate*", ZEEBE_INDEX_PREFIX + "*"),
                                  RequestOptions.DEFAULT)
                              .getIndices()
                              .length
                          == 0)
              .build()
              .retry();
      if (!indicesGone) {
        throw new OperateRuntimeException(
            "Operate/Zeebe indices matching 'operate*' or '"
                + ZEEBE_INDEX_PREFIX
                + "*' still exist after waiting for deletion");
      }
      testContext
          .getEsClient()
          .indices()
          .deleteIndexTemplate(
              new DeleteComposableIndexTemplateRequest("operate*"), RequestOptions.DEFAULT);
      final boolean templatesGone =
          RetryOperation.<Boolean>newBuilder()
              .noOfRetry(10)
              .delayInterval(2000, TimeUnit.MILLISECONDS)
              .retryPredicate(result -> !result)
              .retryConsumer(
                  () -> {
                    try {
                      return testContext
                          .getEsClient()
                          .indices()
                          .getIndexTemplate(
                              new GetComposableIndexTemplateRequest("operate*"),
                              RequestOptions.DEFAULT)
                          .getIndexTemplates()
                          .isEmpty();
                    } catch (final ElasticsearchStatusException e) {
                      if (e.status().getStatus() == 404) {
                        return true;
                      } else {
                        throw e;
                      }
                    }
                  })
              .build()
              .retry();
      if (!templatesGone) {
        throw new OperateRuntimeException(
            "Operate index templates matching 'operate*' still exist after waiting for deletion");
      }
      LOGGER.info("************ Operate indices deleted ************");
    } catch (final IOException e) {
      throw new OperateRuntimeException(
          "Exception occurred while removing Operate and Zeebe indices: " + e.getMessage(), e);
    }
  }

  private void createBackup() throws Exception {
    final TakeBackupResponseDto backupResponse = operateAPICaller.backup(BACKUP_ID);
    snapshots = backupResponse.getScheduledSnapshots();

    RetryOperation.newBuilder()
        .noOfRetry(100)
        .delayInterval(
            10,
            TimeUnit.MILLISECONDS) // short delay to verify that INCOMPLETE state is not returned
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

    final String zeebeVersion =
        ContainerVersionsUtil.readProperty(ZEEBE_CURRENTVERSION_PROPERTY_NAME);
    testContainerUtil.startZeebe(zeebeVersion, testContext);

    operateContainer =
        testContainerUtil
            .createOperateContainer(OPERATE_TEST_DOCKER_IMAGE, VERSION, testContext)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER));
    operateContainer.withEnv("CAMUNDA_OPERATE_BACKUP_REPOSITORYNAME", REPOSITORY_NAME);
    operateContainer.withEnv("CAMUNDA_OPERATE_ARCHIVER_ILMENABLED", "true");
    operateContainer.withEnv("CAMUNDA_OPERATE_ARCHIVER_WAITPERIODBEFOREARCHIVING", "10s");

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

  private void restoreBackup(final boolean restoreGlobalState) {
    snapshots.forEach(
        snapshot -> {
          try {
            testContext
                .getEsClient()
                .snapshot()
                .restore(
                    new RestoreSnapshotRequest(REPOSITORY_NAME, snapshot)
                        .waitForCompletion(true)
                        .includeGlobalState(restoreGlobalState),
                    RequestOptions.DEFAULT);
          } catch (final IOException e) {
            throw new OperateRuntimeException(
                "Exception occurred while restoring the backup: " + e.getMessage(), e);
          }
        });
    LOGGER.info("************ Backup restored (globalState={}) ************", restoreGlobalState);
  }

  private void createSnapshotRepository(final BackupRestoreTestContext testContext)
      throws IOException {
    testContext
        .getEsClient()
        .snapshot()
        .createRepository(
            new PutRepositoryRequest(REPOSITORY_NAME)
                .type(FsRepository.TYPE)
                .settings(asMap("location", REPOSITORY_NAME)),
            RequestOptions.DEFAULT);
  }

  private void stopQuietly(final String name, final ThrowingRunnable action) {
    try {
      action.run();
    } catch (final Exception e) {
      LOGGER.warn("Failed to stop {} during teardown: {}", name, e.getMessage());
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}

@Configuration
@ComponentScan(
    basePackages = {
      "io.camunda.operate.util.rest",
      "io.camunda.operate.qa.backup",
      "io.camunda.operate.webapp.rest.dto",
      "io.camunda.operate.schema.templates",
      "io.camunda.operate.property",
      "io.camunda.operate.conditions"
    })
class TestConfig {}
