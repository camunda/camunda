/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.standalone;

import io.camunda.operate.OperateProfileService;
import io.camunda.operate.data.DataGenerator;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.shared.MainSupport;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;

@SpringBootApplication(proxyBeanMethods = false)
public class StandaloneCamunda {

  public static final String SPRING_THYMELEAF_PREFIX_KEY = "spring.thymeleaf.prefix";
  public static final String SPRING_THYMELEAF_PREFIX_VALUE = "classpath:/META-INF/resources/";
  private static final Logger LOGGER = Loggers.SYSTEM_LOGGER;

  public static void main(final String[] args) {
    MainSupport.setDefaultGlobalConfiguration();
    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/assets/zeebe_broker_banner.txt");
    MainSupport.putSystemPropertyIfAbsent(
        "java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    MainSupport.putSystemPropertyIfAbsent(
        "spring.config.location",
        "optional:classpath:/,optional:classpath:/config/,optional:file:./,optional:file:./config/");

    final var application =
        MainSupport.createDefaultApplicationBuilder()
            .sources(StandaloneCamunda.class)
            .profiles("operate", "zeebe", "broker")
            .build(args);

    setDefaultProperties(application);
    setDefaultAuthProfile(application);
    application.run(args);
  }

  private static void setDefaultAuthProfile(final SpringApplication springApplication) {
    springApplication.addInitializers(
        configurableApplicationContext -> {
          final ConfigurableEnvironment env = configurableApplicationContext.getEnvironment();
          final Set<String> activeProfiles = Set.of(env.getActiveProfiles());
          if (OperateProfileService.AUTH_PROFILES.stream().noneMatch(activeProfiles::contains)) {
            env.addActiveProfile(OperateProfileService.DEFAULT_AUTH);
          }
        });
  }

  private static void setDefaultProperties(final SpringApplication springApplication) {
    final Map<String, Object> defaultsProperties = new HashMap<>();
    defaultsProperties.putAll(getWebProperties());
    defaultsProperties.putAll(getManagementProperties());
    springApplication.setDefaultProperties(defaultsProperties);
  }

  private static Map<String, Object> getWebProperties() {
    return Map.of(
        "server.servlet.session.cookie.name",
        "OPERATE-SESSION",
        SPRING_THYMELEAF_PREFIX_KEY,
        SPRING_THYMELEAF_PREFIX_VALUE,
        "spring.mvc.pathmatch.matching-strategy",
        "ANT_PATH_MATCHER",
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
        "management.endpoints.web.exposure.include", "health, prometheus, loggers, usage-metrics",

        // add custom check to standard readiness check
        "management.endpoint.health.group.readiness.include", "readinessState,indicesCheck");
  }

  @Bean(name = "dataGenerator")
  @ConditionalOnMissingBean
  public DataGenerator stubDataGenerator() {
    LOGGER.debug("Create Data generator stub");
    return DataGenerator.DO_NOTHING;
  }
}
