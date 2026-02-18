/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.document;

import static io.camunda.webapps.backup.repository.BackupRepositoryProps.defaultIncompleteCheckTimeoutInSeconds;
import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.cat.indices.IndicesRecord;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
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
import java.util.Map;
import java.util.concurrent.Executor;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;

public class ESClient implements DocumentClient {

  final RestClient restClient;
  final ElasticsearchClient esClient;
  private final Executor executor;

  public ESClient(final String url, final Executor executor) {
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
        esClient,
        new BackupRepositoryPropsRecord("current", "", 0, defaultIncompleteCheckTimeoutInSeconds()),
        snapshotNameProvider,
        executor);
  }

  @Override
  public List<String> cat(final String indexPrefix) throws IOException {
    return esClient.cat().indices(r -> r.index(indexPrefix + "*")).valueBody().stream()
        .map(IndicesRecord::index)
        .toList();
  }

  @Override
  public void index(final String indexName, final String documentId, final Object document)
      throws IOException {
    final var indexRequest =
        IndexRequest.of(i -> i.index(indexName).id(documentId).document(document));

    final var response = esClient.index(indexRequest);

    // Verify the indexing was successful
    if (response.result() == null) {
      throw new IOException("Failed to index document with ID: " + documentId + ", result is null");
    }

    final String result = response.result().toString().toLowerCase();
    if (!"created".equals(result) && !"updated".equals(result)) {
      throw new IOException(
          "Failed to index document with ID: " + documentId + ", result: " + response.result());
    }
  }

  @Override
  public void refresh(final String indexName) throws IOException {
    final var refreshRequest = RefreshRequest.of(r -> r.index(indexName));
    final var response = esClient.indices().refresh(refreshRequest);

    // Check if refresh was successful
    if (response.shards() != null && response.shards().failed().intValue() > 0) {
      throw new IOException(
          "Refresh failed for index: "
              + indexName
              + ", failed shards: "
              + response.shards().failed());
    }
  }

  @Override
  public void bulkIndex(final String indexName, final Collection<DocumentWithId> documents)
      throws IOException {
    if (documents.isEmpty()) {
      return; // Nothing to index
    }

    final var bulkBuilder = new BulkRequest.Builder();

    for (final var doc : documents) {
      bulkBuilder.operations(
          op -> op.index(idx -> idx.index(indexName).id(doc.id()).document(doc.document())));
    }

    final var bulkRequest = bulkBuilder.build();
    final var response = esClient.bulk(bulkRequest);

    // Check for errors in bulk response
    if (response.errors()) {
      final var errorCount = response.items().size();
      throw new IOException(
          "Bulk indexing failed for " + errorCount + " documents in index: " + indexName);
    }
  }

  @Override
  public List<Map<String, Object>> search(final String indexName, final int size)
      throws IOException {
    return esClient
        .search(b -> b.index(indexName).size(size), java.util.Map.class)
        .hits()
        .hits()
        .stream()
        .map(Hit::source)
        .map(doc -> (Map<String, Object>) doc)
        .toList();
  }

  @Override
  public void setMaxResultWindow(final String indexName, final int maxResultWindow)
      throws IOException {
    esClient
        .indices()
        .putSettings(b -> b.index(indexName).settings(s -> s.maxResultWindow(maxResultWindow)));
  }

  @Override
  public void close() throws Exception {
    restClient.close();
  }
}
