/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.backup;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.backup.BackupRepository;
import io.camunda.webapps.backup.repository.BackupRepositoryPropsRecord;
import io.camunda.webapps.backup.repository.SnapshotNameProvider;
import io.camunda.webapps.backup.repository.opensearch.OpensearchBackupRepository;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.apache.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.cat.indices.IndicesRecord;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.transport.OpenSearchTransport;

public class OSDBClientBackup implements BackupDBClient {
  private final org.opensearch.client.RestClient osRestClient;
  private final OpenSearchClient opensearchClient;
  private final OpenSearchTransport transport;

  public OSDBClientBackup(final String url) {
    osRestClient = org.opensearch.client.RestClient.builder(HttpHost.create(url)).build();
    transport =
        new org.opensearch.client.transport.rest_client.RestClientTransport(
            osRestClient, new org.opensearch.client.json.jackson.JacksonJsonpMapper());
    opensearchClient = new OpenSearchClient(transport);
  }

  @Override
  public void restore(final String repositoryName, final Collection<String> snapshots)
      throws IOException {
    for (final var snapshot : snapshots) {
      final var request =
          org.opensearch.client.opensearch.snapshot.RestoreRequest.of(
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
        org.opensearch.client.opensearch.snapshot.Repository.of(
            r -> r.type("fs").settings(s -> s.location(repositoryName)));
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
        new BackupRepositoryPropsRecord("current", ""),
        snapshotNameProvider);
  }

  @Override
  public List<String> cat() throws IOException {
    return opensearchClient.cat().indices().valueBody().stream().map(IndicesRecord::index).toList();
  }

  @Override
  public void close() throws Exception {
    osRestClient.close();
  }
}
