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
import io.zeebe.containers.ZeebePort;
import io.zeebe.containers.ZeebeStandaloneGatewayContainer;
import java.util.stream.Stream;
import org.junit.Test;
import org.testcontainers.lifecycle.Startable;

public class GatewayLivenessProbeIntegrationTest {

  public static final int MONITORING_PORT_IN_CONTAINER = 9600;
  public static final String PATH_LIVENESS_PROBE = "/live";

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
            .withExposedPorts(
                MONITORING_PORT_IN_CONTAINER); // make sure they are on the same network
    // configure broker so it doesn't start an embedded gateway
    broker.withEmbeddedGateway(false).withHost("zeebe-0");
    gateway
        .withContactPoint(broker.getInternalAddress(ZeebePort.INTERNAL_API))
        .withEnv("ZEEBE_GATEWAY_CLUSTER_CONTACTPOINT", broker.getContactPoint());

    // start both containers
    Stream.of(gateway, broker).parallel().forEach(Startable::start);

    final ZeebeClient zeebeClient = createZeebeClient(gateway);

    // wait a little while to give the broker and gateway a chance to find each other
    await().atMost(60, SECONDS).untilAsserted(() -> assertTopologyIsComplete(zeebeClient));

    final Integer actuatorPort = gateway.getMappedPort(MONITORING_PORT_IN_CONTAINER);
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

  private void assertTopologyIsComplete(final ZeebeClient zeebeClient) {
    final var topology = zeebeClient.newTopologyRequest().send().join();
    assertThat(topology).isComplete(1, 1);
  }

  @Test
  public void shouldReportLivenessDownIfNotConnectedToBroker() throws InterruptedException {
    // --- given ---------------------------------------
    final ZeebeStandaloneGatewayContainer gateway =
        new ZeebeStandaloneGatewayContainer("current-test")
            .withExposedPorts(MONITORING_PORT_IN_CONTAINER);
    gateway.start();

    final Integer actuatorPort = gateway.getMappedPort(MONITORING_PORT_IN_CONTAINER);
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

  private static ZeebeClient createZeebeClient(final ZeebeStandaloneGatewayContainer gateway) {
    return ZeebeClient.newClientBuilder()
        .brokerContactPoint(gateway.getExternalAddress(ZeebePort.GATEWAY))
        .usePlaintext()
        .build();
  }
}
