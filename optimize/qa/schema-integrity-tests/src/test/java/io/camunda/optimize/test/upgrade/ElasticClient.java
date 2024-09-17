/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.upgrade;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.http.HttpHost;
import org.elasticsearch.action.admin.cluster.repositories.delete.DeleteRepositoryRequest;
import org.elasticsearch.action.admin.cluster.repositories.put.PutRepositoryRequest;
import org.elasticsearch.action.admin.cluster.snapshots.delete.DeleteSnapshotRequest;
import org.elasticsearch.action.admin.cluster.snapshots.restore.RestoreSnapshotRequest;
import org.elasticsearch.action.admin.indices.alias.get.GetAliasesRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.admin.indices.settings.get.GetSettingsRequest;
import org.elasticsearch.action.admin.indices.template.delete.DeleteIndexTemplateRequest;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.GetIndexTemplatesRequest;
import org.elasticsearch.client.indices.GetMappingsRequest;
import org.elasticsearch.client.indices.IndexTemplateMetadata;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.cluster.metadata.MappingMetadata;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.repositories.fs.FsRepository;

public class ElasticClient {

  public static final String SNAPSHOT_REPOSITORY_NAME = "my_backup";
  public static final String SNAPSHOT_NAME_1 = "snapshot_1";
  public static final String SNAPSHOT_NAME_2 = "snapshot_2";
  public static final String OPTIMIZE_INDEX_PREFIX = "optimize";
  public static final String DEFAULT_OPTIMIZE_INDEX_PATTERN =
      OPTIMIZE_INDEX_PREFIX + "-*,-" + OPTIMIZE_INDEX_PREFIX + "-update-log*";
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(ElasticClient.class);
  private static final String[] SETTINGS_FILTER =
      new String[] {
        "index.analysis.*",
        "index.number_of_shards",
        "index.number_of_replicas",
        "index.max_ngram_diff",
        "index.mapping.*",
        "index.refresh_interval"
      };
  private final String name;
  private final RestHighLevelClient client;
  private final Boolean readonlySnapshotRepository;

  public ElasticClient(
      final String name,
      final int port,
      final String host,
      final Boolean readonlySnapshotRepository) {
    this.name = name;
    client =
        new RestHighLevelClient(
            RestClient.builder(new HttpHost(host, port, "http"))
                .setRequestConfigCallback(
                    requestConfigBuilder ->
                        requestConfigBuilder.setConnectTimeout(5000).setSocketTimeout(0)));
    this.readonlySnapshotRepository = readonlySnapshotRepository;
  }

  public ElasticClient(final String name, final int port) {
    this(name, port, "localhost", false);
  }

  public void close() throws IOException {
    client.close();
  }

  public void refreshAll() throws IOException {
    log.info("Refreshing all indices of {} Elasticsearch...", name);
    client.indices().refresh(new RefreshRequest("*"), RequestOptions.DEFAULT);
    log.info("Successfully refreshed all indices of {} Elasticsearch!", name);
  }

  public void cleanIndicesAndTemplates() throws IOException {
    log.info("Wiping all indices & templates from {} Elasticsearch...", name);
    client.indices().delete(new DeleteIndexRequest("_all"), RequestOptions.DEFAULT);
    client.indices().deleteTemplate(new DeleteIndexTemplateRequest("*"), RequestOptions.DEFAULT);
    log.info("Successfully wiped all indices & templates from {} Elasticsearch!", name);
  }

  public ImmutableOpenMap<String, Settings> getSettings() throws IOException {
    return client
        .indices()
        .getSettings(
            new GetSettingsRequest().indices(DEFAULT_OPTIMIZE_INDEX_PATTERN).names(SETTINGS_FILTER),
            RequestOptions.DEFAULT)
        .getIndexToSettings();
  }

  public Map<String, MappingMetadata> getMappings() throws IOException {
    return client
        .indices()
        .getMapping(
            new GetMappingsRequest().indices(DEFAULT_OPTIMIZE_INDEX_PATTERN),
            RequestOptions.DEFAULT)
        .mappings();
  }

  public Map<String, Set<AliasMetadata>> getAliases() throws IOException {
    return client
        .indices()
        .getAlias(
            new GetAliasesRequest().indices(DEFAULT_OPTIMIZE_INDEX_PATTERN), RequestOptions.DEFAULT)
        .getAliases();
  }

  public List<IndexTemplateMetadata> getTemplates() throws IOException {
    return client
        .indices()
        .getIndexTemplate(
            new GetIndexTemplatesRequest(DEFAULT_OPTIMIZE_INDEX_PATTERN), RequestOptions.DEFAULT)
        .getIndexTemplates();
  }

