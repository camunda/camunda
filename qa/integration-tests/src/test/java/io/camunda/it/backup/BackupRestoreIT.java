/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.it.utils.MultiDbConfigurator;
import io.camunda.management.backups.TakeBackupHistoryResponse;
import io.camunda.qa.util.cluster.HistoryBackupClient;
import io.camunda.qa.util.cluster.TestSimpleCamundaApplication;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.agrona.CloseHelper;
import org.apache.http.HttpHost;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.awaitility.Awaitility;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.SelinuxContext;

@ZeebeIntegration
public class BackupRestoreIT {
  private static final Logger LOG = LoggerFactory.getLogger(BackupRestoreIT.class);
  private static final String REPOSITORY_NAME = "test-repository";
  private static final String INDEX_PREFIX = "backup-restore";
  private static final String PROCESS_ID = "backup-process";
  private static final int PROCESS_INSTANCE_NUMBER = 1;

  @TempDir public Path repositoryDir;
  protected CamundaClient camundaClient;

  @TestZeebe(autoStart = false)
  protected TestStandaloneApplication<?> testStandaloneCamunda;

  @AutoClose protected BackupDBClient backupDbClient;
  private GenericContainer<?> searchContainer;
  @AutoClose private DataGenerator generator;
  private BackupRestoreTestConfig config;
  private HistoryBackupClient historyBackupClient;

  @AfterEach
  public void tearDown() {
    CloseHelper.quietCloseAll(restClient, camundaClient, searchContainer);
  }

  private void setup(final BackupRestoreTestConfig config) throws IOException {
    final String dbUrl;
    testStandaloneCamunda = new TestSimpleCamundaApplication();
    final var configurator = new MultiDbConfigurator(testStandaloneCamunda);
    searchContainer =
        switch (config.databaseType) {
          case ELASTICSEARCH -> {
            final var container =
                TestSearchContainers.createDefeaultElasticsearchContainer()
                    .withStartupTimeout(Duration.ofMinutes(5))
                    // location of the repository that will be used for snapshots
                    .withEnv("path.repo", "~/");
            // container.addFileSystemBind(
            // repositoryDir.toString(), "~/", BindMode.READ_WRITE, SelinuxContext.SHARED);
            container.start();
            dbUrl = "http://" + container.getHttpHostAddress();

            // configure the app
            configurator.configureElasticsearchSupport(dbUrl, INDEX_PREFIX);
            yield container;
          }

          default ->
              throw new IllegalArgumentException(
                  "Unsupported database type: " + config.databaseType);
        };
    configurator.getOperateProperties().getBackup().setRepositoryName(REPOSITORY_NAME);
    configurator.getTasklistProperties().getBackup().setRepositoryName(REPOSITORY_NAME);

    this.config = config;
    testStandaloneCamunda.start().awaitCompleteTopology();
    historyBackupClient = HistoryBackupClient.of(testStandaloneCamunda);
    createSearchClient();
    createRepository();
  }

  public static Stream<BackupRestoreTestConfig> sources() {
    final var backupRestoreConfigs = new ArrayList<BackupRestoreTestConfig>();
    for (final var db : List.of(DatabaseType.ELASTICSEARCH, DatabaseType.OPENSEARCH)) {
      backupRestoreConfigs.add(new BackupRestoreTestConfig(db, "bucket"));
    }
    return backupRestoreConfigs.stream();
  }

  @ParameterizedTest
  @MethodSource(value = {"sources"})
  public void shouldBackupAndRestoreToPreviousState(final BackupRestoreTestConfig config)
      throws IOException, InterruptedException {
    // given
    setup(config);

    final var takeResponse = historyBackupClient.takeBackup(1L);
    assertThat(takeResponse)
        .extracting(TakeBackupHistoryResponse::getScheduledSnapshots)
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .isNotEmpty();
    final var snapshots = takeResponse.getScheduledSnapshots();

    Awaitility.await("Backup completed")
        .atMost(Duration.ofSeconds(600))
        .untilAsserted(
            () -> {
              final var backupResponse = historyBackupClient.getBackup(1L);
              assertThat(backupResponse.getState()).isEqualTo(BackupStateDto.COMPLETED);
              assertThat(backupResponse.getDetails()).allMatch(d -> d.getState().equals("SUCCESS"));
            });

    // then
    // if we stop all apps and restart elasticsearch
    testStandaloneCamunda.stop();

    // then
    deleteAllIndices();

    // restore with a new client is successful
    restore(snapshots);

    // then
    generator.verifyAllExported();
  }

