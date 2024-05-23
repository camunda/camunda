/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.clients.core.search;

import io.camunda.util.DataStoreObjectBuilder;

public interface DataStoreSearchHit<T> {

  public String id();

  public String index();

  public String shard();

  public String routing();

  public T source();

  public Long seqNo();

  public Long version();

  public Object[] sortValues();

  public interface Builder<T> extends DataStoreObjectBuilder<DataStoreSearchHit<T>> {

    Builder<T> id(final String id);

    Builder<T> index(final String index);

    Builder<T> shard(final String shard);

    Builder<T> routing(final String routing);

    Builder<T> source(final T source);

    Builder<T> seqNo(final Long seqNo);

    Builder<T> version(final Long version);

    Builder<T> sortValues(final Object[] sortValues);
  }
}
