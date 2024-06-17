/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.repository;

import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.io.IOException;
import java.util.Set;
import lombok.AllArgsConstructor;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
@Conditional(ElasticSearchCondition.class)
public class TestIndexRepositoryES implements TestIndexRepository {
  private final OptimizeElasticsearchClient esClient;

  @Override
  public Set<String> getAllIndexNames() {
    GetIndexRequest request = new GetIndexRequest("*");
    try {
      return Set.of(
          esClient
              .getHighLevelClient()
              .indices()
              .get(request, esClient.requestOptions())
              .getIndices());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