  public void createSnapshotRepository() throws IOException {
    log.info("Creating snapshot repository on {} Elasticsearch...", name);
    final Settings settings =
        Settings.builder()
            .put(FsRepository.LOCATION_SETTING.getKey(), "/var/tmp")
            .put(FsRepository.COMPRESS_SETTING.getKey(), true)
            .put(FsRepository.READONLY_SETTING_KEY, readonlySnapshotRepository)
            .build();
    client
        .snapshot()
        .createRepository(
            new PutRepositoryRequest(SNAPSHOT_REPOSITORY_NAME)
                .settings(settings)
                .type(FsRepository.TYPE),
            RequestOptions.DEFAULT);
    log.info("Done creating snapshot repository on {} Elasticsearch!", name);
  }

  public void deleteSnapshotRepository() throws IOException {
    log.info("Removing snapshot repository on {} Elasticsearch...", name);
    client
        .snapshot()
        .deleteRepository(
            new DeleteRepositoryRequest().name(SNAPSHOT_REPOSITORY_NAME), RequestOptions.DEFAULT);
    log.info("Done removing snapshot repository on {} Elasticsearch!", name);
  }

  public void createSnapshotOfOptimizeIndices() throws IOException {
    log.info("Creating snapshot on {} Elasticsearch...", name);
    // using low level client for compatibility here, see
    // https://github.com/elastic/elasticsearch/pull/57661
    final Request createSnapshotRequest =
        new Request("PUT", "/_snapshot/" + SNAPSHOT_REPOSITORY_NAME + "/" + SNAPSHOT_NAME_1);
    createSnapshotRequest.addParameter("wait_for_completion", String.valueOf(true));
    createSnapshotRequest.setJsonEntity(
        "{\"indices\":\"optimize-*\",\n\"include_global_state\":true}");
    final Response response = client.getLowLevelClient().performRequest(createSnapshotRequest);
    if (HttpURLConnection.HTTP_OK != response.getStatusLine().getStatusCode()) {
      throw new RuntimeException(
          "Failed Creating Snapshot, statusCode: " + response.getStatusLine().getStatusCode());
    }
    log.info("Done creating snapshot on {} Elasticsearch!", name);
  }

  public void createAsyncSnapshot() throws IOException {
    log.info("Creating snapshot on {} Elasticsearch...", name);
    // using low level client for compatibility here, see
    // https://github.com/elastic/elasticsearch/pull/57661
    final Request createSnapshotRequest =
        new Request("PUT", "/_snapshot/" + SNAPSHOT_REPOSITORY_NAME + "/" + SNAPSHOT_NAME_2);
    createSnapshotRequest.addParameter("wait_for_completion", String.valueOf(false));
    createSnapshotRequest.setJsonEntity("{\"include_global_state\":true}");
    final Response response = client.getLowLevelClient().performRequest(createSnapshotRequest);
    if (HttpURLConnection.HTTP_OK != (response.getStatusLine().getStatusCode())) {
      throw new RuntimeException(
          "Failed Creating Snapshot, statusCode: " + response.getStatusLine().getStatusCode());
    }
    log.info("Done starting asynchronous snapshot operation on {} Elasticsearch!", name);
  }

  public void restoreSnapshot() throws IOException {
    log.info("Restoring snapshot on {} Elasticsearch...", name);
    client
        .snapshot()
        .restore(
            new RestoreSnapshotRequest(SNAPSHOT_REPOSITORY_NAME, SNAPSHOT_NAME_1)
                .includeGlobalState(true)
                .waitForCompletion(true),
            RequestOptions.DEFAULT);
    log.info("Done restoring snapshot on {} Elasticsearch!", name);
  }

  public void deleteSnapshot() throws IOException {
    deleteSnapshot(SNAPSHOT_NAME_1);
  }

  public void deleteAsyncSnapshot() throws IOException {
    deleteSnapshot(SNAPSHOT_NAME_2);
  }

  public void deleteSnapshot(final String snapshotName) throws IOException {
    log.info("Deleting snapshot {} on {} Elasticsearch...", snapshotName, name);
    client
        .snapshot()
        .delete(
            new DeleteSnapshotRequest(SNAPSHOT_REPOSITORY_NAME, snapshotName),
            RequestOptions.DEFAULT);
    log.info("Done deleting {} snapshot on {} Elasticsearch!", snapshotName, name);
  }
}
