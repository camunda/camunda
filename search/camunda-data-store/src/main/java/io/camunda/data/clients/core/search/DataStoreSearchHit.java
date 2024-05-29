/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.core.search;

import io.camunda.util.DataStoreObjectBuilder;

public final class DataStoreSearchHit<T> {

  private final String id;
  private final String index;
  private final String shard;
  private final String routing;
  private final Long seqNo;
  private final Long version;
  private final T source;
  private final Object[] sortValues;

  public DataStoreSearchHit(final Builder<T> builder) {
    this.id = builder.id;
    this.index = builder.index;
    this.shard = builder.shard;
    this.routing = builder.routing;
    this.seqNo = builder.seqNo;
    this.version = builder.version;
    this.source = builder.source;
    this.sortValues = builder.sortValues;
  }

  public String id() {
    return id;
  }

  public String index() {
    return index;
  }

  public String shard() {
    return shard;
  }

  public String routing() {
    return routing;
  }

  public T source() {
    return source;
  }

  public Long seqNo() {
    return seqNo;
  }

  public Long version() {
    return version;
  }

  public Object[] sortValues() {
    return sortValues;
  }

  public static final class Builder<T> implements DataStoreObjectBuilder<DataStoreSearchHit<T>> {

    private String id;
    private String index;
    private String shard;
    private String routing;
    private Long seqNo;
    private Long version;
    private T source;
    private Object[] sortValues;

    public Builder<T> id(final String id) {
      this.id = id;
      return this;
    }

    public Builder<T> index(final String index) {
      this.index = index;
      return this;
    }

    public Builder<T> shard(final String shard) {
      this.shard = shard;
      return this;
    }

    public Builder<T> routing(final String routing) {
      this.routing = routing;
      return this;
    }

    public Builder<T> source(final T source) {
      this.source = source;
      return this;
    }

    public Builder<T> seqNo(final Long seqNo) {
      this.seqNo = seqNo;
      return this;
    }

    public Builder<T> version(final Long version) {
      this.version = version;
      return this;
    }

    public Builder<T> sortValues(final Object[] sortValues) {
      this.sortValues = sortValues;
      return this;
    }

    public DataStoreSearchHit<T> build() {
      return new DataStoreSearchHit<T>(this);
    }
  }
}
