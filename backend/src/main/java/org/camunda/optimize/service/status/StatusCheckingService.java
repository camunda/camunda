/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.status;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.engine.ProcessEngineDto;
import org.camunda.optimize.dto.optimize.query.status.ConnectionStatusDto;
import org.camunda.optimize.dto.optimize.query.status.StatusWithProgressDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.engine.importing.EngineImportSchedulerFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class StatusCheckingService {

  private final RestHighLevelClient esClient;
  private final ConfigurationService configurationService;
  private final EngineContextFactory engineContextFactory;
  private final EngineImportSchedulerFactory engineImportSchedulerFactory;

  public StatusWithProgressDto getConnectionStatusWithProgress() {
    StatusWithProgressDto result = new StatusWithProgressDto();
    result.setConnectionStatus(getConnectionStatus());
    Map<String, Boolean> importStatusMap = new HashMap<>();
    engineImportSchedulerFactory
      .getImportSchedulers()
      .forEach(s -> importStatusMap.put(s.getEngineAlias(), s.isImporting()));
    result.setIsImporting(importStatusMap);
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

  private boolean isConnectedToEngine(EngineContext engineContext) {
    boolean isConnected = false;
    try {
      String endPoint = configurationService.getEngineRestApiEndpoint(engineContext.getEngineAlias());
      String engineEndpoint = endPoint + "/engine";
      Response response = engineContext.getEngineClient()
        .target(engineEndpoint)
        .request(MediaType.APPLICATION_JSON)
        .get();
      boolean hasCorrectResponseCode = response.getStatus() == 200;
      boolean engineIsRunning = engineWithEngineNameIsRunning(
        response,
        configurationService.getEngineName(engineContext.getEngineAlias())
      );
      isConnected = hasCorrectResponseCode && engineIsRunning;
    } catch (Exception ignored) {
      // do nothing
    }
    return isConnected;
  }

  private boolean engineWithEngineNameIsRunning(Response response, String engineName) {
    List<ProcessEngineDto> engineNames = response.readEntity(new GenericType<List<ProcessEngineDto>>() {
    });
    return engineNames.stream().anyMatch(e -> e.getName().equals(engineName));
  }

  private boolean isConnectedToElasticSearch() {
    boolean isConnected = false;
    try {
      ClusterHealthRequest request = new ClusterHealthRequest();
      ClusterHealthResponse healthResponse = esClient.cluster().health(request, RequestOptions.DEFAULT);

      isConnected =
        healthResponse.status().getStatus() == 200 && healthResponse.getStatus() != ClusterHealthStatus.RED;
    } catch (Exception ignored) {
      // do nothing
    }
    return isConnected;
  }

}
