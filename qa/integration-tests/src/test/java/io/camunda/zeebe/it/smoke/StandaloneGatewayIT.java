/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.smoke;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneGateway;
import io.camunda.zeebe.qa.util.cluster.ZeebePort;
import io.camunda.zeebe.qa.util.cluster.junit.ManageTestNodes;
import io.camunda.zeebe.qa.util.cluster.junit.ManageTestNodes.TestNode;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

@ManageTestNodes
@AutoCloseResources
final class StandaloneGatewayIT {

  @TestNode(awaitReady = false)
  private static final TestStandaloneGateway GATEWAY = new TestStandaloneGateway();

  @AutoCloseResource private final ZeebeClient client = GATEWAY.newClientBuilder().build();

  /** A simple smoke test which checks that the gateway can start and accept requests. */
  @SmokeTest
  void smokeTest() {
    // given - when
    final var topology = client.newTopologyRequest().send();

    // then
    given()
        .contentType(ContentType.JSON)
        .port(GATEWAY.mappedPort(ZeebePort.MONITORING))
        .when()
        .get("/actuator")
        .then()
        .statusCode(200);

    final var result = topology.join(5L, TimeUnit.SECONDS);
    assertThat(result.getBrokers()).as("there are no known brokers").isEmpty();
  }

  @Test
  void shouldCustomizeMonitoringPort() {
    // given
    final RequestSpecification gatewayServerSpec =
        new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setPort(GATEWAY.mappedPort(ZeebePort.MONITORING))
            .addFilter(new ResponseLoggingFilter())
            .addFilter(new RequestLoggingFilter())
            .build();

    // when - then
    given().spec(gatewayServerSpec).when().get("/actuator").then().statusCode(200);
  }
}
