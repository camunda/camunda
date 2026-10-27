/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.status;

import io.camunda.optimize.service.db.os.OptimizeOpenSearchClient;
import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch.cluster.HealthRequest;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(OpenSearchCondition.class)
public class StatusCheckingServiceOS extends StatusCheckingService {

  private final OptimizeOpenSearchClient osClient;

  public StatusCheckingServiceOS(
      final OptimizeOpenSearchClient osClient,
      final OptimizeIndexNameService optimizeIndexNameService) {
    super(optimizeIndexNameService);
    this.osClient = osClient;
  }

  @Override
  public boolean isConnectedToDatabase() {
    try {
      final HealthResponse clusterHealthResponse =
          osClient.getOpenSearchClient().cluster().health(new HealthRequest.Builder().build());
      return clusterHealthResponse.status() != HealthStatus.Red;
    } catch (final Exception ignored) {
      return false;
    }
  }
}
