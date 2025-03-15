/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.smoke;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneGateway;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.restassured.http.ContentType;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;

@ZeebeIntegration
final class StandaloneGatewayIT {

  @TestZeebe(awaitReady = false, awaitCompleteTopology = false) // no brokers
  private final TestStandaloneGateway gateway =
      new TestStandaloneGateway().withUnauthenticatedAccess().withSchemaCreationDisabled();

  @AutoClose private CamundaClient client;

  @BeforeEach
  void beforeEach() {
    client = gateway.newClientBuilder().build();
  }

  /** A simple smoke test which checks that the gateway can start and accept requests. */
  @SmokeTest
  void smokeTest() {
    // given
    // when
    final var topology = client.newTopologyRequest().send();

    // then
    given()
        .contentType(ContentType.JSON)
        .port(gateway.mappedPort(TestZeebePort.MONITORING))
        .when()
        .get("/actuator")
        .then()
        .statusCode(200);

    final var result = topology.join(5L, TimeUnit.SECONDS);
    assertThat(result.getBrokers()).as("there are no known brokers").isEmpty();
  }
}
