/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregator;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import io.camunda.search.clients.aggregator.SearchTermsAggregator;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

/**
 * A generic base class for transforming aggregation options into Elasticsearch aggregations.
 *
 * @param <T> The type of the input aggregation option (e.g., SearchTermsAggregation)
 * @param <R> The type of the resulting Elasticsearch aggregation (e.g., TermsAggregation)
 */
public abstract class AggregationOptionTransformer<
        T extends SearchTermsAggregator, R extends Aggregation>
    extends ElasticsearchTransformer<T, R> implements SearchTransfomer<T, R> {

  public AggregationOptionTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }
}
