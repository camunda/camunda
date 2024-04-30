/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.opensearch;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.TransportOptions;

@ExtendWith(MockitoExtension.class)
class ExtendedOpenSearchClientTest {

  private @Mock OpenSearchTransport transport;
  private ExtendedOpenSearchClient extendedClient;

  @BeforeEach
  void setUp() {
    extendedClient = new ExtendedOpenSearchClient(transport);
    assertThat(extendedClient).isNotNull();
  }

  @Test
  void shouldUseTransportOptionsInArbitraryRequest() throws IOException {
    when(transport.jsonpMapper()).thenReturn(new JacksonJsonpMapper());
    when(transport.options()).thenReturn(mock(TransportOptions.class));
    extendedClient.arbitraryRequest("GET", "/_snapshot/backups/camunda-part-1", "{}");
    verify(transport).performRequest(anyMap(), any(Endpoint.class), any(TransportOptions.class));
  }

  @Test
  void shouldUseTransportOptionsInSearchAsMap() throws IOException {
    when(transport.options()).thenReturn(mock(TransportOptions.class));
    extendedClient.searchAsMap(new SearchRequest.Builder().build());
    verify(transport)
        .performRequest(any(SearchRequest.class), any(Endpoint.class), any(TransportOptions.class));
  }
}
