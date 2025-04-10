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
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.snapshot.Repository;
import co.elastic.clients.elasticsearch.snapshot.RestoreRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.repository.BackupRepositoryPropsRecord;
import io.camunda.webapps.backup.repository.SnapshotNameProvider;
import io.camunda.webapps.backup.repository.elasticsearch.ElasticsearchBackupRepository;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

public class ESDBClientBackup implements BackupDBClient {

  final RestClient restClient;
  final ElasticsearchClient esClient;
  private final Executor executor;

  public ESDBClientBackup(final String url, final Executor executor) throws IOException {
    restClient = RestClient.builder(HttpHost.create(url)).build();
    this.executor = executor;
    esClient =
        new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
  }

  @Override
  public void restore(final String repositoryName, final Collection<String> snapshots)
      throws IOException {
    for (final var snapshot : snapshots) {
      final var request =
          RestoreRequest.of(
              rb ->
                  rb.repository(repositoryName)
                      .snapshot(snapshot)
                      .indices("*")
                      .ignoreUnavailable(true)
                      .waitForCompletion(true));
      final var response = esClient.snapshot().restore(request);
      assertThat(response.snapshot().snapshot()).isEqualTo(snapshot);
    }
  }

  @Override
  public void createRepository(final String repositoryName) throws IOException {
    final var repository =
        Repository.of(r -> r.fs(rb -> rb.settings(s -> s.location(repositoryName))));
    final var response =
        esClient.snapshot().createRepository(b -> b.repository(repository).name(repositoryName));
    assertThat(response.acknowledged()).isTrue();
  }

  @Override
  public void deleteAllIndices(final String indexPrefix) throws IOException {
    esClient.indices().delete(DeleteIndexRequest.of(b -> b.index("*")));
  }

  @Override
  public BackupRepository zeebeBackupRepository(
      final String repositoryName, final SnapshotNameProvider snapshotNameProvider) {
    return new ElasticsearchBackupRepository(
        esClient, new BackupRepositoryPropsRecord("current", ""), snapshotNameProvider, executor);
  }

  @Override
  public List<String> cat() throws IOException {
    return esClient.cat().indices().valueBody().stream().map(IndicesRecord::index).toList();
  }

  @Override
  public void close() throws Exception {
    restClient.close();
  }
}
