/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.initializers;

import static io.camunda.search.connect.configuration.ConnectConfiguration.DATABASE_TYPE_DEFAULT;
import static io.camunda.search.connect.configuration.DatabaseType.ELASTICSEARCH;
import static java.util.Optional.ofNullable;

import io.camunda.search.connect.configuration.DatabaseType;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class StandaloneSchemaManagerInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(final ConfigurableApplicationContext applicationContext) {
    final String databaseTypeProperty =
        applicationContext.getEnvironment().getProperty("camunda.database.type");
    if (ELASTICSEARCH
        != ofNullable(databaseTypeProperty).map(DatabaseType::from).orElse(DATABASE_TYPE_DEFAULT)) {
      throw new IllegalArgumentException(
          "Cannot create schema for anything other than Elasticsearch with this script for now...");
    }
  }
}
