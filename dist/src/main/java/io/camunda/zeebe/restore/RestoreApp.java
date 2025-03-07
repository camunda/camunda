/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.application.MainSupport;
import io.camunda.application.Profile;
import io.camunda.application.commons.configuration.BrokerBasedConfiguration;
import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration;
import io.camunda.application.commons.search.SearchClientDatabaseConfiguration;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.broker.RdbmsExporterConfiguration;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Import;

@SpringBootApplication(scanBasePackages = {"io.camunda.zeebe.restore"})
@ConfigurationPropertiesScan(basePackages = {"io.camunda.zeebe.restore"})
@Import(
    value = {
      BrokerBasedConfiguration.class,
      WorkingDirectoryConfiguration.class,
      RdbmsExporterConfiguration.class,
      SearchClientDatabaseConfiguration.class,
    })
public class RestoreApp implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(RestoreApp.class);
  private final BrokerCfg configuration;
  private final BackupStore backupStore;

  @Value("${backupId}")
  // Parsed from commandline Eg:-`--backupId=100`
  private long backupId;

  private final RestoreConfiguration restoreConfiguration;
  private final MeterRegistry meterRegistry;
  private final ExporterRepository exporterRepository;

  @Autowired
  public RestoreApp(
      final BrokerBasedConfiguration configuration,
      final BackupStore backupStore,
      final RestoreConfiguration restoreConfiguration,
      final MeterRegistry meterRegistry,
      final ExporterRepository exporterRepository) {
    this.configuration = configuration.config();
    this.backupStore = backupStore;
    this.restoreConfiguration = restoreConfiguration;
    this.meterRegistry = meterRegistry;
    this.exporterRepository = exporterRepository;
  }

  public static void main(final String[] args) {
    MainSupport.setDefaultGlobalConfiguration();

    final var application =
        MainSupport.createDefaultApplicationBuilder()
            .web(WebApplicationType.SERVLET)
            .sources(RestoreApp.class)
            .profiles(Profile.RESTORE.getId())
            .build();

    application.run(args);
  }

  @Override
  public void run(final ApplicationArguments args) {
    LOG.info("Starting to restore from backup {}", backupId);
    new RestoreManager(configuration, backupStore, meterRegistry, exporterRepository)
        .restore(backupId, restoreConfiguration.validateConfig())
        .join();
    LOG.info("Successfully restored broker from backup {}", backupId);
  }
}
