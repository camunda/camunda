/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.commons.CommonsModuleConfiguration;
import io.camunda.application.listeners.ApplicationErrorListener;
import io.camunda.optimize.OptimizeModuleConfiguration;
import org.springframework.boot.SpringBootConfiguration;

@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneOptimize {

  private static final String LOCATION_SEPARATOR = ",";

  public static void main(final String[] args) {
    // To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    MainSupport.putSystemPropertyIfAbsent(
        "java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");

    // Workaround for https://github.com/spring-projects/spring-boot/issues/26627
    MainSupport.putSystemPropertyIfAbsent(
        "spring.config.location",
        String.join(
            LOCATION_SEPARATOR,
            "optional:classpath:/",
            "optional:classpath:/config/",
            "optional:file:./",
            "optional:file:./config/"));

    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/optimize-banner.txt");

    final var standaloneOptimizeApplication =
        MainSupport.createDefaultApplicationBuilder()
            .sources(
                CommonsModuleConfiguration.class,
                OptimizeModuleConfiguration.class
            )
            .profiles(Profile.OPTIMIZE.getId(), Profile.STANDALONE.getId())
            .addCommandLineProperties(true)
            .listeners(new ApplicationErrorListener())
            .build(args);

    standaloneOptimizeApplication.run(args);
  }
}
