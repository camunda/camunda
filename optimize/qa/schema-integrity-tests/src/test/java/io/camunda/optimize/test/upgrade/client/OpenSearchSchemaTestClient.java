/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.upgrade.client;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexTemplateRequest;
import org.opensearch.client.opensearch.indices.GetAliasRequest;
import org.opensearch.client.opensearch.indices.GetIndicesSettingsRequest.Builder;
import org.opensearch.client.opensearch.indices.GetMappingRequest;
import org.opensearch.client.opensearch.indices.GetTemplateRequest;
import org.opensearch.client.opensearch.indices.IndexState;
import org.opensearch.client.opensearch.indices.RefreshRequest;
import org.opensearch.client.opensearch.indices.TemplateMapping;
import org.opensearch.client.opensearch.indices.get_alias.IndexAliases;
import org.opensearch.client.opensearch.indices.get_mapping.IndexMappingRecord;
import org.opensearch.client.opensearch.snapshot.CreateRepositoryRequest;
import org.opensearch.client.opensearch.snapshot.CreateSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.DeleteRepositoryRequest;
import org.opensearch.client.opensearch.snapshot.DeleteSnapshotRequest;
import org.opensearch.client.opensearch.snapshot.RepositorySettings;
import org.opensearch.client.opensearch.snapshot.RestoreRequest;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.opensearch.client.util.MissingRequiredPropertyException;

public class OpenSearchSchemaTestClient extends AbstractDatabaseSchemaTestClient {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(OpenSearchSchemaTestClient.class);
  private final OpenSearchClient openSearchClient;

  public OpenSearchSchemaTestClient(final String name, final int port) {
    super(name);
    openSearchClient =
        new OpenSearchClient(
            ApacheHttpClient5TransportBuilder.builder(new HttpHost("http", "localhost", port))
                .build());
  }

  @Override
  public void close() {
    openSearchClient.shutdown();
  }

  @Override
  public void refreshAll() throws IOException {
    log.info("Refreshing all indices of {} Opensearch...", name);
    openSearchClient.indices().refresh(new RefreshRequest.Builder().index("*").build());
    log.info("Successfully refreshed all indices of {} OpenSearch!", name);
  }

  @Override
  public void cleanIndicesAndTemplates() throws IOException {
    log.info("Wiping all indices & templates from {} OpenSearch...", name);
    openSearchClient.indices().delete(new DeleteIndexRequest.Builder().index("_all").build());
    openSearchClient
        .indices()
        .deleteIndexTemplate(new DeleteIndexTemplateRequest.Builder().name("*").build());
    log.info("Successfully wiped all indices & templates from {} OpenSearch!", name);
  }

  @Override
  public void createSnapshotRepository() throws IOException {
    log.info("Creating snapshot repository on {} OpenSearch...", name);
    openSearchClient
        .snapshot()
        .createRepository(
            new CreateRepositoryRequest.Builder()
                .name(SNAPSHOT_REPOSITORY_NAME)
                .type("fs")
                .settings(
                    new RepositorySettings.Builder()
                        .location("/var/tmp")
                        .compress(true)
                        .readOnly(false)
                        .build())
                .build());
    log.info("Done creating snapshot repository on {} OpenSearch!", name);
  }

  @Override
  public void deleteSnapshotRepository() throws IOException {
    log.info("Removing snapshot repository on {} OpenSearch...", name);
    openSearchClient
        .snapshot()
        .deleteRepository(
            new DeleteRepositoryRequest.Builder().name(SNAPSHOT_REPOSITORY_NAME).build());
    log.info("Done removing snapshot repository on {} OpenSearch!", name);
  }

  @Override
  public void createSnapshotOfOptimizeIndices() throws IOException {
    log.info("Creating snapshot on {} OpenSearch...", name);
    openSearchClient
        .snapshot()
        .create(
            new CreateSnapshotRequest.Builder()
                .repository(SNAPSHOT_REPOSITORY_NAME)
                .snapshot(SNAPSHOT_NAME_1)
                .indices(OPTIMIZE_INDEX_PREFIX + "*")
                .waitForCompletion(true)
                .includeGlobalState(true)
                .build());
    log.info("Done creating snapshot on {} OpenSearch!", name);
  }

  @Override
  public void createAsyncSnapshot() throws IOException {
    log.info("Creating snapshot asynchronously on {} OpenSearch...", name);
    try {
      openSearchClient
          .snapshot()
          .create(
              new CreateSnapshotRequest.Builder()
                  .repository(SNAPSHOT_REPOSITORY_NAME)
                  .snapshot(SNAPSHOT_NAME_2)
                  .indices(OPTIMIZE_INDEX_PREFIX + "*")
                  .waitForCompletion(false)
                  .includeGlobalState(true)
                  .build());
    } catch (final RuntimeException e) {
      if (e.getCause() instanceof MissingRequiredPropertyException) {
        // A bug in async snapshot creation in OS:
        // https://github.com/opensearch-project/opensearch-java/issues/418, means that we have to
        // silently handle this exception knowing that the snapshot creation succeeded, just that
        // the response parsing failed
      } else {
        throw e;
      }
    }
    log.info("Done creating snapshot asynchronously on {} OpenSearch!", name);
  }

  @Override
  public void restoreSnapshot() throws IOException {
    log.info("Restoring snapshot on {} OpenSearch...", name);
    openSearchClient
        .snapshot()
        .restore(
            new RestoreRequest.Builder()
                .repository(SNAPSHOT_REPOSITORY_NAME)
                .snapshot(SNAPSHOT_NAME_1)
                .includeGlobalState(true)
                .waitForCompletion(true)
                .build());
    log.info("Done restoring snapshot on {} OpenSearch!", name);
  }

  @Override
  public void deleteSnapshot() throws IOException {
    deleteSnapshot(SNAPSHOT_NAME_1);
  }

  @Override
  public void deleteAsyncSnapshot() throws IOException {
    deleteSnapshot(SNAPSHOT_NAME_2);
  }

  @Override
  public void deleteSnapshot(final String snapshotName) throws IOException {
    log.info("Deleting snapshot {} on {} OpenSearch...", snapshotName, name);
    openSearchClient
        .snapshot()
        .delete(
            new DeleteSnapshotRequest.Builder()
                .repository(SNAPSHOT_REPOSITORY_NAME)
                .snapshot(snapshotName)
                .build());
    log.info("Done deleting {} snapshot on {} OpenSearch!", snapshotName, name);
  }

  public Map<String, IndexState> getSettings() throws IOException {
    return openSearchClient
        .indices()
        .getSettings(
            new Builder()
                .index(DEFAULT_OPTIMIZE_INDEX_PATTERN)
                .name(Arrays.asList(SETTINGS_FILTER))
                .build())
        .result();
  }

  public Map<String, IndexMappingRecord> getMappings() throws IOException {
    return openSearchClient
        .indices()
        .getMapping(new GetMappingRequest.Builder().index(DEFAULT_OPTIMIZE_INDEX_PATTERN).build())
        .result();
  }

  public Map<String, IndexAliases> getAliases() throws IOException {
    return openSearchClient
        .indices()
        .getAlias(new GetAliasRequest.Builder().index(DEFAULT_OPTIMIZE_INDEX_PATTERN).build())
        .result();
  }

  public Map<String, TemplateMapping> getTemplates() throws IOException {
    return openSearchClient
        .indices()
        .getTemplate(new GetTemplateRequest.Builder().name(DEFAULT_OPTIMIZE_INDEX_PATTERN).build())
        .result();
  }
}
