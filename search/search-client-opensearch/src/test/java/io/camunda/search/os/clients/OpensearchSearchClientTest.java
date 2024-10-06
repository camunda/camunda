/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.clients;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.os.util.StubbedOpensearchClient;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHitsRelation;

public class OpensearchSearchClientTest {

  private OpensearchSearchClient client;
  private StubbedOpensearchClient stubbedOpensearchClient;

  @BeforeEach
  public void before() {
    stubbedOpensearchClient = new StubbedOpensearchClient();
    stubbedOpensearchClient.registerHandler(
        (h) -> {
          return SearchResponse.searchResponseOf(
              (f) ->
                  f.took(122)
                      .hits(
                          HitsMetadata.of(
                              (m) ->
                                  m.hits(new ArrayList<>())
                                      .total((t) -> t.value(789).relation(TotalHitsRelation.Eq))))
                      .shards((s) -> s.failed(0).successful(100).total(100))
                      .timedOut(false));
        });

    client = new OpensearchSearchClient(stubbedOpensearchClient);
  }

  @Test
  public void shouldTransformSearchRequest() {
    // given
    final SearchQueryRequest request =
        SearchQueryRequest.of(b -> b.index("operate-list-view-8.3.0_").size(1));

    // when
    client.search(request, Object.class);

    // then
    final var searchRequest = stubbedOpensearchClient.getSingleSearchRequest();
    assertThat(searchRequest.index()).hasSize(1).contains("operate-list-view-8.3.0_");
  }

  @Test
  public void shouldTransformSearchResponse() {
    // given
    final SearchQueryRequest request =
        SearchQueryRequest.of(b -> b.index("operate-list-view-8.3.0_").size(1));

    // when
    final var response = client.search(request, Object.class);

    // then
    assertThat(response).isNotNull();
    assertThat(response.totalHits()).isEqualTo(789);
  }
}
