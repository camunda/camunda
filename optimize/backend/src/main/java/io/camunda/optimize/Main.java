/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.jetty.OptimizeResourceConstants.ACTUATOR_PORT_PROPERTY_KEY;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@Slf4j
@ComponentScan(excludeFilters = @ComponentScan.Filter(IgnoreDuringScan.class))
@SpringBootApplication(exclude = {FreeMarkerAutoConfiguration.class})
public class Main {
  public static void main(String[] args) {
    // Early logging before any Spring Boot initialization
    log.info("[OPTIMIZE-MAIN] Starting Optimize main method at {}", java.time.Instant.now());
    log.info("[OPTIMIZE-MAIN] Java version: {}", System.getProperty("java.version"));
    log.info("[OPTIMIZE-MAIN] Java home: {}", System.getProperty("java.home"));
    log.info("[OPTIMIZE-MAIN] Working directory: {}", System.getProperty("user.dir"));
    log.info(
        "[OPTIMIZE-MAIN] Available processors: {}", Runtime.getRuntime().availableProcessors());
    log.info("[OPTIMIZE-MAIN] Max memory: {} MB", Runtime.getRuntime().maxMemory() / (1024 * 1024));
    log.info("[OPTIMIZE-MAIN] Command line args: {}", java.util.Arrays.toString(args));

    // Log critical environment variables
    log.info(
        "[OPTIMIZE-MAIN] CAMUNDA_OPTIMIZE_DATABASE: {}",
        System.getenv("CAMUNDA_OPTIMIZE_DATABASE"));
    log.info(
        "[OPTIMIZE-MAIN] CAMUNDA_OPTIMIZE_OPENSEARCH_HTTP_PORT: {}",
        System.getenv("CAMUNDA_OPTIMIZE_OPENSEARCH_HTTP_PORT"));
    log.info("[OPTIMIZE-MAIN] SPRING_PROFILES_ACTIVE: {}", System.getenv("SPRING_PROFILES_ACTIVE"));

    try {
      log.info("[OPTIMIZE-MAIN] Creating SpringApplication...");
      SpringApplication optimize = new SpringApplication(Main.class);

      log.info("[OPTIMIZE-MAIN] Creating ConfigurationService...");
      final ConfigurationService configurationService = ConfigurationService.createDefault();
      log.info("[OPTIMIZE-MAIN] ConfigurationService created successfully");

      log.info("[OPTIMIZE-MAIN] Setting default properties...");
      optimize.setDefaultProperties(
          Collections.singletonMap(
              ACTUATOR_PORT_PROPERTY_KEY, configurationService.getActuatorPort()));
      log.info("[OPTIMIZE-MAIN] Actuator port set to: {}", configurationService.getActuatorPort());

      log.info("[OPTIMIZE-MAIN] Starting Spring Boot application...");
      optimize.run(args);
      log.info("[OPTIMIZE-MAIN] Spring Boot application started successfully");
    } catch (Exception e) {
      log.error(
          "[OPTIMIZE-MAIN] FATAL: Exception during startup: {}: {}",
          e.getClass().getSimpleName(),
          e.getMessage(),
          e);
      log.error("[OPTIMIZE-MAIN] Optimize startup failed, exiting with code 1");
      System.exit(1);
    }
  }
}
