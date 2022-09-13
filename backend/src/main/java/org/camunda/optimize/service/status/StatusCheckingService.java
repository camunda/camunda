/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.status;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.status.EngineStatusDto;
import org.camunda.optimize.dto.optimize.query.status.StatusResponseDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.importing.ImportSchedulerManagerService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.importing.EngineConstants;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Component
@Slf4j
public class StatusCheckingService {

  private final OptimizeElasticsearchClient esClient;
  private final ConfigurationService configurationService;
  private final EngineContextFactory engineContextFactory;
  private final ImportSchedulerManagerService importSchedulerManagerService;
  private final OptimizeIndexNameService optimizeIndexNameService;

  private final LoadingCache<EngineContext, Boolean> engineConnectionCache = CacheBuilder.newBuilder()
    .expireAfterWrite(1, TimeUnit.MINUTES)
    .build(new CacheLoader<EngineContext, Boolean>() {
      @Override
      public Boolean load(@NonNull EngineContext engineContext) {
        return isEngineVersionRequestSuccessful(engineContext);
      }
    });

  public StatusResponseDto getStatusResponse() {
    return getStatusResponse(true);
  }

  public StatusResponseDto getCachedStatusResponse() {
    return getStatusResponse(false);
  }

  public boolean isConnectedToElasticSearch() {
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

  public boolean isConnectedToAtLeastOnePlatformEngineOrCloud() {
    final Collection<EngineContext> configuredEngines = engineContextFactory.getConfiguredEngines();
    // if there is no engine configured == cloud
    return configuredEngines.isEmpty()
      // else there must be as least one connected Camunda Platform engine
      || configuredEngines
      .stream()
      .anyMatch(engineContext -> isConnectedToEngine(engineContext, true));
  }

  private StatusResponseDto getStatusResponse(final boolean skipCache) {
    StatusResponseDto statusWithProgress = new StatusResponseDto();
    statusWithProgress.setConnectedToElasticsearch(isConnectedToElasticSearch());
    Map<String, Boolean> importStatusMap = importSchedulerManagerService.getImportStatusMap();
    Map<String, EngineStatusDto> engineConnections = new HashMap<>();
    for (EngineContext e : engineContextFactory.getConfiguredEngines()) {
      EngineStatusDto engineConnection = new EngineStatusDto();
      engineConnection.setIsConnected(isConnectedToEngine(e, skipCache));
      engineConnection.setIsImporting(importStatusMap.getOrDefault(e.getEngineAlias(), false));
      engineConnections.put(e.getEngineAlias(), engineConnection);
    }
    statusWithProgress.setEngineStatus(engineConnections);
    return statusWithProgress;
  }

  private boolean isConnectedToEngine(final EngineContext engineContext, final boolean skipCache) {
    boolean isConnected = false;
    if (skipCache) {
      isConnected = isEngineVersionRequestSuccessful(engineContext);
      // If we skip the cache, we refresh it with the result of the engine request
      engineConnectionCache.put(engineContext, isConnected);
    } else {
      try {
        isConnected = engineConnectionCache.get(engineContext);
      } catch (ExecutionException ex) {
        log.warn(
          "There was a problem checking the connection status of engine with alias {}",
          engineContext.getEngineAlias()
        );
      }
    }
    return isConnected;
  }

  private boolean isEngineVersionRequestSuccessful(EngineContext engineContext) {
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

}
