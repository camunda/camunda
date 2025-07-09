/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.os.clients;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.os.transformers.OpensearchTransformers;
import java.io.IOException;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchRequest;

public class OpensearchSearchClientTest {

  private OpenSearchClient client;
  private OpensearchSearchClient searchClient;
  private SearchQueryRequest searchRequest;

  @BeforeEach
  void setUp() {
    client = mock(OpenSearchClient.class);
    searchClient = new OpensearchSearchClient(client, new OpensearchTransformers());
    searchRequest = mock(SearchQueryRequest.class);
    when(searchRequest.size()).thenReturn(null);
  }

  @Test
  void findAllShouldHandleIOException() throws IOException {
    // given
    when(client.search(any(SearchRequest.class), any())).thenThrow(IOException.class);

    // when & Assert
    assertThrows(
        CamundaSearchException.class, () -> searchClient.scroll(searchRequest, Object.class));
    verify(client, never()).scroll(any(Function.class), any());
    verify(client, never()).clearScroll(any(Function.class));
  }
}
