/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.probes;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

@RestController
public class MonitoringRestController {

  private static final String METRICS_URI = "/metrics";
  private static final String GATEWAY_HEALTH_STATUS_URI = "/health";
  private static final String GATEWAY_LIVENESS_STATUS_URI = "/live";
  private static final String GATEWAY_STARTED_STATUS_URI = "/startup";

  @GetMapping(value = METRICS_URI)
  public ModelAndView metrics() {
    return new ModelAndView("forward:/actuator/prometheus");
  }

  @GetMapping(value = GATEWAY_HEALTH_STATUS_URI)
  public ModelAndView health() {
    return new ModelAndView("forward:/actuator/health");
  }

  @GetMapping(value = GATEWAY_LIVENESS_STATUS_URI)
  public ModelAndView live() {
    return new ModelAndView("forward:/actuator/health/liveness");
  }

  @GetMapping(value = GATEWAY_STARTED_STATUS_URI)
  public ModelAndView started() {
    return new ModelAndView("forward:/actuator/health/startup");
  }
}
