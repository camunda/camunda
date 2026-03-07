/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.it;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.auth.domain.contract.AbstractTokenStoreContractTest;
import io.camunda.auth.domain.model.TokenMetadata;
import io.camunda.auth.domain.port.outbound.TokenStorePort;
import io.camunda.auth.persist.elasticsearch.ElasticsearchTokenStoreAdapter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Contract test for {@link ElasticsearchTokenStoreAdapter} running against a real Elasticsearch
 * instance via Testcontainers. Wraps the adapter to force index refresh after writes, ensuring
 * documents are immediately searchable.
 */
@Testcontainers
class ElasticsearchTokenStoreContractIT extends AbstractTokenStoreContractTest {

  @Container
  private static final ElasticsearchContainer ELASTICSEARCH =
      new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.17.0")
          .withEnv("xpack.security.enabled", "false")
          .withEnv("discovery.type", "single-node");

  private static ElasticsearchClient client;
  private static RestClientTransport transport;

  @BeforeAll
  static void setUp() {
    final RestClient restClient =
        RestClient.builder(HttpHost.create(ELASTICSEARCH.getHttpHostAddress())).build();
    transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
    client = new ElasticsearchClient(transport);
  }

  @AfterAll
  static void tearDown() throws Exception {
    if (transport != null) {
      transport.close();
    }
  }

  @Override
  protected TokenStorePort createStore() {
    return new RefreshingTokenStoreAdapter(new ElasticsearchTokenStoreAdapter(client), client);
  }

  /**
   * Decorator that forces an index refresh after each write so documents are immediately visible for
   * search. Required because ES is eventually consistent by default.
   */
  private record RefreshingTokenStoreAdapter(TokenStorePort delegate, ElasticsearchClient client)
      implements TokenStorePort {

    @Override
    public void store(final TokenMetadata metadata) {
      delegate.store(metadata);
      refreshIndex();
    }

    @Override
    public Optional<TokenMetadata> findByExchangeId(final String exchangeId) {
      return delegate.findByExchangeId(exchangeId);
    }

    @Override
    public List<TokenMetadata> findBySubjectPrincipalId(
        final String subjectPrincipalId, final Instant from, final Instant to) {
      return delegate.findBySubjectPrincipalId(subjectPrincipalId, from, to);
    }

    private void refreshIndex() {
      try {
        client.indices().refresh(r -> r.index("camunda-auth-token-exchange-audit"));
      } catch (final IOException e) {
        throw new UncheckedIOException("Failed to refresh ES index", e);
      }
    }
  }
}
