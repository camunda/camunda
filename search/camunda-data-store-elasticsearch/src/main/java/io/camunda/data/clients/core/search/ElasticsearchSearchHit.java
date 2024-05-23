/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.core.search;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.search.Hit;

public final class ElasticsearchSearchHit<T> implements DataStoreSearchHit<T> {

  private final String id;
  private final String index;
  private final String shard;
  private final String routing;
  private final Long seqNo;
  private final Long version;
  private final T source;
  private final Object[] sortValues;

  public ElasticsearchSearchHit(final Builder<T> builder) {
    this.id = builder.id;
    this.index = builder.index;
    this.shard = builder.shard;
    this.routing = builder.routing;
    this.seqNo = builder.seqNo;
    this.version = builder.version;
    this.source = builder.source;
    this.sortValues = builder.sortValues;
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public String index() {
    return index;
  }

  @Override
  public String shard() {
    return shard;
  }

  @Override
  public String routing() {
    return routing;
  }

  @Override
  public T source() {
    return source;
  }

  @Override
  public Long seqNo() {
    return seqNo;
  }

  @Override
  public Long version() {
    return version;
  }

  @Override
  public Object[] sortValues() {
    return sortValues;
  }

  public static <T> DataStoreSearchHit<T> from(final Hit<T> hit) {
    final var sortValues = hit.sort().stream().map(FieldValue::_get).toArray();
    return new Builder<T>()
        .id(hit.id())
        .index(hit.index())
        .shard(hit.shard())
        .routing(hit.routing())
        .seqNo(hit.seqNo())
        .version(hit.version())
        .source(hit.source())
        .sortValues(sortValues)
        .build();
  }

  public static final class Builder<T> implements DataStoreSearchHit.Builder<T> {

    private String id;
    private String index;
    private String shard;
    private String routing;
    private Long seqNo;
    private Long version;
    private T source;
    private Object[] sortValues;

    @Override
    public Builder<T> id(final String id) {
      this.id = id;
      return this;
    }

    @Override
    public Builder<T> index(final String index) {
      this.index = index;
      return this;
    }

    @Override
    public Builder<T> shard(final String shard) {
      this.shard = shard;
      return this;
    }

    @Override
    public Builder<T> routing(final String routing) {
      this.routing = routing;
      return this;
    }

    @Override
    public Builder<T> source(final T source) {
      this.source = source;
      return this;
    }

    @Override
    public Builder<T> seqNo(final Long seqNo) {
      this.seqNo = seqNo;
      return this;
    }

    @Override
    public Builder<T> version(final Long version) {
      this.version = version;
      return this;
    }

    @Override
    public Builder<T> sortValues(final Object[] sortValues) {
      this.sortValues = sortValues;
      return this;
    }

    @Override
    public DataStoreSearchHit<T> build() {
      return new ElasticsearchSearchHit<T>(this);
    }
  }
}
