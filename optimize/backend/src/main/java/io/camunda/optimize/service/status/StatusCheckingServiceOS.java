/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.status;

import io.camunda.optimize.rest.engine.EngineContextFactory;
import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.importing.ImportSchedulerManagerService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch.cluster.HealthRequest;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class StatusCheckingServiceOS extends StatusCheckingService {

  private final OptimizeOpenSearchClient osClient;

  public StatusCheckingServiceOS(
      final OptimizeOpenSearchClient osClient,
      final ConfigurationService configurationService,
      final EngineContextFactory engineContextFactory,
      final ImportSchedulerManagerService importSchedulerManagerService,
      final OptimizeIndexNameService optimizeIndexNameService) {
    super(
        configurationService,
        engineContextFactory,
        importSchedulerManagerService,
        optimizeIndexNameService);
    this.osClient = osClient;
  }

  @Override
  public boolean isConnectedToDatabase() {
    boolean isConnected = false;
    try {
      final HealthResponse clusterHealthResponse =
          osClient.getOpenSearchClient().cluster().health(new HealthRequest.Builder().build());
      return clusterHealthResponse.status() != HealthStatus.Red;
    } catch (Exception ignored) {
      // do nothing
    }
    return isConnected;
  }
}
