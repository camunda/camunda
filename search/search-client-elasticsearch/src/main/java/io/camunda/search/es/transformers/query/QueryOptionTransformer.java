/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.query;

import co.elastic.clients.elasticsearch._types.query_dsl.QueryVariant;
import io.camunda.search.clients.query.SearchQueryOption;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public abstract class QueryOptionTransformer<T extends SearchQueryOption, R extends QueryVariant>
    extends ElasticsearchTransformer<T, R> implements SearchTransfomer<T, R> {

  public QueryOptionTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }
}
