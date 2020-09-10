/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.gateway;

import static io.restassured.RestAssured.given;
import static io.zeebe.test.util.asserts.TopologyAssert.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.zeebe.client.ZeebeClient;
import io.zeebe.containers.ZeebeBrokerContainer;
import io.zeebe.containers.ZeebeGatewayContainer;
import io.zeebe.containers.ZeebePort;
import java.util.stream.Stream;
import org.junit.Test;
import org.testcontainers.lifecycle.Startable;

public class GatewayLivenessProbeIntegrationTest {

  public static final String PATH_LIVENESS_PROBE = "/live";

  @Test
  public void shouldReportLivenessUpIfConnectedToBroker() {
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

    final ZeebeClient zeebeClient = createZeebeClient(gateway);

    // wait a little while to give the broker and gateway a chance to find each other
    await().atMost(60, SECONDS).untilAsserted(() -> assertTopologyIsComplete(zeebeClient));

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
    given().spec(gatewayServerSpec).when().get(PATH_LIVENESS_PROBE).then().statusCode(200);

    // --- shutdown ------------------------------------------
    Stream.of(gateway, broker).parallel().forEach(Startable::stop);
  }

  private void assertTopologyIsComplete(final ZeebeClient zeebeClient) {
    final var topology = zeebeClient.newTopologyRequest().send().join();
    assertThat(topology).isComplete(1, 1);
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

  private static ZeebeClient createZeebeClient(final ZeebeGatewayContainer gateway) {
    return ZeebeClient.newClientBuilder()
        .brokerContactPoint(gateway.getExternalGatewayAddress())
        .usePlaintext()
        .build();
  }
}
