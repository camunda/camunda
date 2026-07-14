/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.search;

import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import io.camunda.search.clients.core.SearchBulkIndexRequest;
import io.camunda.search.clients.core.SearchIndexRequest;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.util.List;

/**
 * Maps the neutral bulk of index requests onto one {@code _bulk} request: each item becomes an
 * index operation carrying exactly the fields the single-document {@link
 * SearchIndexRequestTransformer} maps — including the optional external version pair.
 */
public class SearchBulkIndexRequestTransformer<T>
    extends ElasticsearchTransformer<SearchBulkIndexRequest<T>, BulkRequest> {

  public SearchBulkIndexRequestTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public BulkRequest apply(final SearchBulkIndexRequest<T> value) {
    final List<BulkOperation> operations =
        value.items().stream().map(SearchBulkIndexRequestTransformer::toOperation).toList();
    return BulkRequest.of(b -> b.operations(operations));
  }

  private static <T> BulkOperation toOperation(final SearchIndexRequest<T> item) {
    final var version = item.version();
    final var versionType = toVersionType(item.versionType());
    return BulkOperation.of(
        op ->
            op.index(
                idx -> {
                  idx.index(item.index())
                      .id(item.id())
                      .routing(item.routing())
                      .document(item.document());
                  if (version != null) {
                    idx.version(version).versionType(versionType);
                  }
                  return idx;
                }));
  }

  private static VersionType toVersionType(final SearchIndexRequest.VersionType versionType) {
    if (versionType == null) {
      return null;
    }
    return switch (versionType) {
      case EXTERNAL -> VersionType.External;
      case EXTERNAL_GTE -> VersionType.ExternalGte;
    };
  }
}
