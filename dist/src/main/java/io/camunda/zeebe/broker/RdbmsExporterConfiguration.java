/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.camunda.application.commons.rdbms.RdbmsConfiguration;
import io.camunda.application.commons.rdbms.RdbmsExporterStack;
import io.camunda.application.commons.rdbms.RdbmsExporterStackFactory;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.Rdbms;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.configuration.conditions.ConditionalOnSecondaryStorageType;
import io.camunda.db.rdbms.RdbmsSchemaManager;
import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.exporter.rdbms.RdbmsExporterFactory;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptor;
import io.camunda.zeebe.broker.exporter.repo.ExporterDescriptorProvider;
import io.camunda.zeebe.broker.system.configuration.ExporterCfg;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
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

  /**
   * Provides the {@link ExporterDescriptorProvider} for RDBMS exporters. In single-exporter mode
   * (backwards-compatible), provides a single descriptor for the "rdbms" exporter. In
   * multi-exporter mode (when {@code camunda.data.secondary-storage.rdbms.exporters} is
   * configured), provides one descriptor per named exporter entry, each backed by its own
   * independent RDBMS stack.
   */
  @Bean
  public ExporterDescriptorProvider rdbmsExporterDescriptorProvider(
      final RdbmsExporterFactory rdbmsExporterFactory,
      final BrokerBasedProperties brokerBasedProperties,
      final Camunda camundaConfig,
      final MeterRegistry meterRegistry) {
    final Rdbms rdbmsConfig = camundaConfig.getData().getSecondaryStorage().getRdbms();

    if (rdbmsConfig.getExporters().isEmpty()) {
      // Single-exporter mode (backwards-compatible)
      LOGGER.info("Providing ExporterDescriptor for single RDBMS Exporter");
      final ExporterDescriptor descriptor =
          new ExporterDescriptor(
              rdbmsExporterFactory.exporterId(),
              rdbmsExporterFactory,
              Optional.ofNullable(brokerBasedProperties.getRdbmsExporter())
                  .map(ExporterCfg::getArgs)
                  .orElseGet(Map::of));
      return () -> List.of(descriptor);
    } else {
      // Multi-exporter mode: one independent stack per configured exporter
      LOGGER.info(
          "Providing ExporterDescriptors for {} RDBMS Exporters: {}",
          rdbmsConfig.getExporters().size(),
          rdbmsConfig.getExporters().keySet());
      return buildMultipleExporterDescriptors(rdbmsConfig, brokerBasedProperties, meterRegistry);
    }
  }

  private ExporterDescriptorProvider buildMultipleExporterDescriptors(
      final Rdbms rdbmsConfig,
      final BrokerBasedProperties brokerBasedProperties,
      final MeterRegistry meterRegistry) {
    final List<ExporterDescriptor> descriptors = new ArrayList<>();
    for (final Map.Entry<String, Rdbms> entry : rdbmsConfig.getExporters().entrySet()) {
      final String exporterName = "rdbms-" + entry.getKey();
      LOGGER.info("Building independent RDBMS exporter stack for: {}", exporterName);
      try {
        final RdbmsExporterStack stack =
            RdbmsExporterStackFactory.build(entry.getValue(), meterRegistry);
        final RdbmsExporterFactory factory =
            new RdbmsExporterFactory(
                stack.rdbmsService(), stack.vendorDatabaseProperties(), stack.rdbmsSchemaManager());
        final ExporterDescriptor descriptor =
            new ExporterDescriptor(
                exporterName,
                factory,
                Optional.ofNullable(brokerBasedProperties.getExporterByName(exporterName))
                    .map(ExporterCfg::getArgs)
                    .orElseGet(Map::of));
        descriptors.add(descriptor);
        LOGGER.info("Successfully configured RDBMS exporter: {}", exporterName);
      } catch (final Exception e) {
        throw new IllegalStateException(
            "Failed to build RDBMS exporter stack for exporter '" + exporterName + "'", e);
      }
    }
    return () -> descriptors;
  }
}
