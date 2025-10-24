/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.cluster.backup;

import io.camunda.configuration.Camunda;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.backup.AzureBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.test.testcontainers.AzuriteContainer;
import org.apache.commons.lang3.RandomStringUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
final class AzureRestoreAcceptanceIT implements RestoreAcceptance {
  @Container private static final AzuriteContainer AZURITE_CONTAINER = new AzuriteContainer();
  private static final String CONTAINER_NAME = RandomStringUtils.randomAlphabetic(10).toLowerCase();

  @Override
  public void configureBackupStore(final BrokerCfg cfg) {
    final var backup = cfg.getData().getBackup();
    backup.setStore(BackupStoreType.AZURE);
    final AzureBackupStoreConfig azureBackupStoreConfig = new AzureBackupStoreConfig();
    azureBackupStoreConfig.setConnectionString(AZURITE_CONTAINER.getConnectString());
    azureBackupStoreConfig.setBasePath(CONTAINER_NAME);
    backup.setAzure(azureBackupStoreConfig);
  }

  @Override
  public void configureBackupStore(final Camunda cfg) {
    final var backup = cfg.getData().getBackup();
    backup.setStore(io.camunda.configuration.Backup.BackupStoreType.AZURE);
    final var azure = backup.getAzure();
    azure.setConnectionString(AZURITE_CONTAINER.getConnectString());
    azure.setBasePath(CONTAINER_NAME);
  }
}
