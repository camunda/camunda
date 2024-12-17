/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.index;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import co.elastic.clients.elasticsearch.indices.AliasDefinition;
import co.elastic.clients.elasticsearch.indices.GetAliasResponse;
import co.elastic.clients.elasticsearch.indices.get_alias.IndexAliases;
import io.camunda.search.clients.index.IndexAliasResponse;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class IndexAliasResponseTransformerTest {

  ElasticsearchTransformers transformers = new ElasticsearchTransformers();

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
