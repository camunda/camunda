/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.reader;

import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import java.util.Iterator;
import java.util.List;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;

class SearchAfterIterator<T> implements Iterator<List<T>> {
  private final OptimizeOpenSearchClient osClient;
  private final SearchRequest searchRequest;
  private final Class<T> itemClass;
  private final String errorMessage;

  private List<T> current;
  private List<String> lastSortValues;
  private boolean finished = false;

  SearchAfterIterator(
      final OptimizeOpenSearchClient osClient,
      final SearchRequest searchRequest,
      final Class<T> itemClass,
      final String errorMessage) {
    this.osClient = osClient;
    this.searchRequest = searchRequest;
    this.itemClass = itemClass;
    this.errorMessage = errorMessage;
  }

  @Override
  public boolean hasNext() {
    if (finished) {
      return false;
    }
    SearchRequest.Builder nextSearch = searchRequest.toBuilder();
    if (lastSortValues != null) {
      nextSearch = nextSearch.searchAfter(lastSortValues);
    }
    final SearchResponse<T> response = osClient.search(nextSearch, itemClass, errorMessage);
    final List<Hit<T>> hits = response.hits().hits();
    if (!hits.isEmpty()) {
      current = hits.stream().map(Hit::source).filter(java.util.Objects::nonNull).toList();
      lastSortValues = hits.getLast().sort();
    } else {
      finished = true;
      return false;
    }
    return true;
  }

  @Override
  public List<T> next() {
    return current;
  }
}
