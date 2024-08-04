/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.transformers.SearchTransfomer;
import org.junit.jupiter.api.Test;

public class ElasticsearchTransfomersTest {

  @Test
  public void should() {
    // given
    final SearchQueryRequest request =
        SearchQueryRequest.of(b -> b.index("operate-list-view-8.3.0_").size(1));

    final ElasticsearchTransformers elasticsearchTransformers = new ElasticsearchTransformers();

    // when
    final SearchTransfomer<SearchQueryRequest, SearchRequest> transformer;
    transformer = elasticsearchTransformers.getTransformer(SearchQueryRequest.class);
    final SearchRequest actual = transformer.apply(request);

    // then
    assertThat(actual.index()).hasSize(1).contains("operate-list-view-8.3.0_");
  }
}
