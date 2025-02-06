/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.index;

import co.elastic.clients.elasticsearch.indices.AliasDefinition;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.get_alias.IndexAliases;
import io.camunda.search.clients.index.IndexAliasResponse;
import io.camunda.search.es.transformers.ElasticsearchTransformer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class IndexAliasResponseTransformer
    extends ElasticsearchTransformer<GetAliasResponse, IndexAliasResponse> {

  public IndexAliasResponseTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public IndexAliasResponse apply(final GetAliasResponse value) {
    return new IndexAliasResponse(
        value.result().entrySet().stream()
            .collect(Collectors.toUnmodifiableMap(Entry::getKey, e -> transform(e.getValue()))));
  }

  private static IndexAliasResponse.IndexAliases transform(final IndexAliases aliases) {
    return new IndexAliasResponse.IndexAliases(
        aliases.aliases().entrySet().stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    Entry::getKey, e -> transformDefinition(e.getValue()))));
  }

  private static IndexAliasResponse.AliasDefinition transformDefinition(
      final AliasDefinition alias) {
    return new IndexAliasResponse.AliasDefinition(
        alias.indexRouting(), alias.isWriteIndex(), alias.routing(), alias.searchRouting());
  }
}
