/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.clients;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch.core.ClearScrollRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.ScrollRequest;
import co.elastic.clients.elasticsearch.core.ScrollResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest.Builder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.search.security.SessionDocumentStorageClient;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElasticsearchSessionDocumentClient implements SessionDocumentStorageClient {

  public static final int SCROLL_KEEP_ALIVE_MS = 60_000;
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchSessionDocumentClient.class);
  private final ElasticsearchClient client;
  private final String indexName = String.format("%s-%s-%s_", "identity", "web-session", "8.7.0");

  public ElasticsearchSessionDocumentClient(final ElasticsearchClient client) {
    this.client = client;
  }

  @Override
  public void consumeSessions(final Consumer<Map<String, Object>> sessionConsumer) {
    LOGGER.debug("Check for expired sessions");
    final SearchRequest searchRequest = new Builder().index(indexName).build();

    try {
      doWithEachSearchResult(searchRequest, sh -> sessionConsumer.accept((Map) sh.source()));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void createOrUpdateSessionDocument(final String id, final Map<String, Object> source) {
    try {
      client.index(i -> i.index(indexName).id(id).document(source));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Map<String, Object> getSessionDocument(final String id) {
    final GetResponse<Map> response;
    try {
      response = client.get(s -> s.index(indexName).id(id), Map.class);
      return (Map<String, Object>) response.source();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void deleteSessionDocument(final String id) {
    try {
      client.delete(i -> i.index(indexName).id(id));
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

  private int doWithEachSearchResult(
      final SearchRequest searchRequest, final Consumer<Hit> searchHitConsumer) throws IOException {

    final SearchResponse<Map> searchResponse = client.search(searchRequest, Map.class);

    final List<Hit<Map>> hits = searchResponse.hits().hits();

    int doneOnSearchHits = 0;
    searchRequest.scroll();
    final SearchResponse<Map> response = client.search(searchRequest, Map.class);
    hits.forEach(searchHitConsumer);
    doneOnSearchHits += response.hits().hits().size();

    String scrollId = null;
    ScrollResponse<Map> scrollResponse = null;
    do {
      scrollId = scrollResponse != null ? scrollResponse.scrollId() : response.scrollId();
      if (scrollId != null) {
        scrollResponse =
            client.scroll(
                new ScrollRequest.Builder()
                    .scrollId(scrollId)
                    .scroll(
                        Time.of(
                            builder ->
                                builder.time(
                                    Instant.ofEpochMilli(SCROLL_KEEP_ALIVE_MS).toString())))
                    .build(),
                Map.class);
        searchResponse.hits().hits().forEach(searchHitConsumer);
        doneOnSearchHits += scrollResponse.hits().hits().size();
      }
    } while (scrollResponse != null && !scrollResponse.hits().hits().isEmpty());
    if (scrollId != null) {
      final ClearScrollRequest clearScrollRequest =
          new ClearScrollRequest.Builder().scrollId(scrollId).build();
      client.clearScroll(clearScrollRequest);
    }
    return doneOnSearchHits;
  }
}
