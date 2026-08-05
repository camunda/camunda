/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.backup;

import static io.camunda.tasklist.qa.util.ContainerVersionsUtil.ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME;
import static io.camunda.tasklist.util.CollectionUtil.asMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.qa.backup.generator.BackupRestoreDataGenerator;
import io.camunda.tasklist.qa.util.ContainerVersionsUtil;
import io.camunda.tasklist.qa.util.TestContainerUtil;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.webapp.management.dto.TakeBackupResponseDto;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.ZeebeClientBuilder;
import java.io.IOException;
import java.util.List;
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
import org.elasticsearch.repositories.fs.FsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.ResponseException;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.snapshot.CreateRepositoryRequest;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {TestConfig.class})
public class BackupRestoreTest {

  public static final String ZEEBE_INDEX_PREFIX = "backup-restore-test";
  public static final String VERSION = "current-test";
  public static final String REPOSITORY_NAME = "testRepository";
  public static final Long BACKUP_ID = 123L;
  public static final String TASKLIST_INDEX_PREFIX = "tasklist";
  public static final String TASKLIST_TEMPLATE = "tasklist_template";
  public static final String TASKLIST_DELETE_ARCHIVED_INDICES = "tasklist_delete_archived_indices";
  private static final Logger LOGGER = LoggerFactory.getLogger(BackupRestoreTest.class);
  private static final String TASKLIST_TEST_DOCKER_IMAGE = "localhost:5000/camunda/tasklist";

  @Autowired private TasklistAPICaller tasklistAPICaller;

  @Autowired private BackupRestoreDataGenerator backupRestoreDataGenerator;

  private GenericContainer tasklistContainer;

  private ZeebeClient zeebeClient;

  // Low-level OpenSearch REST client, used to query/delete the ISM policy (which the typed client
  // does not expose). Created together with the OpenSearch client and closed on teardown.
  private org.opensearch.client.RestClient osRestClient;

  private final TestContainerUtil testContainerUtil = new TestContainerUtil();
  private BackupRestoreTestContext testContext;
  private List<String> snapshots;

  @BeforeEach
  public void setup() {
    testContext = new BackupRestoreTestContext().setZeebeIndexPrefix(ZEEBE_INDEX_PREFIX);
  }

  @AfterEach
  public void tearDown() {
    // Each parameterized run spins up its own containers; stop them so the two scenarios don't run
    // their (heavyweight) database/Zeebe/Tasklist containers side by side. Stopping is best effort:
    // a failure here must not mask the actual test outcome.
    stopQuietly("Tasklist", this::stopTasklist);
    stopQuietly("Zeebe", () -> testContainerUtil.stopZeebeAndTasklist(testContext));
    if (TestUtil.isOpenSearch()) {
      stopQuietly("OpenSearch", testContainerUtil::stopOpenSearch);
      stopQuietly(
          "OpenSearch REST client",
          () -> {
            if (osRestClient != null) {
              osRestClient.close();
            }
          });
    } else {
      stopQuietly("Elasticsearch", testContainerUtil::stopElasticsearch);
    }
    stopQuietly(
        "Zeebe client",
        () -> {
          if (zeebeClient != null) {
            zeebeClient.close();
          }
        });
  }

