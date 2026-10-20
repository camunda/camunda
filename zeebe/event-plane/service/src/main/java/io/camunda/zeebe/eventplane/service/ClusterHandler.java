/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.eventplane.service;

import com.sun.net.httpserver.HttpExchange;
import io.camunda.zeebe.eventplane.store.StoreConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The registry of clusters: {@code GET /v1/clusters} lists the store each cluster writes to, with
 * the secret masked, and {@code GET /v1/credentials?cluster=c} answers the store settings of one,
 * secret included. Without a {@code cluster}, the latter answers the configured credentials. The
 * answers are made once, as the registry doesn't change while the service runs.
 */
final class ClusterHandler {

  private static final String CLUSTER = "cluster";

  private final byte[] clusters;
  private final Map<String, byte[]> credentials = new HashMap<>();
  private final byte[] defaultCredentials;

  ClusterHandler(final Map<String, StoreConfig> registry, final byte[] defaultCredentials) {
    final var masked = new LinkedHashMap<String, Object>();
    registry.forEach(
        (cluster, store) -> {
          masked.put(cluster, StoreCredentials.settings(store, true));
          credentials.put(cluster, StoreCredentials.json(store).getBytes(StandardCharsets.UTF_8));
        });
    clusters = Json.write(Map.of("clusters", masked));
    this.defaultCredentials = defaultCredentials.clone();
  }

  /** {@code GET /v1/clusters}. */
  void clusters(final HttpExchange exchange) throws IOException {
    Responses.json(exchange, 200, clusters);
  }

  /**
   * {@code GET /v1/credentials}, 404 for a cluster that isn't registered.
   *
   * @throws InvalidRequestException if the request names more than one cluster
   */
  void credentials(final HttpExchange exchange) throws IOException {
    final var requested = Query.values(exchange.getRequestURI().getRawQuery(), CLUSTER);
    if (requested.isEmpty()) {
      Responses.json(exchange, 200, defaultCredentials);
    } else if (requested.size() > 1) {
      throw new InvalidRequestException(
          "at most one parameter " + CLUSTER, requested.size() + " of them");
    } else {
      final var cluster = requested.getFirst();
      final var body = credentials.get(cluster);
      if (body == null) {
        Responses.error(
            exchange,
            404,
            "Expected the cluster '%s' to be registered, but found none".formatted(cluster));
      } else {
        Responses.json(exchange, 200, body);
      }
    }
  }
}
