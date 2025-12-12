/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.db.rdbms.RdbmsSchemaManager;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.exporter.rdbms.RdbmsExporterFactory;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(RdbmsConfiguration.class)
@ConditionalOnSecondaryStorageType(SecondaryStorageType.rdbms)
public class RdbmsExporterConfiguration {

  private static final Logger LOGGER = Loggers.SYSTEM_LOGGER;

  @Bean
  public RdbmsExporterFactory rdbmsExporterFactory(
      final RdbmsService rdbmsService,
      final VendorDatabaseProperties vendorDatabaseProperties,
      final RdbmsSchemaManager rdbmsSchemaManager) {
    return new RdbmsExporterFactory(rdbmsService, vendorDatabaseProperties, rdbmsSchemaManager);
  }

  @Bean
  public ExporterDescriptor rdbmsExporterDescriptor(
      final RdbmsExporterFactory rdbmsExporterFactory,
      final BrokerBasedProperties brokerBasedProperties) {
    LOGGER.info("Provide ExporterDescriptor for RDBMS Exporter");
    return new ExporterDescriptor(
        rdbmsExporterFactory.exporterId(),
        rdbmsExporterFactory,
        Optional.ofNullable(brokerBasedProperties.getRdbmsExporter())
            .map(ExporterCfg::getArgs)
            .orElseGet(Map::of));
  }
}
