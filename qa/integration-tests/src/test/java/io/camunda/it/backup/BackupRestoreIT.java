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
import io.camunda.it.utils.MultiDbConfigurator;
import io.camunda.management.backups.TakeBackupHistoryResponse;
import io.camunda.qa.util.cluster.TestRestManagementClient;
import io.camunda.qa.util.cluster.TestSimpleCamundaApplication;
import io.camunda.search.connect.configuration.DatabaseType;
import io.camunda.webapps.backup.BackupStateDto;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneApplication;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
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
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

@ZeebeIntegration
public class BackupRestoreIT {
  private static final String REPOSITORY_NAME = "test-repository";
  private static final String INDEX_PREFIX = "backup-restore";
  @TempDir public Path repositoryDir;
  protected CamundaClient camundaClient;

  @TestZeebe(autoStart = false)
  protected TestStandaloneApplication<?> testStandaloneCamunda;

  private GenericContainer<?> searchContainer;

  private RestClient restClient;
  private ElasticsearchClient esClient;
  private BackupRestoreTestConfig config;
  private TestRestManagementClient backupClient;

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
    backupClient = TestRestManagementClient.of(testStandaloneCamunda);
    createSearchClient();
    createRepository();
  }

  public static Stream<BackupRestoreTestConfig> sources() {
    final var backupRestoreConfigs = new ArrayList<BackupRestoreTestConfig>();
    for (final var db : List.of(DatabaseType.ELASTICSEARCH)) {
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

    final var takeResponse = backupClient.takeBackup(1L);
    assertThat(takeResponse)
        .extracting(TakeBackupHistoryResponse::getScheduledSnapshots)
        .asInstanceOf(InstanceOfAssertFactories.LIST)
        .isNotEmpty();
    final var snapshots = takeResponse.getScheduledSnapshots();

    Awaitility.await("Backup completed")
        .atMost(Duration.ofSeconds(600))
        .untilAsserted(
            () -> {
              final var backupResponse = backupClient.getBackup(1L);
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
      case OPENSEARCH -> {
        throw new UnsupportedOperationException("Opensearch is not yet supported");
      }
    }
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
      }
    }
  }

  public record BackupRestoreTestConfig(DatabaseType databaseType, String bucket) {}
}