  /**
   * The global cluster state carries the index templates and the component template (for both
   * Elasticsearch and OpenSearch) as well as the Elasticsearch ILM policy. Restoring a snapshot
   * <em>with</em> global state therefore brings those back, whereas restoring <em>without</em>
   * global state only brings back the indices, leaving the templates and policy to be recreated by
   * Tasklist on startup (https://github.com/camunda/camunda/issues/32806).
   *
   * <p>OpenSearch ISM policies are stored in a system index rather than in the cluster metadata, so
   * they are never part of the restored global state and are always recreated by Tasklist on
   * startup.
   */
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void testBackupRestore(final boolean restoreGlobalState) throws Exception {
    startAllApps();
    backupRestoreDataGenerator.createData(testContext);
    backupRestoreDataGenerator.assertData();
    snapshots = createBackup();
    backupRestoreDataGenerator.changeData(testContext);
    backupRestoreDataGenerator.assertDataAfterChange();
    stopTasklist();
    deleteTasklistIndices();
    deleteTemplatesAndPolicy();
    restoreBackup(restoreGlobalState);

    if (restoreGlobalState) {
      // Index templates and the component template are part of the cluster global state (for both
      // ES and OS), so restoring it brings them back with the snapshot.
      assertIndexTemplatesPresent();
      assertComponentTemplatePresent();
      // ES stores ILM policies in the cluster global state, so they are restored too. OpenSearch
      // ISM policies live in a system index rather than the cluster metadata, so they are NOT part
      // of the restored global state and are instead recreated by Tasklist on startup (asserted
      // after startTasklist below).
      if (!TestUtil.isOpenSearch()) {
        assertRetentionPolicyPresent();
      }
    } else {
      // Without the global state, none of these are restored; they must be recreated by Tasklist on
      // startup (https://github.com/camunda/camunda/issues/32806).
      assertIndexTemplatesAbsent();
      assertComponentTemplateAbsent();
      assertRetentionPolicyAbsent();
    }

    startTasklist();

    // In both cases the schema must be complete once Tasklist has started: the templates and
    // retention policy were either restored from the global state or recreated on schema startup.
    assertIndexTemplatesPresent();
    assertComponentTemplatePresent();
    assertRetentionPolicyPresent();

    backupRestoreDataGenerator.assertData();
  }

  public List<String> createBackup() {
    final TakeBackupResponseDto backupResponse = tasklistAPICaller.backup(BACKUP_ID);
    tasklistAPICaller.assertBackupState();
    return backupResponse.getScheduledSnapshots();
  }

  // ---------------------------------------------------------------------------------------------
  // Assertions: index templates / component template / retention policy (ILM on ES, ISM on OS)
  // ---------------------------------------------------------------------------------------------

  private void assertIndexTemplatesPresent() throws IOException {
    assertThat(indexTemplatesExist())
        .as("Index templates '%s*' should be present", TASKLIST_INDEX_PREFIX)
        .isTrue();
    LOGGER.info("************ Index templates present ************");
  }

  private void assertIndexTemplatesAbsent() throws IOException {
    assertThat(indexTemplatesExist())
        .as("Index templates '%s*' should be absent before Tasklist startup", TASKLIST_INDEX_PREFIX)
        .isFalse();
    LOGGER.info("************ Index templates absent ************");
  }

  private boolean indexTemplatesExist() throws IOException {
    if (TestUtil.isOpenSearch()) {
      return testContext
          .getOsClient()
          .indices()
          .existsIndexTemplate(it -> it.name(TASKLIST_INDEX_PREFIX + "*"))
          .value();
    }
    try {
      return !testContext
          .getEsClient()
          .indices()
          .getIndexTemplate(
              new GetComposableIndexTemplateRequest(TASKLIST_INDEX_PREFIX + "*"),
              RequestOptions.DEFAULT)
          .getIndexTemplates()
          .isEmpty();
    } catch (final ElasticsearchStatusException e) {
      if (e.status().getStatus() == 404) {
        return false;
      }
      throw e;
    }
  }

  private void assertComponentTemplatePresent() throws IOException {
    assertThat(componentTemplateExists())
        .as("Component template '%s' should be present", TASKLIST_TEMPLATE)
        .isTrue();
    LOGGER.info("************ Component template present ************");
  }

  private void assertComponentTemplateAbsent() throws IOException {
    assertThat(componentTemplateExists())
        .as("Component template '%s' should be absent before Tasklist startup", TASKLIST_TEMPLATE)
        .isFalse();
    LOGGER.info("************ Component template absent ************");
  }

