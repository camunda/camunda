/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.gateway;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class FilterIT {
  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withEmbeddedGateway(false)
          .withGatewaysCount(1)
          .withBrokersCount(1)
          .withGatewayConfig(
              (memberId, testGateway) -> {
                final var filterCfg = new FilterCfg();
                filterCfg.setId("test");
                filterCfg.setClassName(CustomFilter.class.getName());

                final var filterCfg2 = new FilterCfg();
                filterCfg2.setId("test2");
                filterCfg2.setClassName(CustomFilter2.class.getName());

                testGateway.gatewayConfig().setFilters(List.of(filterCfg2, filterCfg));
              })
          .build();

  @AutoCloseResource private ZeebeClient client;

  @BeforeEach
  void initClient() {
    client = cluster.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
  }

  @Test
  void shouldFailWithFilterThrowingException() {
    // when
    assertThatThrownBy(() -> client.newUserTaskUnassignCommand(987654321L).send().join())
        .hasCauseInstanceOf(ProblemException.class)
        .hasMessageContaining("I'm FILTERING!!!!");
  }

  @Test
  void shouldIgnoreFailingFilterOverGrpc() {
    // when
    client.newTopologyRequest().send().join();

    // then no error is thrown
  }
}
