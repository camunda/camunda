/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.aggregation;

import co.elastic.clients.elasticsearch._types.aggregations.AggregateVariant;
import io.camunda.search.clients.aggregation.SearchAggregateOption;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.transformers.SearchTransfomer;

public abstract class AggregateOptionTransformer<
        T extends AggregateVariant, R extends SearchAggregateOption>
    extends ElasticsearchTransformer<T, R> implements SearchTransfomer<T, R> {

  public AggregateOptionTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }
}
