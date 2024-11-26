/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.search;

import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.WriteResponseBase;
import io.camunda.search.clients.core.SearchWriteResponse;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public class SearchWriteResponseTransformer
    extends ElasticsearchTransformer<WriteResponseBase, SearchWriteResponse> {

  public SearchWriteResponseTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public SearchWriteResponse apply(final WriteResponseBase value) {
    final var id = value.id();
    final var index = value.index();
    final var result = toSearchWriteResult(value.result());
    return SearchWriteResponse.of(b -> b.id(id).index(index).result(result));
  }

  private SearchWriteResponse.Result toSearchWriteResult(final Result result) {
    return switch (result) {
      case Created -> SearchWriteResponse.Result.CREATED;
      case Updated -> SearchWriteResponse.Result.UPDATED;
      case Deleted -> SearchWriteResponse.Result.DELETED;
      case NotFound -> SearchWriteResponse.Result.NOT_FOUND;
      default -> SearchWriteResponse.Result.NOOP;
    };
  }
}
