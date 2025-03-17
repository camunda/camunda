/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.incident;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import io.camunda.exporter.utils.SearchDBExtension;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;

@DisabledIfSystemProperty(
    named = SearchDBExtension.TEST_INTEGRATION_OPENSEARCH_AWS_URL,
    matches = "^(?=\\s*\\S).*$",
    disabledReason = "Excluding from AWS OS IT CI")
final class ElasticsearchIncidentUpdateRepositoryIT extends IncidentUpdateRepositoryIT {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ElasticsearchIncidentUpdateRepositoryIT.class);

  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  @AutoClose private final RestClientTransport transport = createTransport();
  private final ElasticsearchAsyncClient client = new ElasticsearchAsyncClient(transport);

  public ElasticsearchIncidentUpdateRepositoryIT() {
    super("http://" + CONTAINER.getHttpHostAddress(), true);
  }

  @Override
  protected IncidentUpdateRepository createRepository() {
    return new ElasticsearchIncidentUpdateRepository(
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
    final var client = new ElasticsearchClient(transport);
    final var values = terms.stream().map(FieldValue::of).toList();
    final var query = QueryBuilders.terms(t -> t.field(field).terms(v -> v.value(values)));
    return client.search(s -> s.index(index).query(query), documentType).hits().hits().stream()
        .map(Hit::source)
        .toList();
  }

  private RestClientTransport createTransport() {
    final var restClient =
        RestClient.builder(HttpHost.create(CONTAINER.getHttpHostAddress())).build();
    return new RestClientTransport(restClient, new JacksonJsonpMapper());
  }
}
