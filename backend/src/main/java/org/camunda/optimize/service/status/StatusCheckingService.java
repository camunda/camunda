/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.status;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.status.ConnectionStatusDto;
import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressResponseDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.importing.engine.EngineImportSchedulerManagerService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.importing.EngineConstants;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class StatusCheckingService {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final EngineContextFactory engineContextFactory;
  private final EngineImportSchedulerManagerService engineImportSchedulerManagerService;

  public StatusWithProgressResponseDto getConnectionStatusWithProgress() {
    final StatusWithProgressResponseDto result = new StatusWithProgressResponseDto();
    result.setConnectionStatus(getConnectionStatus());
    result.setIsImporting(engineImportSchedulerManagerService.getImportStatusMap());
    return result;
  }

  public ConnectionStatusDto getConnectionStatus() {
    ConnectionStatusDto status = new ConnectionStatusDto();
    status.setConnectedToElasticsearch(isConnectedToElasticSearch());
    Map<String, Boolean> engineConnections = new HashMap<>();
    for (EngineContext e : engineContextFactory.getConfiguredEngines()) {
      engineConnections.put(e.getEngineAlias(), isConnectedToEngine(e));
    }
    status.setEngineConnections(engineConnections);
    return status;
  }

  public boolean isConnectedToEsAndAtLeastOneEngine() {
    if (isConnectedToElasticSearch()) {
      for (EngineContext engineContext : engineContextFactory.getConfiguredEngines()) {
        if (isConnectedToEngine(engineContext)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isConnectedToEngine(EngineContext engineContext) {
    boolean isConnected = false;
    try {
      final String engineEndpoint = configurationService
        .getEngineRestApiEndpointOfCustomEngine(engineContext.getEngineAlias()) + EngineConstants.VERSION_ENDPOINT;
      try (final Response response = engineContext.getEngineClient()
        .target(engineEndpoint).request(MediaType.APPLICATION_JSON).get()) {
        isConnected = response.getStatus() == Response.Status.OK.getStatusCode();
      }
    } catch (Exception ignored) {
      // do nothing
    }
    return isConnected;
  }

  private boolean isConnectedToElasticSearch() {
    boolean isConnected = false;
    try {
      ClusterHealthRequest request = new ClusterHealthRequest();
      ClusterHealthResponse healthResponse = esClient.getHighLevelClient().cluster()
        .health(request, RequestOptions.DEFAULT);

      isConnected = healthResponse.status().getStatus() == Response.Status.OK.getStatusCode()
        && healthResponse.getStatus() != ClusterHealthStatus.RED;
    } catch (Exception ignored) {
      // do nothing
    }
    return isConnected;
  }

}
