/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.health;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.cluster.TestZeebePort;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.RequestLoggingFilter;
import io.restassured.filter.log.ResponseLoggingFilter;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public final class BrokerMonitoringEndpointTest {

  static RequestSpecification brokerServerSpec;

  @TestZeebe(initMethod = "initTestStandaloneBroker")
  private static TestStandaloneBroker broker;

  @SuppressWarnings("unused")
  static void initTestStandaloneBroker() {
    broker = new TestStandaloneBroker().withProperty("management.server.base-path", "/foo");
  }

  @BeforeAll
  static void setUpClass() {
    brokerServerSpec =
        new RequestSpecBuilder()
            .setContentType(ContentType.TEXT)
            // set URL explicitly since we want to ensure the mapping is correct
            .setBaseUri("http://localhost:" + broker.mappedPort(TestZeebePort.MONITORING) + "/foo")
            .addFilter(new ResponseLoggingFilter())
            .addFilter(new RequestLoggingFilter())
            .build();
  }

  @Test
  void shouldGetReadyStatus() {
    await("Ready Status")
        .atMost(60, TimeUnit.SECONDS)
        .until(() -> given().spec(brokerServerSpec).when().get("ready").statusCode() == 200);
  }

  @Test
  void shouldGetHealthStatus() {
    await("Health Status")
        .atMost(60, TimeUnit.SECONDS)
        .until(() -> given().spec(brokerServerSpec).when().get("health").statusCode() == 200);
  }

  @Test
  void shouldGetStartupStatus() {
    await("Startup Status")
        .atMost(60, TimeUnit.SECONDS)
        .until(() -> given().spec(brokerServerSpec).when().get("startup").statusCode() == 200);
  }
}
