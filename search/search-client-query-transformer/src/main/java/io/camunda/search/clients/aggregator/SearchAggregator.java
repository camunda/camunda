/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.aggregator;

import static io.camunda.util.CollectionUtil.collectValues;

import java.util.List;

public interface SearchAggregator {

  String getName();

  List<SearchAggregator> getAggregations();

  abstract class AbstractBuilder<T> {

    protected String name = null;
    protected List<SearchAggregator> aggregations = null;

    protected abstract T self();

    public T name(final String value) {
      name = value;

      return self();
    }

    public T aggregations(final List<SearchAggregator> aggregations) {
      this.aggregations = aggregations;
      return self();
    }

    public T aggregations(final SearchAggregator value, final SearchAggregator... values) {
      return aggregations(collectValues(value, values));
    }
  }
}
