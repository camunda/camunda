/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.index;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.search.clients.index.IndexAliasResponse;
import io.camunda.search.os.transformers.OpensearchTransformers;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.indices.AliasDefinition;
import org.opensearch.client.opensearch.indices.GetAliasResponse;
import org.opensearch.client.opensearch.indices.get_alias.IndexAliases;

public class IndexAliasResponseTransformerTest {

  private final OpensearchTransformers transformers = new OpensearchTransformers();

  @Test
  public void shouldDecodeCorrectly() {
    final var aliasDefinition =
        AliasDefinition.of(
            bad ->
                bad.routing("routing")
                    .indexRouting("index-routing")
                    .searchRouting("search-routing"));
    final var response =
        GetAliasResponse.of(
            b ->
                b.result(
                    Map.of(
                        "index-1",
                        IndexAliases.of(bia -> bia.aliases(Map.of("alias-1", aliasDefinition))))));

    final var transformer = transformers.getTransformer(IndexAliasResponse.class);
    final var aliasResponse = (IndexAliasResponse) transformer.apply(response);
    final var definition = aliasResponse.indices().get("index-1").aliases().get("alias-1");
    assertThat(definition.routing()).isEqualTo("routing");
    assertThat(definition.indexRouting()).isEqualTo("index-routing");
    assertThat(definition.searchRouting()).isEqualTo("search-routing");
  }
}