  private void deleteAllIndices() throws IOException {
    switch (config.databaseType) {
      case ELASTICSEARCH -> {
        esClient
            .indices()
            .delete(
                DeleteIndexRequest.of(
                    b -> b.index(INDEX_PREFIX + "*").expandWildcards(ExpandWildcard.All)));
      }
      default -> {
        throw new UnsupportedOperationException("Opensearch is not yet supported");
      }
    }
  }

  public void createSearchClient() throws IOException {
    switch (config.databaseType) {
      case ELASTICSEARCH:
        if (restClient != null) {
          try {
            restClient.close();
          } catch (final IOException ignored) {
            // ignore it
          }
        }
        restClient =
            RestClient.builder(HttpHost.create(testStandaloneCamunda.getElasticSearchHostAddress()))
                .build();

        esClient =
            new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
        break;
      case OPENSEARCH:
    }
  }

  private void createRepository(final String basePath) throws IOException {
    //    final var repository =
    //        switch (config.repositoryType) {
    //          case S3 ->
    //              Repository.of(
    //                  b ->
    //                      b.s3(
    //                          S3Repository.of(
    //                              sb -> sb.settings(s ->
    // s.bucket(config.bucket).basePath(basePath)))));
    //          case GCS ->
    //              Repository.of(
    //                  b ->
    //                      b.gcs(
    //                          GcsRepository.of(
    //                              sb -> sb.settings(s ->
    // s.bucket(config.bucket).basePath(basePath)))));
    //          case AZURE -> Repository.of(b -> b.azure(ab -> ab.settings(s ->
    // s.basePath(basePath))));
    //        };
    switch (config.databaseType) {
      case ELASTICSEARCH -> {
        final var repository =
            Repository.of(r -> r.fs(rb -> rb.settings(s -> s.location(REPOSITORY_NAME))));
        final var response =
            esClient
                .snapshot()
                .createRepository(b -> b.repository(repository).name(REPOSITORY_NAME));
        assertThat(response.acknowledged()).isTrue();
      }
      case OPENSEARCH -> {
        final var repository =
            org.opensearch.client.opensearch.snapshot.Repository.of(
                r -> r.type("fs").settings(s -> s.location(REPOSITORY_NAME)));
        final var response =
            opensearchClient
                .snapshot()
                .createRepository(
                    b ->
                        b.repository(repository)
                            .type("fs")
                            .name(REPOSITORY_NAME)
                            .settings(sb -> sb.location(REPOSITORY_NAME)));
        assertThat(response.acknowledged()).isTrue();
      }
    }
  }

  private void restore(final Collection<String> snapshots) throws IOException {
    for (final var snapshot : snapshots) {
      switch (config.databaseType) {
        case ELASTICSEARCH -> {
          final var request =
              RestoreRequest.of(
                  rb ->
                      rb.repository(REPOSITORY_NAME)
                          .snapshot(snapshot)
                          .indices("*")
                          .ignoreUnavailable(true)
                          .waitForCompletion(true));
          final var response = esClient.snapshot().restore(request);
          assertThat(response.snapshot().snapshot()).isEqualTo(snapshot);
        }
        case OPENSEARCH -> {
          final var request =
              org.opensearch.client.opensearch.snapshot.RestoreRequest.of(
                  rb ->
                      rb.repository(REPOSITORY_NAME)
                          .snapshot(snapshot)
                          .indices("*")
                          .ignoreUnavailable(true)
                          .waitForCompletion(true));
          final var response = opensearchClient.snapshot().restore(request);
          assertThat(response.snapshot().snapshot()).isEqualTo(snapshot);
        }
      }
    }
  }

