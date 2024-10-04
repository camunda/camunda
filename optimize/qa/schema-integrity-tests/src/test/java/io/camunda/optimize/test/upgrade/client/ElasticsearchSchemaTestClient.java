/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.upgrade.client;

import static io.camunda.optimize.service.util.mapper.ObjectMapperFactory.OPTIMIZE_MAPPER;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.analysis.TokenChar;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.DeleteTemplateRequest;
import co.elastic.clients.elasticsearch.snapshot.CreateRepositoryRequest;
import co.elastic.clients.elasticsearch.snapshot.DeleteRepositoryRequest;
import co.elastic.clients.elasticsearch.snapshot.RestoreRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.http.HttpHost;
import org.apache.http.client.methods.HttpGet;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.indices.IndexTemplateMetadata;
import org.elasticsearch.cluster.metadata.AliasMetadata;
import org.elasticsearch.cluster.metadata.MappingMetadata;

public class ElasticsearchSchemaTestClient extends AbstractDatabaseSchemaTestClient {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(ElasticsearchSchemaTestClient.class);
  RestClient restClient;
  private final ElasticsearchClient client;

  public ElasticsearchSchemaTestClient(final String name, final int port) {
    super(name);
    restClient =
        RestClient.builder(new HttpHost("localhost", port, "http"))
            .setRequestConfigCallback(
                requestConfigBuilder ->
                    requestConfigBuilder
                        .setConnectTimeout(5000)
                        // some requests like creating snapshots might take a while and we do them
                        // blocking
                        .setSocketTimeout(0))
            .build();
    client = new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
  }

  @Override
  public void close() throws IOException {
    client._transport().close();
  }

  @Override
  public void refreshAll() throws IOException {
    log.info("Refreshing all indices of {} Elasticsearch...", name);
    client
        .indices()
        .refresh(co.elastic.clients.elasticsearch.indices.RefreshRequest.of(b -> b.index("*")));
    log.info("Successfully refreshed all indices of {} Elasticsearch!", name);
  }

  @Override
  public void cleanIndicesAndTemplates() throws IOException {
    log.info("Wiping all indices & templates from {} Elasticsearch...", name);
    client.indices().delete(DeleteIndexRequest.of(b -> b.index("_all")));
    client.indices().deleteTemplate(DeleteTemplateRequest.of(b -> b.name("*")));
    log.info("Successfully wiped all indices & templates from {} Elasticsearch!", name);
  }

  @Override
  public void createSnapshotRepository() throws IOException {
    log.info("Creating snapshot repository on {} Elasticsearch...", name);
    client
        .snapshot()
        .createRepository(
            CreateRepositoryRequest.of(
                i ->
                    i.name(SNAPSHOT_REPOSITORY_NAME)
                        .repository(
                            r ->
                                r.fs(
                                    f ->
                                        f.settings(
                                            s ->
                                                s.location("/var/tmp")
                                                    .compress(true)
                                                    .readonly(false))))));
    log.info("Done creating snapshot repository on {} Elasticsearch!", name);
  }

  @Override
  public void deleteSnapshotRepository() throws IOException {
    log.info("Removing snapshot repository on {} Elasticsearch...", name);
    client
        .snapshot()
        .deleteRepository(DeleteRepositoryRequest.of(d -> d.name(SNAPSHOT_REPOSITORY_NAME)));
    log.info("Done removing snapshot repository on {} Elasticsearch!", name);
  }