  private boolean componentTemplateExists() throws IOException {
    if (TestUtil.isOpenSearch()) {
      return testContext
          .getOsClient()
          .cluster()
          .existsComponentTemplate(r -> r.name(TASKLIST_TEMPLATE))
          .value();
    }
    try {
      return testContext
              .getEsClient()
              .cluster()
              .getComponentTemplate(
                  new GetComponentTemplatesRequest(TASKLIST_TEMPLATE), RequestOptions.DEFAULT)
              .getComponentTemplates()
              .get(TASKLIST_TEMPLATE)
          != null;
    } catch (final ElasticsearchStatusException e) {
      if (e.status().getStatus() == 404) {
        return false;
      }
      throw e;
    }
  }

  private void assertRetentionPolicyPresent() throws IOException {
    assertThat(retentionPolicyExists())
        .as("Retention policy '%s' should be present", TASKLIST_DELETE_ARCHIVED_INDICES)
        .isTrue();
    LOGGER.info("************ Retention policy present ************");
  }

  private void assertRetentionPolicyAbsent() throws IOException {
    assertThat(retentionPolicyExists())
        .as(
            "Retention policy '%s' should be absent before Tasklist startup",
            TASKLIST_DELETE_ARCHIVED_INDICES)
        .isFalse();
    LOGGER.info("************ Retention policy absent ************");
  }

