/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ExpandWildcard;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.snapshot.Repository;
import co.elastic.clients.elasticsearch.snapshot.RestoreRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.client.CamundaClient;
import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.util.Either;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.stream.Stream;
import org.apache.http.HttpHost;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.SelinuxContext;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@ZeebeIntegration
public class BackupRestoreIT {
  private static final String REPOSITORY_NAME = "test-repository";
  @TempDir public Path repositoryDir;
  protected CamundaClient camundaClient;

  @TestZeebe(autoStart = false)
  protected TestStandaloneCamunda testStandaloneCamunda;

  private RestClient restClient;
  private ElasticsearchClient esClient;
  private org.opensearch.client.RestClient osRestClient;
  private OpenSearchClient opensearchClient;
  //  private GenericContainer<?> storageContainer;
  private String storagePath;
  private BackupRestoreTestConfig config;

  private void setupCamunda(final BackupRestoreTestConfig config) throws IOException {
    //    startStorageContainer(config);
    testStandaloneCamunda = new TestStandaloneCamunda();
    this.config = config;
    config.configure(testStandaloneCamunda, repositoryDir);
    testStandaloneCamunda.start();
    testStandaloneCamunda.awaitCompleteTopology();
    createSearchClient();
    createRepository(storagePath);
  }

  @AfterEach
  public void afterEach() throws IOException {
    testStandaloneCamunda.stop();
    if (restClient != null) {
      restClient.close();
      esClient = null;
    }
    if (osRestClient != null) {
      osRestClient.close();
      opensearchClient = null;
    }
  }

  public static Stream<BackupRestoreTestConfig> sources() {
    return Stream.of(DatabaseType.ELASTICSEARCH, DatabaseType.OPENSEARCH)
        .flatMap(
            dbType ->
                Stream.of(RepositoryType.values())
                    .map(
                        repositoryType ->
                            new BackupRestoreTestConfig(dbType, repositoryType, "bucket"))
                    .limit(1));
  }

  @ParameterizedTest
  @MethodSource(value = {"sources"})
  public void shouldBackupAndRestoreToPreviousState(final BackupRestoreTestConfig config)
      throws IOException, InterruptedException {
    // given
    setupCamunda(config);
    Thread.sleep(10000);
    final var backupClient = testStandaloneCamunda.newBackupClient();
    // a backup is requested
    final var takeResponse = backupClient.takeBackup(1L);
    assertThat(takeResponse)
        .satisfies(Either::isLeft)
        .extracting(r -> r.get().getScheduledSnapshots())
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .isNotEmpty();
    final var snapshots = takeResponse.get().getScheduledSnapshots();
    // the backup is completed
    Awaitility.await("Backup completed")
        .atMost(Duration.ofSeconds(600))
        .until(
            () -> {
              final var backupResponse = backupClient.getBackup(1L);
              return backupResponse.isRight()
                  && backupResponse.get().getState() == BackupStateDto.COMPLETED
                  && backupResponse.get().getDetails().stream()
                      .allMatch(d -> d.getState().equals("SUCCESS"));
            });

    // then
    // if we stop all apps and restart elasticsearch
    testStandaloneCamunda.stop();
    testStandaloneCamunda.startDBContainer();
    // perform a restore with a new client (old one is not valid anymore)
    createSearchClient();
    createRepository(storagePath);
    //    deleteAllIndices();
    restore(snapshots);
  }

  private void deleteAllIndices() throws IOException {
    switch (config.databaseType) {
      case ELASTICSEARCH -> {
        esClient
            .indices()
            .delete(
                DeleteIndexRequest.of(
                    b ->
                        b.index("operate*", "tasklist*", "optimize*")
                            .expandWildcards(ExpandWildcard.All)));
      }
      case OPENSEARCH -> {
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
            RestClient.builder(HttpHost.create(testStandaloneCamunda.getDBHostAddress())).build();

        esClient =
            new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
        break;
      case OPENSEARCH:
        if (osRestClient != null) {
          osRestClient.close();
        }
        osRestClient =
            org.opensearch.client.RestClient.builder(
                    HttpHost.create(testStandaloneCamunda.getDBHostAddress()))
                .build();
        final var transport =
            new org.opensearch.client.transport.rest_client.RestClientTransport(
                osRestClient, new org.opensearch.client.json.jackson.JacksonJsonpMapper());
        opensearchClient = new OpenSearchClient(transport);
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
                      "/mount/backups",
                      BindMode.READ_WRITE,
                      SelinuxContext.SHARED);
                  return c.withEnv("path.repo", "/mount/backups");
                });
      }
    }
  }

  enum RepositoryType {
    S3,
    AZURE,
    GCS;
  }
}
