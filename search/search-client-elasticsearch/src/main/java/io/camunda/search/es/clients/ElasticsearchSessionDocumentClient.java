/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.clients;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest.Builder;
import io.camunda.search.security.SessionDocumentStorageClient;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchSessionDocumentClient implements SessionDocumentStorageClient {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchSessionDocumentClient.class);
  private final RetryElasticsearchClient client;
  private final String indexName = String.format("%s-%s-%s_", "camunda", "web-session", "1.1.0");

  public ElasticsearchSessionDocumentClient(final RetryElasticsearchClient client) {
    this.client = client;
  }

  public void setup() throws IOException {
    client.createIndex(indexName, indexName + "alias", 1, 1, "/camunda-web-session.json");
  }

  @Override
  public void consumeSessions(final Consumer<Map<String, Object>> sessionConsumer) {
    LOGGER.debug("Check for expired sessions");
    final SearchRequest searchRequest = new Builder().index(indexName).build();
    client.doWithEachSearchResult(searchRequest, sh -> sessionConsumer.accept((Map) sh.source()));
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
