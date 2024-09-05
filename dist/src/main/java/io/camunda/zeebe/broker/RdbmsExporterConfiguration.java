/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.camunda.db.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.exporter.rdbms.RdbmsExporterFactory;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import org.slf4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(RdbmsConfiguration.class)
@ConditionalOnProperty(prefix = "camunda.database", name = "type", havingValue = "rdbms")
public class RdbmsExporterConfiguration {

  private static final Logger LOGGER = Loggers.SYSTEM_LOGGER;

  @Bean
  public RdbmsExporterFactory rdbmsExporterFactory(final RdbmsService rdbmsService) {
    return new RdbmsExporterFactory(rdbmsService);
  }

  @Bean
  public ExporterDescriptor rdbmsExporterDescriptor(final RdbmsExporterFactory rdbmsExporterFactory) {
    LOGGER.info("Provide ExporterDescriptor for RDBMS Exporter");
    return new ExporterDescriptor(rdbmsExporterFactory.exporterId(), rdbmsExporterFactory);
  }
}
