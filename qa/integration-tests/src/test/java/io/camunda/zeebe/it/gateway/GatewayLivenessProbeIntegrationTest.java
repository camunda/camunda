/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.gateway;

import static io.restassured.RestAssured.given;

import io.camunda.zeebe.test.util.testcontainers.ZeebeTestContainerDefaults;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.zeebe.containers.ZeebeBrokerContainer;
import io.zeebe.containers.ZeebeGatewayContainer;
import io.zeebe.containers.ZeebePort;
import java.time.Duration;
import java.util.stream.Stream;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.Test;
import org.testcontainers.lifecycle.Startable;

public class GatewayLivenessProbeIntegrationTest {

  public static final String PATH_LIVENESS_PROBE = "/live";

  @Test
  public void shouldReportLivenessUpIfConnectedToBroker() {
    // --- given ---------------------------------------

    // create a broker and a standalone gateway
    final ZeebeBrokerContainer broker =
        new ZeebeBrokerContainer(ZeebeTestContainerDefaults.defaultTestImage());
    final ZeebeGatewayContainer gateway =
        new ZeebeGatewayContainer(ZeebeTestContainerDefaults.defaultTestImage())
            .withNetwork(broker.getNetwork())
            .withEnv("ZEEBE_GATEWAY_MONITORING_ENABLED", "true")
            .withEnv("ZEEBE_GATEWAY_CLUSTER_CONTACTPOINT", broker.getInternalClusterAddress());
    gateway.addExposedPorts(ZeebePort.MONITORING.getPort());

    // start both containers
    Stream.of(gateway, broker).parallel().forEach(Startable::start);

    final Integer actuatorPort = gateway.getMappedPort(ZeebePort.MONITORING.getPort());
    final String containerIPAddress = gateway.getExternalHost();

    final RequestSpecification gatewayServerSpec =
        new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setBaseUri("http://" + containerIPAddress)
            .setPort(actuatorPort)
            .addFilter(new ResponseLoggingFilter())
            .addFilter(new RequestLoggingFilter())
            .build();

    // --- when + then ---------------------------------------
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

    // --- shutdown ------------------------------------------
    Stream.of(gateway, broker).parallel().forEach(Startable::stop);
  }

  @Test
  public void shouldReportLivenessDownIfNotConnectedToBroker() {
    // --- given ---------------------------------------
    final ZeebeGatewayContainer gateway =
        new ZeebeGatewayContainer(ZeebeTestContainerDefaults.defaultTestImage())
            .withEnv("ZEEBE_GATEWAY_MONITORING_ENABLED", "true")
            .withoutTopologyCheck();
    gateway.addExposedPorts(ZeebePort.MONITORING.getPort());
    gateway.start();

    final Integer actuatorPort = gateway.getMappedPort(ZeebePort.MONITORING.getPort());
    final String containerIPAddress = gateway.getExternalHost();

    final RequestSpecification gatewayServerSpec =
        new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setBaseUri("http://" + containerIPAddress)
            .setPort(actuatorPort)
            .addFilter(new ResponseLoggingFilter())
            .addFilter(new RequestLoggingFilter())
            .build();

    // --- when + then ---------------------------------------
    given().spec(gatewayServerSpec).when().get(PATH_LIVENESS_PROBE).then().statusCode(503);

    // --- shutdown ------------------------------------------
    gateway.stop();
  }
}
