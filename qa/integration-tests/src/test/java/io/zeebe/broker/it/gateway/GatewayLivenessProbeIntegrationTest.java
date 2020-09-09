/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.gateway;

import static io.restassured.RestAssured.given;

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
import org.junit.Test;
import org.testcontainers.lifecycle.Startable;

public class GatewayLivenessProbeIntegrationTest {

  public static final String PATH_LIVENESS_PROBE = "/live";

  @Test
  public void shouldReportLivenessUpIfConnectedToBroker() throws InterruptedException {
    // --- given ---------------------------------------

    // create a broker and a standalone gateway
    final ZeebeBrokerContainer broker = new ZeebeBrokerContainer("camunda/zeebe:current-test");
    final ZeebeGatewayContainer gateway =
        new ZeebeGatewayContainer("camunda/zeebe:current-test")
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
    // most of the liveness health indicators are delayed health indicators which are scheduled at
    // a fixed rate of 5 seconds, so it may take up to 5 seconds for all the indicators to be
    // checked
    Awaitility.await("wait for indicator to turn UP")
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .untilAsserted(
            () ->
                given()
                    .spec(gatewayServerSpec)
                    .when()
                    .get(PATH_LIVENESS_PROBE)
                    .then()
                    .statusCode(200));

    // --- shutdown ------------------------------------------
    Stream.of(gateway, broker).parallel().forEach(Startable::stop);
  }

  @Test
  public void shouldReportLivenessDownIfNotConnectedToBroker() {
    // --- given ---------------------------------------
    final ZeebeGatewayContainer gateway =
        new ZeebeGatewayContainer("camunda/zeebe:current-test")
            .withEnv("ZEEBE_GATEWAY_MONITORING_ENABLED", "true")
            .withTopologyCheck(
                ZeebeGatewayContainer.newDefaultTopologyCheck()
                    .forPartitionsCount(0)
                    .forBrokersCount(0));
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
