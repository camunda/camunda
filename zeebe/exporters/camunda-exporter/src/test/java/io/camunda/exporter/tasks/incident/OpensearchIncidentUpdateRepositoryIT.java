/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.AutoClose;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.testcontainers.OpenSearchContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;

final class OpensearchIncidentUpdateRepositoryIT extends IncidentUpdateRepositoryIT {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(OpensearchIncidentUpdateRepositoryIT.class);

  @Container
  private static final OpenSearchContainer<?> CONTAINER =
      TestSearchContainers.createDefaultOpensearchContainer();

  @AutoClose
  private final org.opensearch.client.transport.rest_client.RestClientTransport transport =
      createTransport();

  private final OpenSearchAsyncClient client = new OpenSearchAsyncClient(transport);

  public OpensearchIncidentUpdateRepositoryIT() {
    super(CONTAINER.getHttpHostAddress(), false);
  }

  @Override
  protected IncidentUpdateRepository createRepository() {
    return new OpenSearchIncidentUpdateRepository(
        PARTITION_ID,
        postImporterQueueTemplate.getAlias(),
        incidentTemplate.getAlias(),
        listViewTemplate.getAlias(),
        listViewTemplate.getFullQualifiedName(),
        flowNodeInstanceTemplate.getAlias(),
        operationTemplate.getAlias(),
        client,
        Runnable::run,
        LOGGER);
  }

  @Override
  protected <T> Collection<T> search(
      final String index, final String field, final List<String> terms, final Class<T> documentType)
      throws IOException {
    final var client = new OpenSearchClient(transport);
    final var values =
        terms.stream().map(org.opensearch.client.opensearch._types.FieldValue::of).toList();
    final var query =
        org.opensearch.client.opensearch._types.query_dsl.QueryBuilders.terms()
            .field(field)
            .terms(v -> v.value(values))
            .build()
            .toQuery();
    return client.search(s -> s.index(index).query(query), documentType).hits().hits().stream()
        .map(org.opensearch.client.opensearch.core.search.Hit::source)
        .toList();
  }

  private org.opensearch.client.transport.rest_client.RestClientTransport createTransport() {
    final var restClient =
        org.opensearch.client.RestClient.builder(HttpHost.create(CONTAINER.getHttpHostAddress()))
            .build();
    return new org.opensearch.client.transport.rest_client.RestClientTransport(
        restClient, new org.opensearch.client.json.jackson.JacksonJsonpMapper());
  }
}
