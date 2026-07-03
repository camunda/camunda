/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import io.camunda.analytics.meter.MeterIdStore;
import io.camunda.analytics.meter.MeterKey;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.core.RequestBuilders;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.query.SearchQueryBuilders;
import java.util.HashMap;
import java.util.Map;

/**
 * The document-backed {@link MeterIdStore}: one {@link MeterIdDocument} per {@code aggId}
 * allocation, keyed by {@code cubeId_meterName}. {@code persist} indexes it and {@code load} reads
 * them all back — stable ids across restarts and identical across both stages, served from ES or OS
 * by the injected client.
 */
public final class DocumentMeterIdStore implements MeterIdStore {

  private static final int MAX_HITS = 10_000;

  private final DocumentBasedSearchClient searchClient;
  private final DocumentBasedWriteClient writeClient;
  private final String index;

  public DocumentMeterIdStore(
      final DocumentBasedSearchClient searchClient,
      final DocumentBasedWriteClient writeClient,
      final String index) {
    this.searchClient = searchClient;
    this.writeClient = writeClient;
    this.index = index;
  }

  @Override
  public Map<MeterKey, Integer> load() {
    final SearchQueryResponse<MeterIdDocument> response =
        searchClient.search(
            RequestBuilders.searchRequest(
                r -> r.index(index).query(SearchQueryBuilders.matchAll()).size(MAX_HITS)),
            MeterIdDocument.class);
    final Map<MeterKey, Integer> ids = new HashMap<>();
    response
        .hits()
        .forEach(
            hit -> {
              final MeterIdDocument document = hit.source();
              ids.put(new MeterKey(document.cubeId(), document.meterName()), document.aggId());
            });
    return ids;
  }

  @Override
  public void persist(final MeterKey key, final int aggId) {
    writeClient.index(
        RequestBuilders.<MeterIdDocument>indexRequest(
            r ->
                r.index(index)
                    .id(key.cubeId() + "_" + key.meterName())
                    .document(new MeterIdDocument(key.cubeId(), key.meterName(), aggId))));
  }
}
