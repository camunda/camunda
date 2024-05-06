/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.property;

import java.util.Map;

public class OperateApplicationProperties {
  public static final String OPERATE_STATIC_RESOURCES_LOCATION =
      "classpath:/META-INF/resources/operate/";
  public static final String SPRING_THYMELEAF_PREFIX_KEY = "spring.thymeleaf.prefix";
  public static final String SPRING_THYMELEAF_PREFIX_VALUE = OPERATE_STATIC_RESOURCES_LOCATION;

  public static Map<String, Object> getManagementProperties() {
    return Map.of(
        // disable default health indicators:
        // https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-health-indicators
        "management.health.defaults.enabled",
        "false",

        // enable Kubernetes health groups:
        // https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-kubernetes-probes
        "management.endpoint.health.probes.enabled",
        "true",

        // enable health check and metrics endpoints
        "management.endpoints.web.exposure.include",
        "health, prometheus, loggers, usage-metrics, backups",

        // add custom check to standard readiness check
        "management.endpoint.health.group.readiness.include",
        "readinessState,indicesCheck");
  }
}
