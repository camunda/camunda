/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.restore.PhysicalTenantRestoreConfigurations.PhysicalTenantBrokerConfigurations;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
@NullMarked
final class BackupStoreComponent {

  private final PhysicalTenantBrokerConfigurations physicalTenantConfigurations;

  @Autowired
  BackupStoreComponent(final PhysicalTenantBrokerConfigurations physicalTenantConfigurations) {
    this.physicalTenantConfigurations = physicalTenantConfigurations;
  }

  @Bean(destroyMethod = "close")
  PhysicalTenantBackupStores backupStores() {
    return new PhysicalTenantBackupStores(physicalTenantConfigurations.configurations());
  }
}
