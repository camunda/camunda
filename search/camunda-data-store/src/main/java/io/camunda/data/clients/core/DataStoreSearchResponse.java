/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.core;

import io.camunda.data.clients.core.search.DataStoreSearchHit;
import io.camunda.util.DataStoreObjectBuilder;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public final class DataStoreSearchResponse<T> {

  private final long totalHits;
  private final String scrollId;
  private final List<DataStoreSearchHit<T>> hits;

  public DataStoreSearchResponse(final Builder<T> builder) {
    this.totalHits = builder.totalHits;
    this.scrollId = builder.scrollId;
    this.hits = builder.hits;
  }

  public List<DataStoreSearchHit<T>> hits() {
    if (hits != null) {
      return hits;
    } else {
      return Collections.emptyList();
    }
  }

  public long totalHits() {
    return totalHits;
  }

  public String scrollId() {
    return scrollId;
  }

  public static <T> DataStoreSearchResponse<T> of(
      final Function<Builder<T>, DataStoreObjectBuilder<DataStoreSearchResponse<T>>> fn) {
    return fn.apply(new Builder<T>()).build();
  }

  public static final class Builder<T>
      implements DataStoreObjectBuilder<DataStoreSearchResponse<T>> {

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

    public Builder<T> hits(final List<DataStoreSearchHit<T>> hits) {
      this.hits = hits;
      return this;
    }

    @Override
    public DataStoreSearchResponse<T> build() {
      return new DataStoreSearchResponse<T>(this);
    }
  }
}
