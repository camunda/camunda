/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.status;

import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.importing.ImportSchedulerManagerService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class StatusCheckingServiceES extends StatusCheckingService {

  private final OptimizeElasticsearchClient esClient;

  public StatusCheckingServiceES(final OptimizeElasticsearchClient esClient,
                                 final ConfigurationService configurationService,
                                 final EngineContextFactory engineContextFactory,
                                 final ImportSchedulerManagerService importSchedulerManagerService,
                                 final OptimizeIndexNameService optimizeIndexNameService) {
    super(
      configurationService,
      engineContextFactory,
      importSchedulerManagerService,
      optimizeIndexNameService
    );
    this.esClient = esClient;
  }

  @Override
  public boolean isConnectedToDatabase() {
    boolean isConnected = false;
    try {
      ClusterHealthRequest request = new ClusterHealthRequest(optimizeIndexNameService.getIndexPrefix() + "*");
      final ClusterHealthResponse healthResponse = esClient.getClusterHealth(request);

      isConnected = healthResponse.status().getStatus() == Response.Status.OK.getStatusCode()
        && healthResponse.getStatus() != ClusterHealthStatus.RED;
    } catch (Exception ignored) {
      // do nothing
    }
    return isConnected;
  }
}
