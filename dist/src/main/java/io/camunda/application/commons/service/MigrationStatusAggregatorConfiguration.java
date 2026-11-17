/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.service;

import io.camunda.cluster.migration.MigrationStatusProvider;
import io.camunda.service.MigrationStatusAggregator;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MigrationStatusAggregatorConfiguration {

  @Bean
  public MigrationStatusAggregator migrationStatusAggregator(
      final List<MigrationStatusProvider> providers) {
    return new MigrationStatusAggregator(providers);
  }
}
