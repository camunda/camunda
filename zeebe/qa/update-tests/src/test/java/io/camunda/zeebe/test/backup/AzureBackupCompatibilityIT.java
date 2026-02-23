/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.backup;

import io.camunda.configuration.Camunda;
import io.camunda.configuration.PrimaryStorageBackup;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.test.testcontainers.AzuriteContainer;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Cross-version backup compatibility test for Azure Blob Storage (using Azurite).
 *
 * <p>The Azurite container is placed on a shared network so the old broker can access it via the
 * internal alias.
 */
@Testcontainers
@ZeebeIntegration
final class AzureBackupCompatibilityIT implements BackupCompatibilityAcceptance, AfterAllCallback {
  private static final String CONTAINER_NAME =
      RandomStringUtils.insecure().nextAlphabetic(10).toLowerCase();
  private static final Network NETWORK = Network.newNetwork();

  @Container
  private static final AzuriteContainer AZURITE =
      new AzuriteContainer().withNetwork(NETWORK).withNetworkAlias("azurite");

  @Override
  public Network getNetwork() {
    return NETWORK;
  }

  @Override
  public Map<String, String> oldBrokerBackupStoreEnvVars() {
    return Map.of(
        "ZEEBE_BROKER_DATA_BACKUP_STORE",
        "AZURE",
        "ZEEBE_BROKER_DATA_BACKUP_AZURE_CONNECTIONSTRING",
        AZURITE.internalConnectionString(),
        "ZEEBE_BROKER_DATA_BACKUP_AZURE_BASEPATH",
        CONTAINER_NAME);
  }

  @Override
  public void configureCurrentBackupStore(final Camunda cfg) {
    final var backup = cfg.getData().getPrimaryStorage().getBackup();
    backup.setStore(PrimaryStorageBackup.BackupStoreType.AZURE);

    final var azure = backup.getAzure();
    azure.setConnectionString(AZURITE.externalConnectionString());
    azure.setBasePath(CONTAINER_NAME);
  }

  @Override
  public void afterAll(final ExtensionContext context) {
    NETWORK.close();
  }
}