  //  private void startStorageContainer(final BackupRestoreTestConfig config) {
  //    storagePath = "";
  //    switch (config.repositoryType) {
  //      case S3 -> {
  //        final var minio = new MinioContainer().withDomain("minio.domain", config.bucket);
  //        storageContainer = minio;
  //        storageContainer.start();
  //        storagePath = minio.externalEndpoint();
  //        createS3Bucket(config, minio);
  //      }
  //      case GCS -> {
  //        final var gcs = new GcsContainer();
  //        storageContainer = gcs;
  //        storageContainer.start();
  //        storagePath = gcs.externalEndpoint();
  //      }
  //      case AZURE -> {
  //        final var azure = new AzuriteContainer();
  //        azure.start();
  //        storagePath = azure.getConnectString();
  //        storageContainer = azure;
  //      }
  //      default -> {}
  //    }
  //  }
  //
  //  private void createS3Bucket(final BackupRestoreTestConfig config, final MinioContainer minio)
  // {
  //    final var s3Config =
  //        new Builder()
  //            .withBucketName(config.bucket)
  //            .withEndpoint(minio.externalEndpoint())
  //            .withRegion(minio.region())
  //            .withCredentials(minio.accessKey(), minio.secretKey())
  //            .withApiCallTimeout(Duration.ofSeconds(25))
  //            .forcePathStyleAccess(true)
  //            .build();
  //    try (final var client = S3BackupStore.buildClient(s3Config)) {
  //      // it's possible to query to fast and get a 503 from the server here, so simply retry
  // after
  //      org.awaitility.Awaitility.await("until bucket is created")
  //          .untilAsserted(
  //              () ->
  //                  client
  //                      .createBucket(builder -> builder.bucket(s3Config.bucketName()).build())
  //                      .join());
  //    }
  //  }

  public record BackupRestoreTestConfig(
      DatabaseType databaseType, RepositoryType repositoryType, String bucket) {
    public void configure(
        final TestStandaloneCamunda testStandaloneCamunda, final Path repositoryDir) {
      testStandaloneCamunda.withBackupRepository(REPOSITORY_NAME);
      switch (databaseType) {
        case ELASTICSEARCH, OPENSEARCH ->
            testStandaloneCamunda.withDBContainer(
                c -> {
                  c.addFileSystemBind(
                      repositoryDir.toString(),
                      "/home",
                      BindMode.READ_WRITE,
                      SelinuxContext.SHARED);
                  return c.withEnv("path.repo", "/home");
                });
      }
    }
  }



  private void createBackupDbClient() throws Exception {
    if (backupDbClient != null) {
      backupDbClient.close();
    }
    backupDbClient = BackupDBClient.create(testStandaloneCamunda, config.databaseType);
  }


  public void createSearchClient() {
    switch (config.databaseType) {
      case ELASTICSEARCH:
        final var esContainer = (ElasticsearchContainer) searchContainer;
        CloseHelper.quietClose(restClient);
        restClient = RestClient.builder(HttpHost.create(esContainer.getHttpHostAddress())).build();

        esClient =
            new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
        break;
      case OPENSEARCH:
      default:
    }
  }

  private void createRepository() throws IOException {
    switch (config.databaseType) {
      case ELASTICSEARCH -> {
        final var repository =
            Repository.of(r -> r.fs(rb -> rb.settings(s -> s.location(REPOSITORY_NAME))));
        final var response =
            esClient
                .snapshot()
                .createRepository(b -> b.repository(repository).name(REPOSITORY_NAME));
        assertThat(response.acknowledged()).isTrue();
      }
      case OPENSEARCH -> {
        final var repository =
            org.opensearch.client.opensearch.snapshot.Repository.of(
                r -> r.type("fs").settings(s -> s.location(REPOSITORY_NAME)));
        final var response =
            opensearchClient
                .snapshot()
                .createRepository(
                    b ->
                        b.repository(repository)
                            .type("fs")
                            .name(REPOSITORY_NAME)
                            .settings(sb -> sb.location(REPOSITORY_NAME)));
        assertThat(response.acknowledged()).isTrue();
      }
      default -> {}
    }
  }

  private void restore(final Collection<String> snapshots) throws IOException {
    for (final var snapshot : snapshots) {
      switch (config.databaseType) {
        case ELASTICSEARCH -> {
          final var request =
              RestoreRequest.of(
                  rb ->
                      rb.repository(REPOSITORY_NAME)
                          .snapshot(snapshot)
                          .indices("*")
                          .ignoreUnavailable(true)
                          .waitForCompletion(true));
          final var response = esClient.snapshot().restore(request);
          assertThat(response.snapshot().snapshot()).isEqualTo(snapshot);
        }
        case OPENSEARCH -> {
          final var request =
              org.opensearch.client.opensearch.snapshot.RestoreRequest.of(
                  rb ->
                      rb.repository(REPOSITORY_NAME)
                          .snapshot(snapshot)
                          .indices("*")
                          .ignoreUnavailable(true)
                          .waitForCompletion(true));
          final var response = opensearchClient.snapshot().restore(request);
          assertThat(response.snapshot().snapshot()).isEqualTo(snapshot);
        }
        default -> {}
      }
    }
  }


  public record BackupRestoreTestConfig(DatabaseType databaseType, String bucket) {}
  enum RepositoryType {
    S3,
    AZURE,
    GCS;
  }
}
