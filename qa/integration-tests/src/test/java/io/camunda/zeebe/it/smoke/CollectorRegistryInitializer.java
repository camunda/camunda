/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.smoke;

import io.prometheus.client.CollectorRegistry;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * An {@link ApplicationContextInitializer} which clears the metrics registry in between tests. This
 * is required since we use a static registry, and it only allows us to registry collectors and
 * metrics explicitly once.
 */
public final class CollectorRegistryInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(final ConfigurableApplicationContext applicationContext) {
    CollectorRegistry.defaultRegistry.clear();
  }
}
