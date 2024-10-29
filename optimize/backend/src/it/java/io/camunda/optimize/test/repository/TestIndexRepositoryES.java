/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.repository;

import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Set;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class TestIndexRepositoryES implements TestIndexRepository {

  private final OptimizeElasticsearchClient esClient;

  public TestIndexRepositoryES(final OptimizeElasticsearchClient esClient) {
    this.esClient = esClient;
  }

  @Override
  public Set<String> getAllIndexNames() {
    final GetIndexRequest request = GetIndexRequest.of(i -> i.index("*"));
    try {
      return esClient.elasticsearchClient().indices().get(request).result().keySet();
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }
}
