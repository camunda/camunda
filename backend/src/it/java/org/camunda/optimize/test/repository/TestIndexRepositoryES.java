/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.test.repository;

import java.io.IOException;
import java.util.Set;
import lombok.AllArgsConstructor;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
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
