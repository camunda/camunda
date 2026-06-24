/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.transformers.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.search.clients.core.SearchIndexRequest;
import io.camunda.search.clients.core.SearchWriteResponse;
import io.camunda.search.clients.transformers.SearchTransfomer;
import io.camunda.search.os.transformers.OpensearchTransformers;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.WriteResponseBase;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;

public class SearchIndexRequestTransformerTest {

  private final OpensearchTransformers transformers = new OpensearchTransformers();
  private SearchTransfomer<SearchIndexRequest<TestDocument>, IndexRequest<TestDocument>>
      requestTransformer;
  private SearchTransfomer<WriteResponseBase, SearchWriteResponse> responseTransformer;

  @BeforeEach
  public void before() throws IOException {
    requestTransformer = transformers.getTransformer(SearchIndexRequest.class);
    responseTransformer = transformers.getTransformer(SearchWriteResponse.class);
  }

  @Test
  public void shouldCreateIndexRequest() {
    // given
    final var doc = new TestDocument("test");
    final SearchIndexRequest<TestDocument> searchIndexRequest =
        SearchIndexRequest.of(b -> b.id("foo").index("bar").routing("foobar").document(doc));

    // when
    final var result = requestTransformer.apply(searchIndexRequest);

    // then
    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo("foo");
    assertThat(result.index()).isEqualTo("bar");
    assertThat(result.routing()).isEqualTo("foobar");
    assertThat(result.document()).isEqualTo(doc);
  }

  @Test
  public void shouldCreateIndexResponse() {
    // given
    final var doc = new TestDocument("bar");
    final IndexResponse indexResponse =
        IndexResponse.of(
            b ->
                b.id("foo")
                    .index("bar")
                    .result(Result.Created)
                    .primaryTerm(1L)
                    .seqNo(1L)
                    .version(1L)
                    .shards(s -> s.total(1).successful(1).failed(0)));

    // when
    final var result = responseTransformer.apply(indexResponse);

    // then
    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo("foo");
    assertThat(result.index()).isEqualTo("bar");
    assertThat(result.result()).isEqualTo(SearchWriteResponse.Result.CREATED);
  }

  @Test
  public void shouldFailToBuildIndexRequestWhenIndexNotPresent() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> SearchIndexRequest.of(b -> b.document(new TestDocument("test"))));
  }

  @Test
  public void shouldFailToBuildIndexRequestWhenDocumentNotPresent() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> SearchIndexRequest.of(b -> b.index("bar")));
  }

  record TestDocument(String id) {}
}
