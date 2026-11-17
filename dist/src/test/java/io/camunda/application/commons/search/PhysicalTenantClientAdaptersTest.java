/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.ElasticsearchTransport;
import io.camunda.search.connect.tenant.SearchClients;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;

final class PhysicalTenantClientAdaptersTest {

  private final PhysicalTenantSearchClientReadersConfiguration configuration =
      new PhysicalTenantSearchClientReadersConfiguration();

  @Test
  void shouldCreateOneAdapterPerPhysicalTenantForBothEngines() {
    // given
    final var searchClients =
        new SearchClients(
            Map.of("es-tenant", elasticsearchClient(mock(ElasticsearchTransport.class))),
            Map.of("os-tenant", opensearchClient(mock(OpenSearchTransport.class))),
            Map.of());

    // when
    final var adapters = configuration.physicalTenantClientAdapters(searchClients);

    // then
    assertThat(adapters).containsOnlyKeys("es-tenant", "os-tenant");
  }

  @Test
  void shouldNotCloseTheSharedClientsWhenAnAdapterIsClosed() throws Exception {
    // given
    final var esTransport = mock(ElasticsearchTransport.class);
    final var osTransport = mock(OpenSearchTransport.class);
    final var searchClients =
        new SearchClients(
            Map.of("es-tenant", elasticsearchClient(esTransport)),
            Map.of("os-tenant", opensearchClient(osTransport)),
            Map.of());
    final var adapters = configuration.physicalTenantClientAdapters(searchClients);

    // when
    for (final var adapter : adapters.values()) {
      adapter.close();
    }

    // then
    verify(esTransport, never()).close();
    verify(osTransport, never()).close();
  }

  @Test
  void shouldCreateNoAdaptersWithoutClients() {
    // when
    final var adapters =
        configuration.physicalTenantClientAdapters(new SearchClients(Map.of(), Map.of(), Map.of()));

    // then
    assertThat(adapters).isEmpty();
  }

  private static ElasticsearchClient elasticsearchClient(final ElasticsearchTransport transport) {
    when(transport.jsonpMapper())
        .thenReturn(new co.elastic.clients.json.jackson.JacksonJsonpMapper());
    return new ElasticsearchClient(transport);
  }

  private static OpenSearchClient opensearchClient(final OpenSearchTransport transport) {
    when(transport.jsonpMapper())
        .thenReturn(new org.opensearch.client.json.jackson.JacksonJsonpMapper());
    return new OpenSearchClient(transport);
  }
}
