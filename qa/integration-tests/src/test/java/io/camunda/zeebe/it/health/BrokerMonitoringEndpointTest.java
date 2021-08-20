/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.health;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.containsString;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import io.camunda.zeebe.test.util.testcontainers.ZeebeTestContainerDefaults;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.zeebe.containers.ZeebeContainer;
import io.zeebe.containers.ZeebePort;
import java.util.concurrent.TimeUnit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public final class BrokerMonitoringEndpointTest {

  static ZeebeContainer sutBroker;

  static RequestSpecification brokerServerSpec;

  @BeforeClass
  public static void setUpClass() {
    sutBroker = new ZeebeContainer(ZeebeTestContainerDefaults.defaultTestImage());

    sutBroker.start();

    final Integer monitoringPort = sutBroker.getMappedPort(ZeebePort.MONITORING.getPort());
    final String containerIPAddress = sutBroker.getExternalHost();

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
  public void shouldGetMetrics() {
    given()
        .spec(brokerServerSpec)
        .when()
        .get("metrics")
        .then() //
        .statusCode(200)
        .body(
            containsString("jvm_info"), // example JVM metric
            containsString("zeebe_health"), // example zebe metric
            containsString(
                "zeebe_rocksdb_writes_actual_delayed_write_rate") // exanmple rocks db metric
            );
  }

  @Test
  public void shouldGetReadyStatus() {
    await("Ready Status")
        .atMost(60, TimeUnit.SECONDS)
        .until(() -> given().spec(brokerServerSpec).when().get("ready").statusCode() == 204);
  }

  @Test
  public void shouldGetHealthStatus() {
    await("Health Status")
        .atMost(60, TimeUnit.SECONDS)
        .until(() -> given().spec(brokerServerSpec).when().get("health").statusCode() == 204);
  }

  @Test
  public void shouldGetStartupStatus() {
    await("Startup Status")
        .atMost(60, TimeUnit.SECONDS)
        .until(() -> given().spec(brokerServerSpec).when().get("startup").statusCode() == 204);
  }
}
