/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.shared.gateway;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneGateway;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class GatewayHealthProbeIntegrationTest {

  private static final String PATH_LIVENESS_PROBE = "/health/liveness";
  private static final String PATH_READINESS_PROBE = "/health/readiness";
  private static final String PATH_TO_HEALTH_PROBE = "/health";

  @Nested
  final class WithBrokerTest {
    @TestZeebe
    private final TestCluster cluster =
        TestCluster.builder().withEmbeddedGateway(false).withGatewaysCount(1).build();

    @Test
    void shouldReportLivenessUpIfConnectedToBroker() {
      // given
      final var gateway = cluster.availableGateway();
      final var gatewayServerSpec =
          new RequestSpecBuilder()
              .setContentType(ContentType.JSON)
              .setBaseUri(gateway.actuatorUri())
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

    @Test
    void shouldReportHealthUpIfConnectedToBroker() {
      // given
      final var gateway = cluster.availableGateway();
      final var gatewayServerSpec =
          new RequestSpecBuilder()
              .setContentType(ContentType.JSON)
              .setBaseUri(gateway.actuatorUri())
              .addFilter(new ResponseLoggingFilter())
              .addFilter(new RequestLoggingFilter())
              .build();

      // when - then
      // most of the health probes use a delayed health indicator which is scheduled at a fixed
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
                        .get(PATH_TO_HEALTH_PROBE)
                        .then()
                        .body("components.clusterHealth.status", equalTo("UP")));
      } catch (final ConditionTimeoutException e) {
        // it can happen that a single request takes too long and causes awaitility to timeout,
        // in which case we want to try a second time to run the request without timeout
        given()
            .spec(gatewayServerSpec)
            .when()
            .get(PATH_TO_HEALTH_PROBE)
            .then()
            .body("components.clusterHealth.status", equalTo("UP"));
      }
    }
  }

  @Nested
  final class WithoutBrokerTest {
    @TestZeebe(awaitReady = false, awaitCompleteTopology = false) // since there's no broker
    private final TestStandaloneGateway gateway = new TestStandaloneGateway();

    @Test
    void shouldReportLivenessDownIfNotConnectedToBroker() {
      // given
      final var gatewayServerSpec =
          new RequestSpecBuilder()
              .setContentType(ContentType.JSON)
              .setBaseUri(gateway.actuatorUri())
              .addFilter(new ResponseLoggingFilter())
              .addFilter(new RequestLoggingFilter())
              .build();

      // when - then
      given().spec(gatewayServerSpec).when().get(PATH_LIVENESS_PROBE).then().statusCode(503);
    }

    @Test
    void shouldReportReadinessUpIfApplicationIsUp() {
      // given
      final var gatewayServerSpec =
          new RequestSpecBuilder()
              .setContentType(ContentType.JSON)
              .setBaseUri(gateway.actuatorUri())
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

    @Test
    void shouldReportDownIfNotConnectedToBroker() {
      // given
      final var gatewayServerSpec =
          new RequestSpecBuilder()
              .setContentType(ContentType.JSON)
              .setBaseUri(gateway.actuatorUri())
              .addFilter(new ResponseLoggingFilter())
              .addFilter(new RequestLoggingFilter())
              .build();

      // when - then
      // most of the health probes use a delayed health indicator which is scheduled at a fixed
      // rate of 5 seconds, so it may take up to that and a bit more in the worst case once the
      // gateway finds the broker
      try {
        Awaitility.await("wait until status turns DOWN")
            .atMost(Duration.ofSeconds(10))
            .pollInterval(Duration.ofMillis(100))
            .untilAsserted(
                () ->
                    given()
                        .spec(gatewayServerSpec)
                        .when()
                        .get(PATH_TO_HEALTH_PROBE)
                        .then()
                        .body("components.clusterHealth.status", equalTo("DOWN")));
      } catch (final ConditionTimeoutException e) {
        // it can happen that a single request takes too long and causes awaitility to timeout,
        // in which case we want to try a second time to run the request without timeout
        given()
            .spec(gatewayServerSpec)
            .when()
            .get(PATH_TO_HEALTH_PROBE)
            .then()
            .body("components.clusterHealth.status", equalTo("DOWN"));
      }
    }
  }

  @Nested
  final class WithAuthenticationIdentityTest {
    @TestZeebe(awaitReady = false, awaitCompleteTopology = false) // since there's no broker
    private final TestStandaloneGateway gateway =
        new TestStandaloneGateway().withAdditionalProfile("identity-auth");

    @Test
    void shouldReportReadinessUpWithoutAuthentication() {
      // given
      final var gatewayServerSpec =
          new RequestSpecBuilder()
              .setContentType(ContentType.JSON)
              .setBaseUri(gateway.actuatorUri())
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

    @Test
    void shouldReturnNotFoundWhenCallingNoneExistingEndpoint() {
      // given
      final var gatewayServerSpec =
          new RequestSpecBuilder()
              .setContentType(ContentType.JSON)
              .setBaseUri(gateway.monitoringUri())
              .addFilter(new ResponseLoggingFilter())
              .addFilter(new RequestLoggingFilter())
              .build();

      // when - then
      given().spec(gatewayServerSpec).when().get(PATH_TO_HEALTH_PROBE).then().statusCode(404);
    }
  }
}
