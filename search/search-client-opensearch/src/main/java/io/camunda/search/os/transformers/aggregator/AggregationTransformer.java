/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.aggregator;

import io.camunda.search.clients.aggregator.SearchAggregator;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.os.transformers.OpensearchTransformer;
import io.camunda.search.os.transformers.OpensearchTransformers;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;

/**
 * A generic base class for transforming aggregation options into Elasticsearch aggregations.
 *
 * @param <T> The type of the input aggregation option (e.g., SearchTermsAggregation)
 * @param <R> The type of the resulting Elasticsearch aggregation (e.g., TermsAggregation)
 */
public abstract class AggregationTransformer<T extends SearchAggregator, R extends Aggregation>
    extends OpensearchTransformer<T, R> implements SearchTransfomer<T, R> {

  public AggregationTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }
}
