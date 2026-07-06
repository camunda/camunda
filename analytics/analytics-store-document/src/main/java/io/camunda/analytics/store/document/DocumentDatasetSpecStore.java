/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.document;

import io.camunda.analytics.dataset.RegisteredDataset;
import io.camunda.analytics.serving.spi.DatasetSpecQuery;
import io.camunda.analytics.serving.spi.DatasetSpecStore;
import io.camunda.search.clients.DocumentBasedSearchClient;
import io.camunda.search.clients.DocumentBasedWriteClient;
import io.camunda.search.clients.core.RequestBuilders;
import io.camunda.search.clients.core.SearchGetResponse;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The document-backed {@link DatasetSpecStore}: one implementation over OC's neutral {@link
 * DocumentBasedSearchClient}/{@link DocumentBasedWriteClient}, serving both Elasticsearch and
 * OpenSearch (the backend is whichever client is injected — no per-backend code here). The {@link
 * RegisteredDataset} is stored <b>directly</b> as the document (the client's mapper serializes it);
 * {@code create} indexes it by {@code cubeId}, {@code read} gets it by id, and {@code search}
 * builds neutral term filters from the {@link DatasetSpecQuery} over the declaration's fields.
 */
public final class DocumentDatasetSpecStore implements DatasetSpecStore {

  private static final int MAX_HITS = 10_000;

  private final DocumentBasedSearchClient searchClient;
  private final DocumentBasedWriteClient writeClient;
  private final String index;

  public DocumentDatasetSpecStore(
      final DocumentBasedSearchClient searchClient,
      final DocumentBasedWriteClient writeClient,
      final String index) {
    this.searchClient = searchClient;
    this.writeClient = writeClient;
    this.index = index;
  }

  @Override
  public boolean isEmpty() {
    return searchClient
            .search(
                RequestBuilders.searchRequest(
                    r -> r.index(index).query(SearchQueryBuilders.matchAll()).size(0)),
                RegisteredDataset.class)
            .totalHits()
        == 0;
  }

  @Override
  public void create(final RegisteredDataset spec) {
    writeClient.index(
        RequestBuilders.<RegisteredDataset>indexRequest(
            r -> r.index(index).id(Long.toString(spec.cubeId())).document(spec)));
  }

  @Override
  public Optional<RegisteredDataset> read(final long cubeId) {
    final SearchGetResponse<RegisteredDataset> response =
        searchClient.get(
            RequestBuilders.getRequest(r -> r.index(index).id(Long.toString(cubeId))),
            RegisteredDataset.class);
    return response.found() ? Optional.of(response.source()) : Optional.empty();
  }

  @Override
  public List<RegisteredDataset> search(final DatasetSpecQuery query) {
    final List<SearchQuery> filters = new ArrayList<>();
    if (query.name() != null) {
      filters.add(SearchQueryBuilders.term("declaration.name", query.name()));
    }
    if (query.sourceFact() != null) {
      filters.add(SearchQueryBuilders.term("declaration.sourceFact", query.sourceFact().name()));
    }
    if (query.kind() != null) {
      filters.add(SearchQueryBuilders.term("declaration.kind", query.kind().name()));
    }
    final SearchQuery filter =
        filters.isEmpty() ? SearchQueryBuilders.matchAll() : SearchQueryBuilders.and(filters);

    final SearchQueryResponse<RegisteredDataset> response =
        searchClient.search(
            RequestBuilders.searchRequest(r -> r.index(index).query(filter).size(MAX_HITS)),
            RegisteredDataset.class);
    final List<RegisteredDataset> specs = new ArrayList<>(response.hits().size());
    response.hits().forEach(hit -> specs.add(hit.source()));
    return specs;
  }
}
