/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.status;

import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import jakarta.ws.rs.core.Response;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(ElasticSearchCondition.class)
public class StatusCheckingServiceES extends StatusCheckingService {

  private final OptimizeElasticsearchClient esClient;

  public StatusCheckingServiceES(
      final OptimizeElasticsearchClient esClient,
      final OptimizeIndexNameService optimizeIndexNameService) {
    super(optimizeIndexNameService);
    this.esClient = esClient;
  }

  @Override
  public boolean isConnectedToDatabase() {
    try {
      ClusterHealthRequest request =
          new ClusterHealthRequest(optimizeIndexNameService.getIndexPrefix() + "*");
      final ClusterHealthResponse healthResponse = esClient.getClusterHealth(request);

      return healthResponse.status().getStatus() == Response.Status.OK.getStatusCode()
          && healthResponse.getStatus() != ClusterHealthStatus.RED;
    } catch (Exception ignored) {
      return false;
    }
  }
}
