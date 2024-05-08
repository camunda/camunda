/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.initializers.DefaultAuthenticationInitializer;
import io.camunda.application.listeners.ApplicationErrorListener;
import io.camunda.tasklist.TasklistModuleConfiguration;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringBootConfiguration;

@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneTasklist {

  public static final String TASKLIST_STATIC_RESOURCES_LOCATION =
      "classpath:/META-INF/resources/tasklist/";
  public static final String SPRING_THYMELEAF_PREFIX_KEY = "spring.thymeleaf.prefix";
  public static final String SPRING_THYMELEAF_PREFIX_VALUE = TASKLIST_STATIC_RESOURCES_LOCATION;

  public static void main(final String[] args) {
    // To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    MainSupport.putSystemPropertyIfAbsent(
        "java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    // Workaround for https://github.com/spring-projects/spring-boot/issues/26627
    MainSupport.putSystemPropertyIfAbsent(
        "spring.config.location",
        "optional:classpath:/,optional:classpath:/config/,optional:file:./,optional:file:./config/");

    // show Operate banner
    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/tasklist-banner.txt");

    final var standaloneOperateApplication =
        MainSupport.createDefaultApplicationBuilder()
            .sources(TasklistModuleConfiguration.class)
            .profiles(Profile.TASKLIST.getId(), Profile.STANDALONE.getId())
            .addCommandLineProperties(true)
            .properties(getDefaultProperties())
            .initializers(new DefaultAuthenticationInitializer())
            .listeners(new ApplicationErrorListener())
            .build(args);

    standaloneOperateApplication.run(args);
  }

  private static Map<String, Object> getDefaultProperties() {
    final var defaultProperties = new HashMap<String, Object>();
    defaultProperties.putAll(getManagementProperties());
    defaultProperties.putAll(getGraphqlProperties());
    defaultProperties.putAll(getWebProperties());
    return defaultProperties;
  }

  private static Map<String, Object> getGraphqlProperties() {
    // GraphQL's inspection tool is disabled by default
    // Exception handler is enabled
    return Map.of(
        "graphql.playground.enabled", "false",
        "graphql.servlet.exception-handlers-enabled", "true",
        "graphql.extended-scalars", "DateTime",
        "graphql.schema-strategy", "annotations",
        "graphql.annotations.base-package", "io.camunda.tasklist",
        "graphql.annotations.always-prettify", "false",
        "graphql.annotations.input-prefix", "");
  }

  private static Map<String, Object> getWebProperties() {
    return Map.of(
        "server.servlet.session.cookie.name",
        TasklistURIs.COOKIE_JSESSIONID,
        "spring.thymeleaf.check-template-location",
        "true",
        SPRING_THYMELEAF_PREFIX_KEY,
        SPRING_THYMELEAF_PREFIX_VALUE,
        // Return error messages for all endpoints by default, except for Internal API.
        // Internal API error handling is defined in InternalAPIErrorController.
        "server.error.include-message",
        "always");
  }

  public static Map<String, Object> getManagementProperties() {
    return Map.of(
        // disable default health indicators:
        // https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-health-indicators
        "management.health.defaults.enabled", "false",

        // enable Kubernetes health groups:
        // https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-kubernetes-probes
        "management.endpoint.health.probes.enabled", "true",

        // enable health check and metrics endpoints
        "management.endpoints.web.exposure.include",
            "health, prometheus, loggers, usage-metrics, backups",

        // add custom check to standard readiness check
        "management.endpoint.health.group.readiness.include", "readinessState,searchEngineCheck");
  }
}
