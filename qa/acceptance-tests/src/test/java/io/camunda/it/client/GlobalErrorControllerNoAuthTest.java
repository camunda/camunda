/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.zeebe.test.util.asserts.TopologyAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.Topology;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class GlobalErrorControllerNoAuthTest {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker().withAuthorizationsDisabled().withAuthenticatedAccess();

  @Test
  void shouldPass() throws URISyntaxException {
    try (final CamundaClient client = createClient("")) {

      final Topology topology = client.newTopologyRequest().send().join();

      assertThat(topology).isNotNull();
    }
  }

  @Test
  void shouldThrowClientException() throws URISyntaxException {
    try (final CamundaClient client = createClient("/wrong")) {

      assertThatThrownBy(() -> client.newTopologyRequest().send().join())
          .isInstanceOf(ClientException.class)
          .hasMessage(
              "Failed with code 404: 'Not Found'. Details: 'class ProblemDetail {\n"
                  + "    type: about:blank\n"
                  + "    title: Not Found\n"
                  + "    status: 404\n"
                  + "    detail: No endpoint GET /wrong/v2/topology.\n"
                  + "    instance: /wrong/v2/topology\n"
                  + "}'");
    }
  }

  private static CamundaClient createClient(final String path) throws URISyntaxException {
    return BROKER
        .newClientBuilder()
        .restAddress(new URI("http://localhost:" + BROKER.mappedPort(TestZeebePort.REST) + path))
        .defaultRequestTimeout(Duration.ofMinutes(10))
        .preferRestOverGrpc(true)
        .build();
  }
}
