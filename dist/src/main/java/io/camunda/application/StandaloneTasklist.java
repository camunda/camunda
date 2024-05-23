/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.initializers.DefaultAuthenticationInitializer;
import io.camunda.application.initializers.WebappsConfigurationInitializer;
import io.camunda.tasklist.TasklistModuleConfiguration;
import io.camunda.webapps.WebappsModuleConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringBootConfiguration;

@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneTasklist {

  private static final Logger LOGGER = LoggerFactory.getLogger(StandaloneTasklist.class);

  public static void main(final String[] args) {
    // To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    MainSupport.putSystemPropertyIfAbsent(
        "java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
    // Workaround for https://github.com/spring-projects/spring-boot/issues/26627
    MainSupport.putSystemPropertyIfAbsent(
        "spring.config.location",
        "optional:classpath:/,optional:classpath:/config/,optional:file:./,optional:file:./config/");
    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/tasklist-banner.txt");

    final var standaloneTasklistApplication =
        MainSupport.createDefaultApplicationBuilder()
            .sources(TasklistModuleConfiguration.class, WebappsModuleConfiguration.class)
            .profiles(Profile.TASKLIST.getId(), Profile.STANDALONE.getId())
            .addCommandLineProperties(true)
            .initializers(
                new DefaultAuthenticationInitializer(), new WebappsConfigurationInitializer())
            .listeners(new io.camunda.application.listeners.ApplicationErrorListener())
            .build(args);

    standaloneTasklistApplication.run(args);
  }
}
