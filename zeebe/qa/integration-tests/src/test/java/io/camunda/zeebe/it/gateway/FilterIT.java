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
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.time.Duration;
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
          .build();

  @AutoCloseResource private ZeebeClient client;

  @BeforeEach
  void initClient() {
    client = cluster.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
  }

  @Test
  void shouldFailWithFilterThrowingException() {
    // when
    assertThatThrownBy(() -> client.newTopologyRequest().useRest().send().join())
        // the exception should be a problem exception but on filter exceptions, the global advice
        // in the REST API seems to be bypassed and we receive an application/json response, not an
        // application/problem+json. Parsing the error response into a TopologyResponse fails and
        // swallows the original exception
        //        .hasCauseInstanceOf(ProblemException.class)
        //        .hasMessageContaining("I'm FILTERING!!!!");
        .hasCauseInstanceOf(ClientException.class)
        .hasMessageContaining("timestamp");
  }

  @Test
  void shouldIgnoreFailingFilterOverGrpc() {
    // when
    client.newTopologyRequest().send().join();

    // then no error is thrown
  }
}
