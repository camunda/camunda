/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.ACTUATOR_PORT_PROPERTY_KEY;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.util.tomcat.LoggingConfigurationReader;
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

    // Check if running in no-db mode and fail fast if so
    checkForNoSecondaryStorageMode();

    final ConfigurationService configurationService = ConfigurationService.createDefault();
    final SpringApplication optimize = new SpringApplication(Main.class);

    final Map<String, Object> defaultProperties = new HashMap<>();
    defaultProperties.put(ACTUATOR_PORT_PROPERTY_KEY, configurationService.getActuatorPort());

    optimize.setDefaultProperties(defaultProperties);
    optimize.run(args);
  }

  private static void checkForNoSecondaryStorageMode() {
    final String databaseType = getDatabaseType();
    
    if (isNoSecondaryStorageMode(databaseType)) {
      final String errorMessage = "Optimize is not supported without secondary storage. "
          + "The database type is configured as 'none', but Optimize requires a secondary storage "
          + "backend (Elasticsearch or OpenSearch) to function properly. "
          + "Please configure 'camunda.database.type' to either 'elasticsearch' or 'opensearch', "
          + "or remove Optimize from your deployment when running in no-secondary-storage mode.";
      
      System.err.println("ERROR: " + errorMessage);
      System.exit(1);
    }
  }

  static boolean isNoSecondaryStorageMode(final String databaseType) {
    return "none".equalsIgnoreCase(databaseType);
  }

  static String getDatabaseType() {
    // Check environment variable first (standard Spring Boot pattern)
    String databaseType = System.getenv("CAMUNDA_DATABASE_TYPE");
    
    if (databaseType == null) {
      // Check system property (for -Dcamunda.database.type=none)
      databaseType = System.getProperty("camunda.database.type");
    }
    
    // Default to elasticsearch if not specified
    return databaseType != null ? databaseType : "elasticsearch";
  }
}
