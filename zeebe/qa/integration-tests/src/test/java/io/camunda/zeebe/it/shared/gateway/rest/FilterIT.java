/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.shared.gateway.rest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import io.camunda.zeebe.it.shared.gateway.rest.util.DisableTopologyFilter;
import io.camunda.zeebe.it.shared.gateway.rest.util.DisableUserTasksTestFilter;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class FilterIT {

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withEmbeddedGateway(false)
          .withGatewaysCount(1)
          .withBrokersCount(1)
          .withGatewayConfig(
              (memberId, testGateway) -> {
                final var firstFilterCfg = new FilterCfg();
                firstFilterCfg.setId("firstFilter");
                firstFilterCfg.setClassName(DisableTopologyFilter.class.getName());

                final var secondFilterCfg = new FilterCfg();
                secondFilterCfg.setId("secondFilter");
                secondFilterCfg.setClassName(DisableUserTasksTestFilter.class.getName());

                testGateway.gatewayConfig().setFilters(List.of(firstFilterCfg, secondFilterCfg));
              })
          .build();

  @AutoClose private CamundaClient client;

  @BeforeEach
  void initClient() {
    client =
        cluster
            .newClientBuilder()
            .preferRestOverGrpc(false)
            .defaultRequestTimeout(Duration.ofSeconds(15))
            .build();
  }

  @Test
  void shouldFailWithFirstFilterThrowingException() {
    // when / then
    assertThatThrownBy(() -> client.newTopologyRequest().useRest().send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("No topology interactions while testing");
  }

  @Test
  void shouldFailWithSecondFilterThrowingException() {
    // when / then
    assertThatThrownBy(() -> client.newCompleteUserTaskCommand(12345).send().join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("No user task interactions while testing");
  }

  @Test
  void shouldIgnoreFailingFilterOverGrpc() {
    // when
    client.newTopologyRequest().send().join();

    // then no error is thrown
  }
}
