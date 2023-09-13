/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.gateway;

import static io.restassured.RestAssured.given;

import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.qa.util.cluster.TestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneGateway;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class GatewayHealthProbeIntegrationTest {

  private static final String PATH_LIVENESS_PROBE = "/actuator/health/liveness";
  private static final String PATH_READINESS_PROBE = "/actuator/health/readiness";

  @Nested
  final class WithBrokerTest {
    @Test
    void shouldReportLivenessUpIfConnectedToBroker() {
      // given
      try (final var broker = new TestStandaloneBroker().start().awaitCompleteTopology();
          final var gateway =
              new TestStandaloneGateway()
                  .withGatewayConfig(config -> configureGateway(config, broker))
                  .start()
                  .awaitCompleteTopology()) {
        final var gatewayServerSpec =
            new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setBaseUri("http://" + gateway.monitoringAddress())
                .addFilter(new ResponseLoggingFilter())
                .addFilter(new RequestLoggingFilter())
                .build();

        // when - then
        // most of the liveness probes use a delayed health indicator which is scheduled at a fixed
        // rate of 5 seconds, so it may take up to that and a bit more in the worst case once the
        // gateway finds the broker
        try {
          Awaitility.await("wait until status turns UP")
              .atMost(Duration.ofSeconds(10))
              .pollInterval(Duration.ofMillis(100))
              .untilAsserted(
                  () ->
                      given()
                          .spec(gatewayServerSpec)
                          .when()
                          .get(PATH_LIVENESS_PROBE)
                          .then()
                          .statusCode(200));
        } catch (final ConditionTimeoutException e) {
          // it can happen that a single request takes too long and causes awaitility to timeout,
          // in which case we want to try a second time to run the request without timeout
          given().spec(gatewayServerSpec).when().get(PATH_LIVENESS_PROBE).then().statusCode(200);
        }
      }
    }

    private void configureGateway(final GatewayCfg config, final TestApplication<?> broker) {
      config.getCluster().setInitialContactPoints(List.of(broker.address(TestZeebePort.CLUSTER)));
    }
  }

  @Nested
  final class WithoutBrokerTest {
    @Test
    void shouldReportLivenessDownIfNotConnectedToBroker() {
      // given
      try (final var gateway = new TestStandaloneGateway().start()) {
        final var gatewayServerSpec =
            new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setBaseUri("http://" + gateway.monitoringAddress())
                .addFilter(new ResponseLoggingFilter())
                .addFilter(new RequestLoggingFilter())
                .build();

        // when - then
        given().spec(gatewayServerSpec).when().get(PATH_LIVENESS_PROBE).then().statusCode(503);
      }
    }

    @Test
    void shouldReportReadinessUpIfApplicationIsUp() {
      // given
      try (final var gateway = new TestStandaloneGateway().start()) {
        final var gatewayServerSpec =
            new RequestSpecBuilder()
                .setContentType(ContentType.JSON)
                .setBaseUri("http://" + gateway.monitoringAddress())
                .addFilter(new ResponseLoggingFilter())
                .addFilter(new RequestLoggingFilter())
                .build();

        // when - then
        // most of the readiness probes use a delayed health indicator which is scheduled at a fixed
        // rate of 5 seconds, so it may take up to that and a bit more in the worst case once the
        // gateway finds the broker
        try {
          Awaitility.await("wait until status turns UP")
              .atMost(Duration.ofSeconds(10))
              .pollInterval(Duration.ofMillis(100))
              .untilAsserted(
                  () ->
                      given()
                          .spec(gatewayServerSpec)
                          .when()
                          .get(PATH_READINESS_PROBE)
                          .then()
                          .statusCode(200));
        } catch (final ConditionTimeoutException e) {
          // it can happen that a single request takes too long and causes awaitility to timeout,
          // in which case we want to try a second time to run the request without timeout
          given().spec(gatewayServerSpec).when().get(PATH_READINESS_PROBE).then().statusCode(200);
        }
      }
    }
  }
}
