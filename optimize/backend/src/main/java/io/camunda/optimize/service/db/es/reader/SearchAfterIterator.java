/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.reader;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.List;

class SearchAfterIterator<T> implements Iterator<List<T>> {
  private final OptimizeElasticsearchClient esClient;
  private final SearchRequest searchRequest;
  private final Class<T> itemClass;

  private List<T> current;
  private List<FieldValue> lastSortValues;
  private boolean finished = false;

  SearchAfterIterator(
      final OptimizeElasticsearchClient esClient,
      final SearchRequest searchRequest,
      final Class<T> itemClass) {
    this.esClient = esClient;
    this.searchRequest = searchRequest;
    this.itemClass = itemClass;
  }

  @Override
  public boolean hasNext() {
    if (finished) {
      return false;
    }
    SearchRequest nextSearch = searchRequest;
    if (lastSortValues != null) {
      nextSearch = searchRequest.rebuild().searchAfter(lastSortValues).build();
    }
    try {
      final SearchResponse<T> response = esClient.search(nextSearch, itemClass);
      final List<Hit<T>> hits = response.hits().hits();
      if (!hits.isEmpty()) {
        current = hits.stream().map(Hit::source).filter(java.util.Objects::nonNull).toList();
        lastSortValues = hits.getLast().sort();
      } else {
        finished = true;
        return false;
      }
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
    return true;
  }

  @Override
  public List<T> next() {
    return current;
  }
}
