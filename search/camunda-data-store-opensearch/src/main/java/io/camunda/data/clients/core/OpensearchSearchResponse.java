/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.core;

import io.camunda.data.clients.core.search.DataStoreSearchHit;
import io.camunda.data.clients.core.search.OpensearchSearchHit;
import java.util.Collections;
import java.util.List;
import org.opensearch.client.opensearch.core.search.SearchResult;

public class OpensearchSearchResponse<T> implements DataStoreSearchResponse<T> {

  private final long totalHits;
  private final String scrollId;
  private final List<DataStoreSearchHit<T>> hits;

  public OpensearchSearchResponse(final Builder<T> builder) {
    this.totalHits = builder.totalHits;
    this.scrollId = builder.scrollId;
    this.hits = builder.hits;
  }

  @Override
  public List<DataStoreSearchHit<T>> hits() {
    if (hits != null) {
      return hits;
    } else {
      return Collections.emptyList();
    }
  }

  @Override
  public long totalHits() {
    return totalHits;
  }

  @Override
  public String scrollId() {
    return scrollId;
  }

  public static <T> DataStoreSearchResponse<T> from(final SearchResult<T> response) {
    final var builder = new OpensearchSearchResponse.Builder<T>();

    final var totalHits = response.hits().total().value();
    final var scrollId = response.scrollId();
    final var hits = response.hits().hits().stream().map(OpensearchSearchHit::from).toList();

    return builder.totalHits(totalHits).scrollId(scrollId).hits(hits).build();
  }

  public static final class Builder<T> implements DataStoreSearchResponse.Builder<T> {

    private long totalHits;
    private String scrollId;
    private List<DataStoreSearchHit<T>> hits;

    public Builder<T> totalHits(final long totalHits) {
      this.totalHits = totalHits;
      return this;
    }

    public Builder<T> scrollId(final String scrollId) {
      this.scrollId = scrollId;
      return this;
    }

    @Override
    public Builder<T> hits(final List<DataStoreSearchHit<T>> hits) {
      this.hits = hits;
      return this;
    }

    @Override
    public DataStoreSearchResponse<T> build() {
      return new OpensearchSearchResponse<T>(this);
    }
  }
}
