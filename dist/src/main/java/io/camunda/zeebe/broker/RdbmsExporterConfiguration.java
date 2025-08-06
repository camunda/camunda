/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.exporter.rdbms.RdbmsExporterFactory;
import io.camunda.search.connect.configuration.DatabaseConfig;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(RdbmsConfiguration.class)
@ConditionalOnProperty(
    prefix = "camunda.database",
    name = "type",
    havingValue = DatabaseConfig.RDBMS)
public class RdbmsExporterConfiguration {

  private static final Logger LOGGER = Loggers.SYSTEM_LOGGER;

  @Bean
  public RdbmsExporterFactory rdbmsExporterFactory(final RdbmsService rdbmsService) {
    return new RdbmsExporterFactory(rdbmsService);
  }

  @Bean
  public ExporterDescriptor rdbmsExporterDescriptor(
      final RdbmsExporterFactory rdbmsExporterFactory, final ExporterCfg rdbmsExporterCfg) {
    LOGGER.debug("Provide ExporterDescriptor for RDBMS Exporter");
    return new ExporterDescriptor(
        rdbmsExporterFactory.exporterId(),
        rdbmsExporterFactory,
        Optional.ofNullable(rdbmsExporterCfg).map(ExporterCfg::getArgs).orElseGet(Map::of));
  }

  @ConfigurationProperties("zeebe.broker.exporters.rdbms")
  @Bean
  public ExporterCfg rdbmsExporterCfg() {
    return new ExporterCfg();
  }
}
