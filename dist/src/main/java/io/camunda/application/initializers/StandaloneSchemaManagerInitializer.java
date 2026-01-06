/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.initializers;

import static io.camunda.spring.utils.DatabaseTypeUtils.UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE;

import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import java.util.Optional;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Ensures that the Standalone Schema Manager is only used for Elasticsearch databases. */
public class StandaloneSchemaManagerInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(final ConfigurableApplicationContext applicationContext) {
    final String databaseTypeProperty =
        applicationContext
            .getEnvironment()
            .getProperty(UNIFIED_CONFIG_PROPERTY_CAMUNDA_DATABASE_TYPE);

    final var storageType =
        Optional.ofNullable(databaseTypeProperty)
            .map(String::toLowerCase)
            .map(SecondaryStorageType::valueOf)
            .orElse(SecondaryStorageType.elasticsearch);
    if (!storageType.isElasticSearch() && !storageType.isOpenSearch()) {
      throw new IllegalArgumentException(
          "Cannot create schema for anything other than Elasticsearch and Opensearch with this script for now...");
    }
  }
}
