/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.core;

import io.camunda.util.ObjectBuilder;

public final record SearchQueryHit<T>(
    String id,
    String index,
    String shard,
    String routing,
    Long seqNo,
    Long version,
    T source,
    Object[] sortValues) {

  public static final class Builder<T> implements ObjectBuilder<SearchQueryHit<T>> {

    private String id;
    private String index;
    private String shard;
    private String routing;
    private Long seqNo;
    private Long version;
    private T source;
    private Object[] sortValues;

    public Builder<T> id(final String value) {
      id = value;
      return this;
    }

    public Builder<T> index(final String value) {
      index = value;
      return this;
    }

    public Builder<T> shard(final String value) {
      shard = value;
      return this;
    }

    public Builder<T> routing(final String value) {
      routing = value;
      return this;
    }

    public Builder<T> source(final T value) {
      source = value;
      return this;
    }

    public Builder<T> seqNo(final Long value) {
      seqNo = value;
      return this;
    }

    public Builder<T> version(final Long value) {
      version = value;
      return this;
    }

    public Builder<T> sortValues(final Object[] values) {
      sortValues = values;
      return this;
    }

    public SearchQueryHit<T> build() {
      return new SearchQueryHit<T>(id, index, shard, routing, seqNo, version, source, sortValues);
    }
  }
}
