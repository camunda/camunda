/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.monitoring;

import io.camunda.zeebe.broker.MicronautBrokerBridge;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller
public class MonitoringRestController {

  private static final String BROKER_READY_STATUS_URI = "/ready";
  private static final String BROKER_STARTUP_STATUS_URI = "/startup";
  private static final String BROKER_HEALTH_STATUS_URI = "/health";

  private final MicronautBrokerBridge micronautBrokerBridge;

  public MonitoringRestController(final MicronautBrokerBridge micronautBrokerBridge) {
    this.micronautBrokerBridge = micronautBrokerBridge;
  }

  @Get(value = BROKER_HEALTH_STATUS_URI)
  public HttpStatus health() {
    final boolean brokerHealthy =
        micronautBrokerBridge
            .getBrokerHealthCheckService()
            .map(BrokerHealthCheckService::isBrokerHealthy)
            .orElse(false);

    final HttpStatus status;
    if (brokerHealthy) {
      status = HttpStatus.NO_CONTENT;
    } else {
      status = HttpStatus.SERVICE_UNAVAILABLE;
    }
    return status;
  }

  @Get(value = BROKER_READY_STATUS_URI)
  public HttpStatus ready() {
    final boolean brokerReady =
        micronautBrokerBridge
            .getBrokerHealthCheckService()
            .map(BrokerHealthCheckService::isBrokerReady)
            .orElse(false);

    final HttpStatus status;
    if (brokerReady) {
      status = HttpStatus.NO_CONTENT;
    } else {
      status = HttpStatus.SERVICE_UNAVAILABLE;
    }
    return status;
  }

  @Get(value = BROKER_STARTUP_STATUS_URI)
  public HttpStatus startup() {
    final boolean brokerStarted =
        micronautBrokerBridge
            .getBrokerHealthCheckService()
            .map(BrokerHealthCheckService::isBrokerStarted)
            .orElse(false);

    final HttpStatus status;
    if (brokerStarted) {
      status = HttpStatus.NO_CONTENT;
    } else {
      status = HttpStatus.SERVICE_UNAVAILABLE;
    }
    return status;
  }
}
