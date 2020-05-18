/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.monitoring;

import io.zeebe.broker.SpringBrokerBridge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

@RestController
public class MonitoringRestController {

  private static final String BROKER_READY_STATUS_URI = "/ready";
  private static final String METRICS_URI = "/metrics";
  private static final String BROKER_HEALTH_STATUS_URI = "/health";

  @Autowired private SpringBrokerBridge springBrokerBridge;

  @GetMapping(value = METRICS_URI)
  public ModelAndView metrics() {
    return new ModelAndView("forward:/actuator/prometheus");
  }

  @GetMapping(value = BROKER_HEALTH_STATUS_URI)
  public ResponseEntity<String> health() {
    final boolean brokerHealthy =
        springBrokerBridge
            .getBrokerHealthCheckService()
            .map(BrokerHealthCheckService::isBrokerHealthy)
            .orElse(false);

    final HttpStatus status;
    if (brokerHealthy) {
      status = HttpStatus.NO_CONTENT;
    } else {
      status = HttpStatus.SERVICE_UNAVAILABLE;
    }
    return new ResponseEntity<>(status);
  }

  @GetMapping(value = BROKER_READY_STATUS_URI)
  public ResponseEntity<String> ready() {
    final boolean brokerReady =
        springBrokerBridge
            .getBrokerHealthCheckService()
            .map(BrokerHealthCheckService::isBrokerReady)
            .orElse(false);

    final HttpStatus status;
    if (brokerReady) {
      status = HttpStatus.NO_CONTENT;
    } else {
      status = HttpStatus.SERVICE_UNAVAILABLE;
    }
    return new ResponseEntity<>(status);
  }
}
