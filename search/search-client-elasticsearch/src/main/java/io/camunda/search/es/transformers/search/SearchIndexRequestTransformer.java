/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.search;

import co.elastic.clients.elasticsearch._types.VersionType;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import io.camunda.search.clients.core.SearchIndexRequest;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;

public class SearchIndexRequestTransformer<T>
    extends ElasticsearchTransformer<SearchIndexRequest<T>, IndexRequest<T>> {

  public SearchIndexRequestTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public IndexRequest<T> apply(final SearchIndexRequest<T> value) {
    final var id = value.id();
    final var index = value.index();
    final var routing = value.routing();
    final var document = value.document();
    final var version = value.version();
    final var versionType = toVersionType(value.versionType());
    return IndexRequest.of(
        b -> {
          b.id(id).index(index).routing(routing).document(document);
          if (version != null) {
            b.version(version).versionType(versionType);
          }
          return b;
        });
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
