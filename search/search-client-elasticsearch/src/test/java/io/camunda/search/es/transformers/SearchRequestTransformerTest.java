/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers;

import static org.assertj.core.api.Assertions.assertThat;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.transformers.SearchTransfomer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SearchRequestTransformerTest {

  private final ElasticsearchTransformers transformers = new ElasticsearchTransformers();
  private SearchTransfomer<SearchQueryRequest, SearchRequest> transformer;

  @BeforeEach
  public void before() {
    transformer = transformers.getTransformer(SearchQueryRequest.class);
  }

  @Test
  public void shouldTransformEmptySearchRequest() {
    // given
    final SearchQueryRequest request =
        SearchQueryRequest.of(b -> b.index("operate-list-view-8.3.0_"));

    // when
    final SearchRequest actual = transformer.apply(request);

    // then
    assertThat(actual.index()).hasSize(1).contains("operate-list-view-8.3.0_");
  }

  @Test
  public void shouldTransformSort() {
    // given
    final SearchQueryRequest request =
        SearchQueryRequest.of(
            b ->
                b.index("operate-list-view-8.3.0_")
                    .sort((s) -> s.field((f) -> f.field("abc").asc())));

    // when
    final SearchRequest actual = transformer.apply(request);

    // then
    assertThat(actual.index()).hasSize(1).contains("operate-list-view-8.3.0_");
    assertThat(actual.sort()).hasSize(1);
    assertThat(actual.sort().get(0).field().field()).isEqualTo("abc");
    assertThat(actual.sort().get(0).field().order()).isEqualTo(SortOrder.Asc);
  }
}
