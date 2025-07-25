/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.transformers.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import co.elastic.clients.elasticsearch.core.GetRequest;
import co.elastic.clients.elasticsearch.core.GetResponse;
import io.camunda.search.clients.core.SearchGetRequest;
import io.camunda.search.clients.core.SearchGetResponse;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SearchGetRequestTransformerTest {

  private final ElasticsearchTransformers transformers = new ElasticsearchTransformers();
  private SearchTransfomer<SearchGetRequest, GetRequest> requestTransformer;
  private SearchTransfomer<GetResponse<TestDocument>, SearchGetResponse<TestDocument>>
      responseTransformer;

  @BeforeEach
  public void before() throws IOException {
    requestTransformer = transformers.getTransformer(SearchGetRequest.class);
    responseTransformer = transformers.getTransformer(SearchGetResponse.class);
  }

  @Test
  public void shouldCreateGetRequest() {
    // given
    final var searchGetRequest =
        SearchGetRequest.of(b -> b.id("foo").index("bar").routing("foobar"));

    // when
    final var result = requestTransformer.apply(searchGetRequest);

    // then
    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo("foo");
    assertThat(result.index()).isEqualTo("bar");
    assertThat(result.routing()).isEqualTo("foobar");
  }

  @Test
  public void shouldCreateGetResponse() {
    // given
    final var doc = new TestDocument("bar");
    final GetResponse<TestDocument> getResponse =
        GetResponse.of(b -> b.id("foo").index("bar").found(true).source(doc));

    // when
    final var result = responseTransformer.apply(getResponse);

    // then
    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo("foo");
    assertThat(result.index()).isEqualTo("bar");
    assertThat(result.found()).isTrue();
    assertThat(result.source()).isEqualTo(doc);
  }

  @Test
  public void shouldFailToBuildGetRequestWhenIdNotPresent() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> SearchGetRequest.of(b -> b.index("bar")));
  }

  @Test
  public void shouldFailToBuildGetRequestWhenIndexNotPresent() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> SearchGetRequest.of(b -> b.id("foo")));
  }

  record TestDocument(String id) {}
}
