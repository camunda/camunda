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
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.gateway.StandaloneGateway;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.grpc.Status.Code;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    classes = StandaloneGateway.class,
    properties = "zeebe.gateway.monitoring.enabled=true")
@ContextConfiguration(
    initializers = {RandomPortInitializer.class, CollectorRegistryInitializer.class})
@ActiveProfiles("test")
final class StandaloneGatewayIT {

  @SuppressWarnings("unused")
  @Autowired
  private GatewayCfg config;

  @SuppressWarnings("unused")
  @LocalServerPort
  private int managementPort;

  /** A simple smoke test which checks that the gateway can start and accept requests. */
  @SmokeTest
  void smokeTest() {
    // given
    try (final var client =
        ZeebeClient.newClientBuilder()
            .usePlaintext()
            .gatewayAddress("localhost:" + config.getNetwork().getPort())
            .build()) {
      // when
      final var topology = client.newTopologyRequest().send();

      // then
      given()
          .contentType(ContentType.JSON)
          .port(managementPort)
          .when()
          .get("/actuator")
          .then()
          .statusCode(200);

      // depending on how fast the gateway is, we may get an unavailable error or an empty topology
      try {
        final var result = topology.join(5L, TimeUnit.SECONDS);
        assertThat(result.getBrokers()).as("there are no known brokers").isEmpty();
      } catch (final ClientStatusException e) {
        assertThat(e).hasRootCauseMessage("UNAVAILABLE: No brokers available");
        assertThat(e.getStatus().getCode()).isEqualTo(Code.UNAVAILABLE);
      } catch (final Exception e) {
        throw e;
      }
    }
  }

  @Test
  void shouldCustomizeMonitoringPort() {
    // given
    final RequestSpecification gatewayServerSpec =
        new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setPort(managementPort)
            .addFilter(new ResponseLoggingFilter())
            .addFilter(new RequestLoggingFilter())
            .build();

    // when - then
    given().spec(gatewayServerSpec).when().get("/actuator").then().statusCode(200);
  }
}
