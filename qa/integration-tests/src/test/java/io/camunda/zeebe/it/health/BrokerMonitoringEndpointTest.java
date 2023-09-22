/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.health;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
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
  @TestZeebe private static final TestStandaloneBroker BROKER = new TestStandaloneBroker();

  @BeforeAll
  static void setUpClass() {
    brokerServerSpec =
        new RequestSpecBuilder()
            .setContentType(ContentType.TEXT)
            .setBaseUri("http://" + BROKER.monitoringAddress())
            .addFilter(new ResponseLoggingFilter())
            .addFilter(new RequestLoggingFilter())
            .build();
  }

  @Test
  void shouldGetReadyStatus() {
    await("Ready Status")
        .atMost(60, TimeUnit.SECONDS)
        .until(() -> given().spec(brokerServerSpec).when().get("ready").statusCode() == 204);
  }

  @Test
  void shouldGetHealthStatus() {
    await("Health Status")
        .atMost(60, TimeUnit.SECONDS)
        .until(() -> given().spec(brokerServerSpec).when().get("health").statusCode() == 204);
  }

  @Test
  void shouldGetStartupStatus() {
    await("Startup Status")
        .atMost(60, TimeUnit.SECONDS)
        .until(() -> given().spec(brokerServerSpec).when().get("startup").statusCode() == 204);
  }
}
