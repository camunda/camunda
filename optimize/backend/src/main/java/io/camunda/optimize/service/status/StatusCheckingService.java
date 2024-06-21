/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.status;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.camunda.optimize.dto.optimize.query.status.EngineStatusDto;
import io.camunda.optimize.dto.optimize.query.status.StatusResponseDto;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.rest.engine.EngineContextFactory;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.importing.ImportSchedulerManagerService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.importing.EngineConstants;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public abstract class StatusCheckingService {

  protected final ConfigurationService configurationService;
  protected final EngineContextFactory engineContextFactory;
  protected final ImportSchedulerManagerService importSchedulerManagerService;
  protected final OptimizeIndexNameService optimizeIndexNameService;
  private final LoadingCache<EngineContext, Boolean> engineConnectionCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(1, TimeUnit.MINUTES)
          .build(
              new CacheLoader<EngineContext, Boolean>() {
                @Override
                public Boolean load(@NonNull final EngineContext engineContext) {
                  return isEngineVersionRequestSuccessful(engineContext);
                }
              });

  public abstract boolean isConnectedToDatabase();

  public StatusResponseDto getStatusResponse() {
    return getStatusResponse(true);
  }

  public StatusResponseDto getCachedStatusResponse() {
    return getStatusResponse(false);
  }

  public boolean isConnectedToAtLeastOnePlatformEngineOrCloud() {
    final Collection<EngineContext> configuredEngines = engineContextFactory.getConfiguredEngines();
    // if there is no engine configured == cloud
    return configuredEngines.isEmpty()
        // else there must be as least one connected Camunda Platform engine
        || configuredEngines.stream()
        .anyMatch(engineContext -> isConnectedToEngine(engineContext, true));
  }

  private StatusResponseDto getStatusResponse(final boolean skipCache) {
    final StatusResponseDto statusWithProgress = new StatusResponseDto();
    statusWithProgress.setConnectedToElasticsearch(isConnectedToDatabase());
    final Map<String, Boolean> importStatusMap = importSchedulerManagerService.getImportStatusMap();
    final Map<String, EngineStatusDto> engineConnections = new HashMap<>();
    for (final EngineContext e : engineContextFactory.getConfiguredEngines()) {
      final EngineStatusDto engineConnection = new EngineStatusDto();
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
      } catch (final ExecutionException ex) {
        log.warn(
            "There was a problem checking the connection status of engine with alias {}",
            engineContext.getEngineAlias());
      }
    }
    return isConnected;
  }

  private boolean isEngineVersionRequestSuccessful(final EngineContext engineContext) {
    boolean isConnected = false;
    try {
      final String engineEndpoint =
          configurationService.getEngineRestApiEndpointOfCustomEngine(
              engineContext.getEngineAlias())
              + EngineConstants.VERSION_ENDPOINT;
      try (final Response response =
          engineContext
              .getEngineClient()
              .target(engineEndpoint)
              .request(MediaType.APPLICATION_JSON)
              .get()) {
        isConnected = response.getStatus() == Response.Status.OK.getStatusCode();
      }
    } catch (final Exception ignored) {
      // do nothing
    }
    return isConnected;
  }
}
