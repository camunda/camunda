/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.processor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLog;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;

public class CamundaConfigurationPostProcessor implements EnvironmentPostProcessor {
  private final DeferredLog log;

  public CamundaConfigurationPostProcessor(final DeferredLogFactory deferredLogFactory) {
    log = (DeferredLog) deferredLogFactory.getLog(getClass());
  }

  @Override
  public void postProcessEnvironment(
      final ConfigurableEnvironment environment, final SpringApplication application) {
    mapLegacyProperties(environment);
  }

  private void mapLegacyProperties(final ConfigurableEnvironment environment) {
    new CamundaLegacyPropertiesMapper(log, CamundaLegacyPropertiesMappingLoader.load())
        .mapProperties(environment);
  }
}
