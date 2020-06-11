/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.health;

import static io.restassured.RestAssured.given;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.zeebe.containers.ZeebeBrokerContainer;
import java.io.IOException;
import org.hamcrest.CoreMatchers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public final class BrokerMonitoringEndpointTest {

  static ZeebeBrokerContainer sutBroker;

  static RequestSpecification brokerServerSpec;

  @BeforeClass
  public static void setUpClass() {
    sutBroker = new ZeebeBrokerContainer("current-test").withClusterName("zeebe-cluster");

    sutBroker.start();

    final Integer monitoringPort = sutBroker.getMappedPort(9600);
    final String containerIPAddress = sutBroker.getContainerIpAddress();

    brokerServerSpec =
        new RequestSpecBuilder()
            .setContentType(ContentType.TEXT)
            .setBaseUri("http://" + containerIPAddress)
            .setPort(monitoringPort)
            .addFilter(new ResponseLoggingFilter())
            .addFilter(new RequestLoggingFilter())
            .build();
  }

  @AfterClass
  public static void tearDownClass() {
    sutBroker.stop();
  }

  @Test
  public void shouldGetMetrics() throws IOException, InterruptedException {
    given()
        .spec(brokerServerSpec)
        .when()
        .get("metrics")
        .then() //
        .statusCode(200)
        .body(CoreMatchers.containsString("zeebe_health"));
  }

  @Test
  public void shouldGetReadyStatus() throws IOException, InterruptedException {
    given()
        .spec(brokerServerSpec)
        .when()
        .get("ready")
        .then() //
        .statusCode(204);
  }

  @Test
  public void shouldGetHealthStatus() throws IOException, InterruptedException {
    given()
        .spec(brokerServerSpec)
        .when()
        .get("health")
        .then() //
        .statusCode(204);
  }
}
