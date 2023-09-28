/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.status;

import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.service.es.schema.OptimizeIndexNameService;
import org.camunda.optimize.service.importing.ImportSchedulerManagerService;
import org.camunda.optimize.service.os.OptimizeOpensearchClient;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch.cluster.HealthRequest;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class StatusCheckingServiceOS extends StatusCheckingService {

  private final OptimizeOpensearchClient osClient;

  public StatusCheckingServiceOS(final OptimizeOpensearchClient osClient,
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
    this.osClient = osClient;
  }

  @Override
  public boolean isConnectedToDatabase() {
    final RetryPolicy<Boolean> retryPolicy = new RetryPolicy<>();
    retryPolicy.withDelay(Duration.ofSeconds(1));
    return Failsafe.with(retryPolicy)
      .get(
        () -> {
          final HealthResponse clusterHealthResponse =
            osClient.getDatabaseClient().cluster().health(new HealthRequest.Builder().build());
          return clusterHealthResponse.status() != HealthStatus.Red;
        });
  }

}
