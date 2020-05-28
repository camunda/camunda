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
import io.zeebe.containers.ZeebePort;
import io.zeebe.containers.ZeebeStandaloneGatewayContainer;
import java.util.stream.Stream;
import org.junit.Test;
import org.testcontainers.lifecycle.Startable;

public class GatewayLivenessProbeIntegrationTest {

  public static final int ACTUATOR_PORT_IN_CONTAINER = 8080;
  public static final String PATH_LIVENESS_PROBE = "/actuator/health/liveness";

  @Test
  public void shouldReportLivenessUpIfConnectedToBroker() throws InterruptedException {
    // --- given ---------------------------------------

    // create a broker and a standalone gateway
    final ZeebeBrokerContainer broker =
        new ZeebeBrokerContainer("current-test").withClusterName("zeebe-cluster");
    final ZeebeStandaloneGatewayContainer gateway =
        new ZeebeStandaloneGatewayContainer("current-test")
            .withNetwork(broker.getNetwork())
            .withClusterName("zeebe-cluster")
            .withExposedPorts(8080); // make sure they are on the same network
    // configure broker so it doesn't start an embedded gateway
    broker.withEmbeddedGateway(false).withHost("zeebe-0");
    gateway
        .withContactPoint(broker.getInternalAddress(ZeebePort.INTERNAL_API))
        .withEnv("ZEEBE_GATEWAY_CLUSTER_CONTACTPOINT", broker.getContactPoint());

    // start both containers
    Stream.of(gateway, broker).parallel().forEach(Startable::start);

    final Integer actuatorPort = gateway.getMappedPort(ACTUATOR_PORT_IN_CONTAINER);
    final String containerIPAddress = gateway.getContainerIpAddress();

    final RequestSpecification gatewayServerSpec =
        new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setBaseUri("http://" + containerIPAddress)
            .setPort(actuatorPort)
            .addFilter(new ResponseLoggingFilter())
            .addFilter(new RequestLoggingFilter())
            .build();

    // --- when + then ---------------------------------------
    given().spec(gatewayServerSpec).when().get(PATH_LIVENESS_PROBE).then().statusCode(200);

    // --- shutdown ------------------------------------------
    Stream.of(gateway, broker).parallel().forEach(Startable::stop);
  }

  @Test
  public void shouldReportLivenessDownIfNotConnectedToBroker() throws InterruptedException {
    // --- given ---------------------------------------
    final ZeebeStandaloneGatewayContainer gateway =
        new ZeebeStandaloneGatewayContainer("current-test").withExposedPorts(8080);
    gateway.start();

    final Integer actuatorPort = gateway.getMappedPort(ACTUATOR_PORT_IN_CONTAINER);
    final String containerIPAddress = gateway.getContainerIpAddress();

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
