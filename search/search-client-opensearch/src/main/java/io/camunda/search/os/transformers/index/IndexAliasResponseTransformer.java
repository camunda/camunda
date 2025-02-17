/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.index;

import io.camunda.search.clients.index.IndexAliasResponse;
import io.camunda.search.os.transformers.OpensearchTransformer;
import io.camunda.search.os.transformers.OpensearchTransformers;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.indices.AliasDefinition;
import org.opensearch.client.opensearch.indices.GetAliasResponse;
import org.opensearch.client.opensearch.indices.get_alias.IndexAliases;

public class IndexAliasResponseTransformer
    extends OpensearchTransformer<GetAliasResponse, IndexAliasResponse> {

  public IndexAliasResponseTransformer(final OpensearchTransformers transformers) {
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
