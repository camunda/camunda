/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.externalcode.client.sync;

import io.camunda.optimize.service.db.schema.OptimizeIndexNameService;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch.cluster.HealthResponse;
import org.opensearch.client.opensearch.nodes.Stats;
import org.slf4j.Logger;

public class OpenSearchClusterOperations extends OpenSearchSyncOperation {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(OpenSearchClusterOperations.class);

  public OpenSearchClusterOperations(
      final OpenSearchClient openSearchClient, final OptimizeIndexNameService indexNameService) {
    super(openSearchClient, indexNameService);
  }

  public boolean isHealthy() {
    try {
      final HealthResponse response =
          openSearchClient.cluster().health(h -> h.timeout(t -> t.time("500")));
      final HealthStatus status = response.status();
      return !response.timedOut() && !status.equals(HealthStatus.Red);
    } catch (final IOException e) {
      log.error(
          String.format(
              "Couldn't connect to OpenSearch due to %s. Return unhealthy state.", e.getMessage()),
          e);
      return false;
    }
  }

  public Map<String, Stats> nodesStats() throws IOException {
    return openSearchClient.nodes().stats().nodes();
  }

  public int totalOpenContexts() throws IOException {
    return openContexts().values().stream().mapToInt(Long::intValue).sum();
  }

  public Map<String, Long> openContexts() throws IOException {
    return nodesStats().entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, entry -> entry.getValue().indices().search().openContexts()));
  }
}
