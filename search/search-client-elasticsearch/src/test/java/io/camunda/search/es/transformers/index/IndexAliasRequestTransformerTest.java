/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.index;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import co.elastic.clients.elasticsearch.indices.GetAliasRequest;
import io.camunda.search.clients.index.IndexAliasRequest;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.util.List;
import org.junit.jupiter.api.Test;

public class IndexAliasRequestTransformerTest {

  private final ElasticsearchTransformers transformers = new ElasticsearchTransformers();

  @Test
  public void shouldConvertTo() {
    final var indices = List.of("index-1", "that-index-2");
    final var names = List.of("Name-1", "Name-2", "Name-3");
    final var request = new IndexAliasRequest(indices, names);
    final var elasticRequest =
        (GetAliasRequest) transformers.getTransformer(IndexAliasRequest.class).apply(request);
    assertThat(elasticRequest.index()).isEqualTo(indices);
    assertThat(elasticRequest.name()).isEqualTo(names);
  }
}