  @Override
  public void createSnapshotOfOptimizeIndices() throws IOException {
    log.info("Creating snapshot on {} Elasticsearch...", name);
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
          String.format(
              "Failed Creating Snapshot, statusCode: %s",
              response.getStatusLine().getStatusCode()));
    }
    log.info("Done creating snapshot on {} Elasticsearch!", name);
  }

  @Override
  public void createAsyncSnapshot() throws IOException {
    log.info("Creating snapshot on {} Elasticsearch...", name);
    // using low level client for compatibility here, see
    // https://github.com/elastic/elasticsearch/pull/57661
    final Request createSnapshotRequest =
        new Request("PUT", "/_snapshot/" + SNAPSHOT_REPOSITORY_NAME + "/" + SNAPSHOT_NAME_2);
    createSnapshotRequest.addParameter("wait_for_completion", String.valueOf(false));
    createSnapshotRequest.setJsonEntity("{\"include_global_state\":true}");
    final Response response = restClient.performRequest(createSnapshotRequest);
    if (HttpURLConnection.HTTP_OK != response.getStatusLine().getStatusCode()) {
      throw new RuntimeException(
          String.format(
              "Failed Creating Snapshot, statusCode: %s",
              response.getStatusLine().getStatusCode()));
    }
    log.info("Done starting asynchronous snapshot operation on {} Elasticsearch!", name);
  }

  @Override
  public void restoreSnapshot() throws IOException {
    log.info("Restoring snapshot on {} Elasticsearch...", name);
    client
        .snapshot()
        .restore(
            RestoreRequest.of(
                r ->
                    r.includeGlobalState(true)
                        .waitForCompletion(true)
                        .snapshot(SNAPSHOT_NAME_1)
                        .repository(SNAPSHOT_REPOSITORY_NAME)));
    log.info("Done restoring snapshot on {} Elasticsearch!", name);
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
    log.info("Deleting snapshot {} on {} Elasticsearch...", snapshotName, name);
    client
        .snapshot()
        .delete(
            co.elastic.clients.elasticsearch.snapshot.DeleteSnapshotRequest.of(
                e -> e.repository(SNAPSHOT_REPOSITORY_NAME).snapshot(snapshotName)));
    log.info("Done deleting {} snapshot on {} Elasticsearch!", snapshotName, name);
  }

  public Map<String, Map> getSettings() throws IOException {
    final Request request =
        new Request(
            HttpGet.METHOD_NAME,
            "/" + DEFAULT_OPTIMIZE_INDEX_PATTERN + "/_settings/" + SETTINGS_FILTER);
    final Response response = restClient.performRequest(request);
    final Map<String, Map> map =
        OPTIMIZE_MAPPER.readValue(response.getEntity().getContent(), Map.class);
    for (final Map stringStringEntry : map.values()) {
      final var analysis =
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

  public Map<String, MappingMetadata> getMappings() throws IOException {
    final Request request =
        new Request(HttpGet.METHOD_NAME, "/" + DEFAULT_OPTIMIZE_INDEX_PATTERN + "/_mapping");
    final Response response = restClient.performRequest(request);
    final Map<String, MappingMetadata> value =
        OPTIMIZE_MAPPER.readValue(response.getEntity().getContent(), Map.class);
    for (final MappingMetadata mappingMetadata : value.values()) {
      final Map<String, Object> lastKpiEvaluationResults =
          (Map)
              ((Map) ((Map) mappingMetadata.getSourceAsMap().get("mappings")).get("properties"))
                  .get("lastKpiEvaluationResults");
      if (lastKpiEvaluationResults != null) {
        lastKpiEvaluationResults.put("dynamic", "true");
      }
    }
    return value;
  }

  public Map<String, Set<AliasMetadata>> getAliases() throws IOException {
    final Request request =
        new Request(HttpGet.METHOD_NAME, "/" + DEFAULT_OPTIMIZE_INDEX_PATTERN + "/_alias");
    final Response response = restClient.performRequest(request);
    final Map<String, Set<AliasMetadata>> map =
        OPTIMIZE_MAPPER.readValue(response.getEntity().getContent(), Map.class);
    return map;
  }

  public List<IndexTemplateMetadata> getTemplates() throws IOException {
    final Request request =
        new Request(HttpGet.METHOD_NAME, "/_template/" + DEFAULT_OPTIMIZE_INDEX_PATTERN);
    final Response response = restClient.performRequest(request);
    return OPTIMIZE_MAPPER.readValue(response.getEntity().getContent(), List.class);
  }
}
