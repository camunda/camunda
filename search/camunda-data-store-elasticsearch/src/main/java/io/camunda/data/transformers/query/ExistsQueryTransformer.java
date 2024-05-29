/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.data.transformers.query;

import co.elastic.clients.elasticsearch._types.query_dsl.ExistsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import io.camunda.data.clients.query.DataStoreExistsQuery;
import io.camunda.data.transformers.ElasticsearchTransformers;

public final class ExistsQueryTransformer
    extends QueryVariantTransformer<DataStoreExistsQuery, ExistsQuery> {

  public ExistsQueryTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public ExistsQuery apply(final DataStoreExistsQuery value) {
    final var field = value.field();
    return QueryBuilders.exists().field(field).build();
  }
}
