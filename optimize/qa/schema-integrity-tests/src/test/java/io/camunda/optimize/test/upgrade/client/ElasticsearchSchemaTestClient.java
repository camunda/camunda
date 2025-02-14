/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.upgrade.client;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.analysis.TokenChar;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteTemplateRequest;
import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingRequest;
import co.elastic.clients.elasticsearch.indices.GetTemplateRequest;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import co.elastic.clients.elasticsearch.indices.TemplateMapping;
import co.elastic.clients.elasticsearch.indices.get_alias.IndexAliases;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import co.elastic.clients.elasticsearch.snapshot.CreateRepositoryRequest;
import co.elastic.clients.elasticsearch.snapshot.DeleteRepositoryRequest;
import co.elastic.clients.elasticsearch.snapshot.DeleteSnapshotRequest;
import co.elastic.clients.elasticsearch.snapshot.RestoreRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.optimize.service.util.mapper.OptimizeJacksonConfig;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpGet;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;

public class ElasticsearchSchemaTestClient extends AbstractDatabaseSchemaTestClient {

  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ElasticsearchSchemaTestClient.class);
  private final ElasticsearchClient client;
  private final RestClient restClient;

  public ElasticsearchSchemaTestClient(final String name, final int port) {
    super(name);

    restClient =
        RestClient.builder(new HttpHost("localhost", port, "http"))
            .setRequestConfigCallback(
                requestConfigBuilder ->
                    requestConfigBuilder.setConnectTimeout(5000).setSocketTimeout(0))
            .build();

    // Create the transport with a Jackson mapper
    final ElasticsearchTransport transport =
        new RestClientTransport(restClient, new JacksonJsonpMapper());

    // And create the API client
    client = new ElasticsearchClient(transport);
  }

  @Override
  public void close() throws IOException {
    client._transport().close();
  }

  @Override
  public void refreshAll() throws IOException {
    LOG.info("Refreshing all indices of {} Elasticsearch...", name);
    client.indices().refresh(RefreshRequest.of(b -> b.index("*")));
    LOG.info("Successfully refreshed all indices of {} Elasticsearch!", name);
  }

  @Override
  public void cleanIndicesAndTemplates() throws IOException {
    LOG.info("Wiping all indices & templates from {} Elasticsearch...", name);
    client.indices().delete(DeleteIndexRequest.of(b -> b.index("_all")));
    client.indices().deleteTemplate(DeleteTemplateRequest.of(b -> b.name("*")));
    LOG.info("Successfully wiped all indices & templates from {} Elasticsearch!", name);
  }

  @Override
  public void createSnapshotRepository() throws IOException {
    LOG.info("Creating snapshot repository on {} Elasticsearch...", name);
    client
        .snapshot()
        .createRepository(
            CreateRepositoryRequest.of(
                b ->
                    b.name(SNAPSHOT_REPOSITORY_NAME)
                        .repository(
                            r ->
                                r.fs(
                                    f ->
                                        f.settings(
                                            s ->
                                                s.location("/var/tmp")
                                                    .readonly(false)
                                                    .compress(true))))));
    LOG.info("Done creating snapshot repository on {} Elasticsearch!", name);
  }

  @Override
  public void deleteSnapshotRepository() throws IOException {
    LOG.info("Removing snapshot repository on {} Elasticsearch...", name);
    client
        .snapshot()
        .deleteRepository(DeleteRepositoryRequest.of(b -> b.name(SNAPSHOT_REPOSITORY_NAME)));
    LOG.info("Done removing snapshot repository on {} Elasticsearch!", name);
  }

  @Override
  public void createSnapshotOfOptimizeIndices() throws IOException {
    LOG.info("Creating snapshot on {} Elasticsearch...", name);
    // using low level client for compatibility here, see
    // https://github.com/elastic/elasticsearch/pull/57661
    final Request createSnapshotRequest =
        new Request("PUT", "/_snapshot/" + SNAPSHOT_REPOSITORY_NAME + "/" + SNAPSHOT_NAME_1);
    createSnapshotRequest.addParameter("wait_for_completion", String.valueOf(true));
    createSnapshotRequest.setJsonEntity(
        "{\"indices\":\"optimize-*\",\n\"include_global_state\":true}");
    final Response response = restClient.performRequest(createSnapshotRequest);
    if (HttpURLConnection.HTTP_OK != response.getStatusLine().getStatusCode()) {
      throw new RuntimeException(
          "Failed Creating Snapshot, statusCode: " + response.getStatusLine().getStatusCode());
    }
    LOG.info("Done creating snapshot on {} Elasticsearch!", name);
  }

  @Override
  public void createAsyncSnapshot() throws IOException {
    LOG.info("Creating snapshot on {} Elasticsearch...", name);
    // using low level client for compatibility here, see
    // https://github.com/elastic/elasticsearch/pull/57661
    final Request createSnapshotRequest =
        new Request("PUT", "/_snapshot/" + SNAPSHOT_REPOSITORY_NAME + "/" + SNAPSHOT_NAME_2);
    createSnapshotRequest.addParameter("wait_for_completion", String.valueOf(false));
    createSnapshotRequest.setJsonEntity("{\"include_global_state\":true}");
    final Response response = restClient.performRequest(createSnapshotRequest);
    if (HttpURLConnection.HTTP_OK != (response.getStatusLine().getStatusCode())) {
      throw new RuntimeException(
          "Failed Creating Snapshot, statusCode: " + response.getStatusLine().getStatusCode());
    }
    LOG.info("Done starting asynchronous snapshot operation on {} Elasticsearch!", name);
  }

  @Override
  public void restoreSnapshot() throws IOException {
    LOG.info("Restoring snapshot on {} Elasticsearch...", name);
    client
        .snapshot()
        .restore(
            RestoreRequest.of(
                b ->
                    b.repository(SNAPSHOT_REPOSITORY_NAME)
                        .snapshot(SNAPSHOT_NAME_1)
                        .includeGlobalState(true)
                        .waitForCompletion(true)));
    LOG.info("Done restoring snapshot on {} Elasticsearch!", name);
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
    LOG.info("Deleting snapshot {} on {} Elasticsearch...", snapshotName, name);
    client
        .snapshot()
        .delete(
            DeleteSnapshotRequest.of(
                b -> b.snapshot(snapshotName).repository(SNAPSHOT_REPOSITORY_NAME)));
    LOG.info("Done deleting {} snapshot on {} Elasticsearch!", snapshotName, name);
  }

  public Map<String, Map> getSettings() throws IOException {
    final Request request =
        new Request(
            HttpGet.METHOD_NAME,
            "/"
                + DEFAULT_OPTIMIZE_INDEX_PATTERN
                + "/_settings/"
                + String.join(",", SETTINGS_FILTER));
    final Response response = restClient.performRequest(request);
    final OptimizeJacksonConfig optimizeJacksonConfig = new OptimizeJacksonConfig();
    final Map<String, Map> map =
        optimizeJacksonConfig
            .objectMapper()
            .readValue(response.getEntity().getContent(), Map.class);
    for (final Map stringStringEntry : map.values()) {
      final Map analysis =
          (Map) ((Map) ((Map) stringStringEntry.get("settings")).get("index")).get("analysis");
      final Map analyzer = (Map) ((Map) analysis.get("analyzer")).get("is_present_analyzer");
      if (!List.class.isInstance(analyzer.get("filter"))) {
        analyzer.put("filter", List.of(analyzer.get("filter")));
      }
      final Map lowercaseNgram = (Map) ((Map) analysis.get("analyzer")).get("lowercase_ngram");
      if (!List.class.isInstance(lowercaseNgram.get("filter"))) {
        lowercaseNgram.put("filter", List.of(lowercaseNgram.get("filter")));
      }
      final Map ngramTokenizer = (Map) ((Map) analysis.get("tokenizer")).get("ngram_tokenizer");
      if (!ngramTokenizer.containsKey("token_chars")) {
        ngramTokenizer.put(
            "token_chars",
            List.of(
                TokenChar.Letter.jsonValue(),
                TokenChar.Digit.jsonValue(),
                TokenChar.Whitespace.jsonValue(),
                TokenChar.Punctuation.jsonValue(),
                TokenChar.Symbol.jsonValue()));
      }
    }
    return map;
  }

  public Map<String, IndexMappingRecord> getMappings() throws IOException {
    return client
        .indices()
        .getMapping(GetMappingRequest.of(b -> b.index(DEFAULT_OPTIMIZE_INDEX_PATTERN)))
        .result();
  }

  public Map<String, IndexAliases> getAliases() throws IOException {
    return client
        .indices()
        .getAlias(GetAliasRequest.of(b -> b.index(DEFAULT_OPTIMIZE_INDEX_PATTERN)))
        .result();
  }

  public Map<String, TemplateMapping> getTemplates() throws IOException {
    return client
        .indices()
        .getTemplate(
            GetTemplateRequest.of(b -> b.name(List.of(DEFAULT_OPTIMIZE_INDEX_PATTERN.split(",")))))
        .result();
  }
}
