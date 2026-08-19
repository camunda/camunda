/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

final class ClusterRebalanceForwardingIT {

  private static final String REBALANCE_PATH = "cluster/v2/rebalance";

  @AutoClose private TestCluster cluster;
  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void shouldForwardARequestFromANonCoordinatorToTheCoordinator() throws Exception {
    // given
    cluster =
        TestCluster.builder()
            .withBrokersCount(2)
            .withEmbeddedGateway(true)
            .withPartitionsCount(1)
            .withReplicationFactor(2)
            .build();
    cluster.start().awaitCompleteTopology();

    final var coordinator = cluster.brokers().get(MemberId.from("0"));
    final var nonCoordinator = cluster.brokers().get(MemberId.from("1"));

    // when
    final var fromNonCoordinator = getRebalance(nonCoordinator.restAddress());

    // then
    assertThat(fromNonCoordinator.statusCode()).isEqualTo(200);
    final var fromCoordinator = getRebalance(coordinator.restAddress());
    assertThat(fromNonCoordinator.body()).isEqualTo(fromCoordinator.body());
  }

  private HttpResponse<String> getRebalance(final URI restAddress) throws Exception {
    final var request = HttpRequest.newBuilder(restAddress.resolve(REBALANCE_PATH)).GET().build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
