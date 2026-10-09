/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.protocol.model.ClusterBalanceResponse;
import io.camunda.gateway.protocol.model.ClusterCompletedRebalance;
import io.camunda.zeebe.qa.util.restapi.ClusterRebalanceRestClient;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;

final class ClusterRebalanceTestUtil {

  private ClusterRebalanceTestUtil() {}

  static ClusterBalanceResponse getRebalanceStatus(final ClusterRebalanceRestClient client) {
    final var response = client.getRebalance();
    assertThat(response.status())
        .as("rebalance status response: %s", response.body())
        .isEqualTo(HttpURLConnection.HTTP_OK);
    assertThat(response.body()).as("rebalance status response has a body").isNotNull();
    return response.body();
  }

  static ClusterCompletedRebalance awaitTerminalRebalance(
      final ClusterRebalanceRestClient client, final Duration timeout) {
    final var completed = new AtomicReference<ClusterCompletedRebalance>();
    Awaitility.await("the rebalance reaches a terminal state")
        .atMost(timeout)
        .untilAsserted(
            () -> {
              final var status = getRebalanceStatus(client);
              assertThat(status.getRunningRebalance())
                  .as("no rebalance still running: %s", status)
                  .isNull();
              assertThat(status.getLastCompletedRebalance())
                  .as("a completed rebalance is present: %s", status)
                  .isNotNull();
              completed.set(status.getLastCompletedRebalance());
            });
    return completed.get();
  }
}
