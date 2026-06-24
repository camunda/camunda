/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.es.clients;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.es.transformers.ElasticsearchTransformers;
import io.camunda.search.exception.CamundaSearchException;
import java.io.IOException;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ElasticsearchSearchClientTest {

  private ElasticsearchClient client;
  private ElasticsearchSearchClient searchClient;
  private SearchQueryRequest searchRequest;

  @BeforeEach
  void setUp() {
    client = mock(ElasticsearchClient.class);
    searchClient = new ElasticsearchSearchClient(client, new ElasticsearchTransformers());
    searchRequest = mock(SearchQueryRequest.class);
    when(searchRequest.size()).thenReturn(null);
  }

  @Test
  void findAllShouldHandleIOException() throws IOException {
    // given
    when(client.search(any(SearchRequest.class), any())).thenThrow(IOException.class);

    // when & Assert
    assertThatExceptionOfType(CamundaSearchException.class)
        .isThrownBy(() -> searchClient.scroll(searchRequest, Object.class));
    verify(client, never()).scroll(any(Function.class), any());
    verify(client, never()).clearScroll(any(Function.class));
  }
}
