/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.ACTUATOR_PORT_PROPERTY_KEY;
import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CAMUNDA_OPTIMIZE_DATABASE;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.util.tomcat.LoggingConfigurationReader;
import io.camunda.search.connect.configuration.DatabaseConfig;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan(excludeFilters = @ComponentScan.Filter(IgnoreDuringScan.class))
@SpringBootApplication(exclude = {FreeMarkerAutoConfiguration.class})
public class Main {

  public static void main(final String[] args) {
    new LoggingConfigurationReader().defineLog4jLoggingConfiguration();

    // Check for camunda.database.type=none and override CAMUNDA_OPTIMIZE_DATABASE
    final String databaseType = System.getProperty("camunda.database.type", 
        System.getenv("CAMUNDA_DATABASE_TYPE"));
    
    if (DatabaseConfig.NONE.equalsIgnoreCase(databaseType)) {
      System.setProperty(CAMUNDA_OPTIMIZE_DATABASE, DatabaseConfig.NONE);
    }

    final ConfigurationService configurationService = ConfigurationService.createDefault();
    final SpringApplication optimize = new SpringApplication(Main.class);

    final Map<String, Object> defaultProperties = new HashMap<>();
    defaultProperties.put(ACTUATOR_PORT_PROPERTY_KEY, configurationService.getActuatorPort());

    optimize.setDefaultProperties(defaultProperties);
    optimize.run(args);
  }
}
