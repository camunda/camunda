/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.shared.MainSupport;
import io.camunda.zeebe.shared.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(
    scanBasePackages = {"io.camunda.zeebe.restore", "io.camunda.zeebe.broker.shared"})
@ConfigurationPropertiesScan(basePackages = {"io.camunda.zeebe.broker.shared"})
public class RestoreApp implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(RestoreApp.class);
  private final BrokerCfg configuration;
  private final BackupStore backupStore;

  @Value("${backupId}")
  // Parsed from commandline Eg:-`--backupId=100`
  private long backupId;

  @Autowired
  public RestoreApp(final BrokerCfg configuration, final BackupStore backupStore) {
    this.configuration = configuration;
    this.backupStore = backupStore;
  }

  public static void main(final String[] args) {
    MainSupport.setDefaultGlobalConfiguration();

    final var application =
        MainSupport.createDefaultApplicationBuilder()
            .web(WebApplicationType.NONE)
            .sources(RestoreApp.class)
            .profiles(Profile.RESTORE.getId())
            .build();

    application.run(args);
  }

  @Override
  public void run(final ApplicationArguments args) {
    LOG.info("Starting to restore from backup {}", backupId);
    new RestoreManager(configuration, backupStore).restore(backupId).join();
    LOG.info("Successfully restored broker from backup {}", backupId);
  }
}
