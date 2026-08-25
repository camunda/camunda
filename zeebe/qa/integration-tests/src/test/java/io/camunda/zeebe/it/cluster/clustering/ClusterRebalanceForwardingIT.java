/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.clustering;

import static org.assertj.core.api.Assertions.assertThat;

import feign.Response;
import feign.Util;
import io.atomix.cluster.MemberId;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.restapi.ClusterRebalanceRestClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

final class ClusterRebalanceForwardingIT {

  @AutoClose private TestCluster cluster;

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
    final var nonCoordinatorClient = ClusterRebalanceRestClient.of(nonCoordinator.restAddress());
    final var coordinatorClient = ClusterRebalanceRestClient.of(coordinator.restAddress());

    // then
    try (final var fromNonCoordinator = nonCoordinatorClient.getRebalance();
        final var fromCoordinator = coordinatorClient.getRebalance()) {
      assertThat(fromNonCoordinator.status()).isEqualTo(200);
      final var nonCoordinatorBody = readBody(fromNonCoordinator);
      final var coordinatorBody = readBody(fromCoordinator);
      assertThat(nonCoordinatorBody).isEqualTo(coordinatorBody);
    }
  }

  private static String readBody(final Response response) throws IOException {
    if (response.body() == null) {
      return "";
    }
    return Util.toString(response.body().asReader(StandardCharsets.UTF_8));
  }
}
