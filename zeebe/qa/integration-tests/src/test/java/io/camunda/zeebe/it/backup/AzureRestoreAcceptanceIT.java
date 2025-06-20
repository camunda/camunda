/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.backup;

import io.camunda.unifiedconfig.AzureStore;
import io.camunda.unifiedconfig.Backup;
import io.camunda.unifiedconfig.UnifiedConfiguration;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
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
  public void configureBackupStore(
      final BrokerCfg _cfg, // TODO: Remove this once all of the backup stores are configured
                            //  through the Unified Configuration object.
      final UnifiedConfiguration unifiedConfiguration) {
    final AzureStore azureStoreConfig = new AzureStore();
    azureStoreConfig.setConnectionString(AZURITE_CONTAINER.getConnectString());
    azureStoreConfig.setBasePath(CONTAINER_NAME);

    final var backup = unifiedConfiguration.getCamunda().getData().getBackup();
    backup.setStoreType(Backup.STORE_TYPE_AZURE);
    backup.setAzure(azureStoreConfig);
  }
}
