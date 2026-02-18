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

import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.repository.BackupRepositoryPropsRecord;
import io.camunda.webapps.backup.repository.SnapshotNameProvider;
import io.camunda.webapps.backup.repository.opensearch.OpensearchBackupRepository;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.cat.indices.IndicesRecord;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.RefreshRequest;
import org.opensearch.client.opensearch.snapshot.Repository;
import org.opensearch.client.opensearch.snapshot.RestoreSnapshotRequest;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;

public class OSClient implements DocumentClient {
  private final OpenSearchClient opensearchClient;
  private final OpenSearchTransport transport;

  public OSClient(final String url) {
    try {
      transport =
          ApacheHttpClient5TransportBuilder.builder(HttpHost.create(url))
              .setMapper(new JacksonJsonpMapper())
              .build();
      opensearchClient = new OpenSearchClient(transport);
    } catch (final URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void restore(final String repositoryName, final Collection<String> snapshots)
      throws IOException {
    for (final var snapshot : snapshots) {
      final var request =
          RestoreSnapshotRequest.of(
              rb ->
                  rb.repository(repositoryName)
                      .snapshot(snapshot)
                      .indices("*")
                      .ignoreUnavailable(true)
                      .waitForCompletion(true));
      final var response = opensearchClient.snapshot().restore(request);
      assertThat(response.snapshot().snapshot()).isEqualTo(snapshot);
    }
  }

  @Override
  public void createRepository(final String repositoryName) throws IOException {
    final var repository =
        Repository.of(r -> r.type("fs").settings(s -> s.location(repositoryName)));
    final var response =
        opensearchClient
            .snapshot()
            .createRepository(
                b ->
                    b.repository(repository)
                        .type("fs")
                        .name(repositoryName)
                        .settings(sb -> sb.location(repositoryName)));
    assertThat(response.acknowledged()).isTrue();
  }

  @Override
  public void deleteAllIndices(final String indexPrefix) throws IOException {
    opensearchClient.indices().delete(DeleteIndexRequest.of(b -> b.index("*")));
  }

  @Override
  public BackupRepository zeebeBackupRepository(
      final String repositoryName, final SnapshotNameProvider snapshotNameProvider) {
    return new OpensearchBackupRepository(
        opensearchClient,
        new OpenSearchAsyncClient(transport),
        new BackupRepositoryPropsRecord("current", "", 0, defaultIncompleteCheckTimeoutInSeconds()),
        snapshotNameProvider);
  }

  @Override
  public List<String> cat(final String indexPrefix) throws IOException {
    return opensearchClient.cat().indices(r -> r.index(indexPrefix + "*")).valueBody().stream()
        .map(IndicesRecord::index)
        .toList();
  }

  @Override
  public void index(final String indexName, final String documentId, final Object document)
      throws IOException {
    final var indexRequest =
        IndexRequest.of(i -> i.index(indexName).id(documentId).document(document));

    final var response = opensearchClient.index(indexRequest);

    final String result = response.result().toString().toLowerCase();
    if (!"created".equals(result) && !"updated".equals(result)) {
      throw new IOException(
          "Failed to index document with ID: " + documentId + ", result: " + response.result());
    }
  }

  @Override
  public void refresh(final String indexName) throws IOException {
    final var refreshRequest = RefreshRequest.of(r -> r.index(indexName));
    final var response = opensearchClient.indices().refresh(refreshRequest);

    // Check if refresh was successful
    if (response.shards() != null && response.shards().failed() > 0) {
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
    final var response = opensearchClient.bulk(bulkRequest);

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
    return opensearchClient
        .search(b -> b.index(indexName).size(size), Map.class)
        .hits()
        .hits()
        .stream()
        .map(Hit::source)
        .map(doc -> (Map<String, Object>) doc)
        .toList();
  }

  @Override
  public void close() throws Exception {
    transport.close();
  }
}
