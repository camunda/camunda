/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.clients;

import io.camunda.search.security.SessionDocumentStorageClient;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.elasticsearch.action.admin.indices.alias.Alias;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchSessionDocumentClient implements SessionDocumentStorageClient {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchSessionDocumentClient.class);
  private static final String NUMBER_OF_SHARDS = "index.number_of_shards";
  private static final String NUMBER_OF_REPLICAS = "index.number_of_replicas";
  private final RetryElasticsearchClient client;
  private final String indexName = String.format("%s-%s-%s_", "camunda", "web-session", "1.1.0");

  public ElasticsearchSessionDocumentClient(final RetryElasticsearchClient client) {
    this.client = client;
  }

  public void setup() {
    final Map<String, Object> indexDescription =
        ElasticsearchJSONUtil.readJSONFileToMap("/camunda-web-session.json");
    client.createIndex(
        new CreateIndexRequest(indexName)
            .source(indexDescription)
            .aliases(Set.of(new Alias(indexName + "alias").writeIndex(false)))
            .settings(
                Settings.builder().put(NUMBER_OF_SHARDS, 1).put(NUMBER_OF_REPLICAS, 1).build()));
  }

  @Override
  public void consumeSessions(final Consumer<Map<String, Object>> sessionConsumer) {
    LOGGER.debug("Check for expired sessions");
    final SearchRequest searchRequest = new SearchRequest(indexName);
    client.doWithEachSearchResult(searchRequest, sh -> sessionConsumer.accept(sh.getSourceAsMap()));
  }

  @Override
  public void createOrUpdateSessionDocument(final String id, final Map<String, Object> source) {
    client.createOrUpdateDocument(indexName, id, source);
  }

  @Override
  public Map<String, Object> getSessionDocument(final String id) {
    return client.getDocument(indexName, id);
  }

  @Override
  public void deleteSessionDocument(final String id) {
    client.deleteDocument(indexName, id);
  }
}