  private boolean retentionPolicyExists() throws IOException {
    if (TestUtil.isOpenSearch()) {
      final Request request =
          new Request("GET", "/_plugins/_ism/policies/" + TASKLIST_DELETE_ARCHIVED_INDICES);
      try {
        final Response response = osRestClient.performRequest(request);
        return response.getStatusLine().getStatusCode() == 200;
      } catch (final ResponseException e) {
        if (e.getResponse().getStatusLine().getStatusCode() == 404) {
          return false;
        }
        throw e;
      }
    }
    try {
      return testContext
              .getEsClient()
              .indexLifecycle()
              .getLifecyclePolicy(
                  new GetLifecyclePolicyRequest(TASKLIST_DELETE_ARCHIVED_INDICES),
                  RequestOptions.DEFAULT)
              .getPolicies()
              .get(TASKLIST_DELETE_ARCHIVED_INDICES)
          != null;
    } catch (final ElasticsearchStatusException e) {
      if (e.status().getStatus() == 404) {
        return false;
      }
      throw e;
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Deletion of templates + retention policy (to simulate a restore into a fresh cluster)
  // ---------------------------------------------------------------------------------------------

  private void deleteTemplatesAndPolicy() throws IOException {
    if (TestUtil.isOpenSearch()) {
      deleteOsTemplatesAndPolicy();
    } else {
      deleteElsTemplatesAndPolicy();
    }
  }

  private void deleteElsTemplatesAndPolicy() throws IOException {
    final RestHighLevelClient esClient = testContext.getEsClient();
    try {
      esClient
          .indices()
          .deleteIndexTemplate(
              new DeleteComposableIndexTemplateRequest(TASKLIST_INDEX_PREFIX + "*"),
              RequestOptions.DEFAULT);
    } catch (final ElasticsearchStatusException e) {
      if (e.status().getStatus() != 404) {
        throw e;
      }
    }
    esClient
        .getLowLevelClient()
        .performRequest(
            new org.elasticsearch.client.Request(
                "DELETE", "/_component_template/" + TASKLIST_TEMPLATE));
    esClient
        .indexLifecycle()
        .deleteLifecyclePolicy(
            new DeleteLifecyclePolicyRequest(TASKLIST_DELETE_ARCHIVED_INDICES),
            RequestOptions.DEFAULT);
    LOGGER.info(
        "************ Tasklist ElasticSearch templates and ILM policy deleted ************");
  }

  private void deleteOsTemplatesAndPolicy() throws IOException {
    final OpenSearchClient osClient = testContext.getOsClient();
    osClient
        .indices()
        .getIndexTemplate(it -> it.name(TASKLIST_INDEX_PREFIX + "*"))
        .indexTemplates()
        .forEach(
            t -> {
              try {
                osClient.indices().deleteIndexTemplate(d -> d.name(t.name()));
              } catch (final IOException e) {
                throw new TasklistRuntimeException(
                    "Exception occurred while deleting index template " + t.name(), e);
              }
            });
    try {
      osClient.cluster().deleteComponentTemplate(d -> d.name(TASKLIST_TEMPLATE));
    } catch (final OpenSearchException e) {
      if (e.status() != 404) {
        throw e;
      }
    }
    try {
      osRestClient.performRequest(
          new Request("DELETE", "/_plugins/_ism/policies/" + TASKLIST_DELETE_ARCHIVED_INDICES));
    } catch (final ResponseException e) {
      if (e.getResponse().getStatusLine().getStatusCode() != 404) {
        throw e;
      }
    }
    LOGGER.info("************ Tasklist OpenSearch templates and ISM policy deleted ************");
  }

  // ---------------------------------------------------------------------------------------------
  // Application / container lifecycle
  // ---------------------------------------------------------------------------------------------

  private void startAllApps() throws IOException {
    if (TestUtil.isOpenSearch()) {
      startOsApps();
    } else {
      startElsApps();
    }

    tasklistContainer =
        testContainerUtil
            .createTasklistContainer(TASKLIST_TEST_DOCKER_IMAGE, VERSION, testContext)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .withEnv("CAMUNDA_TASKLIST_BACKUP_REPOSITORYNAME", REPOSITORY_NAME)
            .withEnv("CAMUNDA_TASKLIST_ARCHIVER_ILMENABLED", "true")
            .withEnv(
                "CAMUNDA_TASKLIST_DATABASE",
                TestUtil.isOpenSearch() ? "opensearch" : "elasticsearch")
            .withEnv("CAMUNDA_TASKLIST_CSRF_PREVENTION_ENABLED", "false");

    startTasklist();
  }

  private void startElsApps() throws IOException {
    testContainerUtil.startElasticsearch(testContext);
    testContainerUtil.checkElasctisearchHealth(testContext);
    testContext.setEsClient(
        new RestHighLevelClient(
            RestClient.builder(
                new HttpHost(testContext.getExternalElsHost(), testContext.getExternalElsPort()))));
    createElsSnapshotRepository(testContext);

    testContainerUtil.startZeebe(
        ContainerVersionsUtil.readProperty(ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME), testContext);
    createZeebeClient(testContext.getExternalZeebeContactPoint());
  }

  private OpenSearchClient createOsClient() {
    final org.apache.hc.core5.http.HttpHost host =
        new org.apache.hc.core5.http.HttpHost(
            testContext.getExternalOsHost(), testContext.getExternalOsPort());
    final ApacheHttpClient5TransportBuilder builder =
        ApacheHttpClient5TransportBuilder.builder(host);

    final JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(CommonUtils.OBJECT_MAPPER);
    builder.setMapper(jsonpMapper);

    return new OpenSearchClient(builder.build());
  }

  private void startOsApps() throws IOException {
    testContainerUtil.startOpenSearch(testContext);
    final OpenSearchClient osClient = createOsClient();
    testContainerUtil.checkOpenSearchHealth(osClient);
    testContext.setOsClient(osClient);
    osRestClient =
        org.opensearch.client.RestClient.builder(
                new HttpHost(testContext.getExternalOsHost(), testContext.getExternalOsPort()))
            .build();
    createOsSnapshotRepository(testContext);

    testContainerUtil.startZeebe(
        ContainerVersionsUtil.readProperty(ZEEBE_CURRENTVERSION_DOCKER_PROPERTY_NAME), testContext);
    createZeebeClient(testContext.getExternalZeebeContactPoint());
  }

  private void startTasklist() {
    testContainerUtil.startTasklistContainer(tasklistContainer, VERSION, testContext);
    LOGGER.info("************ Tasklist started  ************");
    testContext.setTasklistRestClient(tasklistAPICaller.createGraphQLTestTemplate(testContext));
  }

  private void stopTasklist() {
    if (tasklistContainer != null) {
      tasklistContainer.stop();
      LOGGER.info("************ Tasklist stopped  ************");
    }
  }

  private void restoreBackup(final boolean restoreGlobalState) {
    if (TestUtil.isOpenSearch()) {
      restoreOsBackup(restoreGlobalState);
    } else {
      restoreElsBackup(restoreGlobalState);
    }
    LOGGER.info("************ Backup restored (globalState={}) ************", restoreGlobalState);
  }

  private void restoreElsBackup(final boolean restoreGlobalState) {
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
            throw new TasklistRuntimeException(
                "Exception occurred while restoring the backup: " + e.getMessage(), e);
          }
        });
  }

  private void restoreOsBackup(final boolean restoreGlobalState) {
    snapshots.forEach(
        snapshot -> {
          try {
            testContext
                .getOsClient()
                .snapshot()
                .restore(
                    r ->
                        r.repository(REPOSITORY_NAME)
                            .snapshot(snapshot)
                            .waitForCompletion(true)
                            .includeGlobalState(restoreGlobalState));
          } catch (final IOException | OpenSearchException e) {
            throw new TasklistRuntimeException(
                "Exception occurred while restoring the backup: " + e.getMessage(), e);
          }
        });
  }

  private void deleteTasklistIndices() {
    if (TestUtil.isOpenSearch()) {
      deleteOsIndices();
    } else {
      deleteElsIndices();
    }
  }

  private void deleteOsIndices() {
    try {
      testContext.getOsClient().indices().delete(dir -> dir.index("tasklist*"));
      // we need to remove Zeebe indices as otherwise Tasklist will start importing data at once and
      // we won't be able to assert the older state of data (from backup)
      testContext.getOsClient().indices().delete(dir -> dir.index(ZEEBE_INDEX_PREFIX + "*"));
      tasklistAPICaller.checkIndicesAreDeleted(testContext.getOsClient());
      LOGGER.info("************ Tasklist OpenSearch indices deleted ************");
    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          "Exception occurred while removing Tasklist and Zeebe indices: " + e.getMessage(), e);
    }
  }

  private void deleteElsIndices() {
    try {
      testContext
          .getEsClient()
          .indices()
          .delete(new DeleteIndexRequest("tasklist*"), RequestOptions.DEFAULT);
      // we need to remove Zeebe indices as otherwise Tasklist will start importing data at once and
      // we won't be able to assert the older state of data (from backup)
      testContext
          .getEsClient()
          .indices()
          .delete(new DeleteIndexRequest(ZEEBE_INDEX_PREFIX + "*"), RequestOptions.DEFAULT);
      tasklistAPICaller.checkIndicesAreDeleted(testContext.getEsClient());
      LOGGER.info("************ Tasklist ElasticSearch indices deleted ************");
    } catch (final IOException e) {
      throw new TasklistRuntimeException(
          "Exception occurred while removing Tasklist and Zeebe indices: " + e.getMessage(), e);
    }
  }

  private void createElsSnapshotRepository(final BackupRestoreTestContext testContext)
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

  private void createOsSnapshotRepository(final BackupRestoreTestContext testContext)
      throws IOException {
    testContext
        .getOsClient()
        .snapshot()
        .createRepository(
            CreateRepositoryRequest.of(
                r ->
                    r.name(REPOSITORY_NAME)
                        .type(FsRepository.TYPE)
                        .settings(s -> s.location(REPOSITORY_NAME))));
  }

  private ZeebeClient createZeebeClient(final String zeebeGateway) {
    final ZeebeClientBuilder builder =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(zeebeGateway)
            .defaultJobWorkerMaxJobsActive(5)
            .usePlaintext();
    zeebeClient = builder.build();
    return zeebeClient;
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
      "io.camunda.tasklist.qa.backup",
      "io.camunda.tasklist.webapp.graphql.entity",
      "io.camunda.tasklist.qa.util.rest"
    })
class TestConfig {}
