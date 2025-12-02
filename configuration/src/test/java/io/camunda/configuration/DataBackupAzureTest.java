/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  BrokerBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
@ActiveProfiles("broker")
public class DataBackupAzureTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.backup.azure.endpoint=endpointNew",
        "camunda.data.backup.azure.account-name=accountNameNew",
        "camunda.data.backup.azure.account-key=accountKeyNew",
        "camunda.data.backup.azure.connection-string=connectionStringNew",
        "camunda.data.backup.azure.base-path=basePathNew",
        "camunda.data.backup.azure.create-container=false",
      })
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetEndpoint() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getEndpoint()).isEqualTo("endpointNew");
    }

    @Test
    void shouldSetAccountName() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getAccountName())
          .isEqualTo("accountNameNew");
    }

    @Test
    void shouldSetAccountKey() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getAccountKey())
          .isEqualTo("accountKeyNew");
    }

    @Test
    void shouldSetConnectionString() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getConnectionString())
          .isEqualTo("connectionStringNew");
    }

    @Test
    void shouldSetBasePath() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getBasePath()).isEqualTo("basePathNew");
    }

    @Test
    void shouldSetCreateContainer() {
      assertThat(brokerCfg.getData().getBackup().getAzure().isCreateContainer()).isFalse();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.data.backup.azure.endpoint=endpointLegacy",
        "zeebe.broker.data.backup.azure.accountName=accountNameLegacy",
        "zeebe.broker.data.backup.azure.accountKey=accountKeyLegacy",
        "zeebe.broker.data.backup.azure.connectionString=connectionStringLegacy",
        "zeebe.broker.data.backup.azure.basePath=basePathLegacy",
        "zeebe.broker.data.backup.azure.createContainer=false",
      })
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetEndpoint() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getEndpoint())
          .isEqualTo("endpointLegacy");
    }

    @Test
    void shouldSetAccountName() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getAccountName())
          .isEqualTo("accountNameLegacy");
    }

    @Test
    void shouldSetAccountKey() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getAccountKey())
          .isEqualTo("accountKeyLegacy");
    }

    @Test
    void shouldSetConnectionString() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getConnectionString())
          .isEqualTo("connectionStringLegacy");
    }

    @Test
    void shouldSetBasePath() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getBasePath())
          .isEqualTo("basePathLegacy");
    }

    @Test
    void shouldSetCreateContainer() {
      assertThat(brokerCfg.getData().getBackup().getAzure().isCreateContainer()).isFalse();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.data.backup.azure.endpoint=endpointNew",
        "camunda.data.backup.azure.account-name=accountNameNew",
        "camunda.data.backup.azure.account-key=accountKeyNew",
        "camunda.data.backup.azure.connection-string=connectionStringNew",
        "camunda.data.backup.azure.base-path=basePathNew",
        "camunda.data.backup.azure.create-container=false",

        // legacy
        "zeebe.broker.data.backup.azure.endpoint=endpointLegacy",
        "zeebe.broker.data.backup.azure.accountName=accountNameLegacy",
        "zeebe.broker.data.backup.azure.accountKey=accountKeyLegacy",
        "zeebe.broker.data.backup.azure.connectionString=connectionStringLegacy",
        "zeebe.broker.data.backup.azure.basePath=basePathLegacy",
        "zeebe.broker.data.backup.azure.createContainer=true",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetEndpointFromNew() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getEndpoint()).isEqualTo("endpointNew");
    }

    @Test
    void shouldSetAccountNameFromNew() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getAccountName())
          .isEqualTo("accountNameNew");
    }

    @Test
    void shouldSetAccountKeyFromNew() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getAccountKey())
          .isEqualTo("accountKeyNew");
    }

    @Test
    void shouldSetConnectionStringFromNew() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getConnectionString())
          .isEqualTo("connectionStringNew");
    }

    @Test
    void shouldSetBasePathFromNew() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getBasePath()).isEqualTo("basePathNew");
    }

    @Test
    void shouldSetCreateContainerFromNew() {
      assertThat(brokerCfg.getData().getBackup().getAzure().isCreateContainer()).isFalse();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.data.primary-storage.backup.azure.endpoint=endpointPrimaryStorage",
        "camunda.data.primary-storage.backup.azure.account-name=accountNamePrimaryStorage",
        "camunda.data.primary-storage.backup.azure.account-key=accountKeyPrimaryStorage",
        "camunda.data.primary-storage.backup.azure.connection-string=connectionStringPrimaryStorage",
        "camunda.data.primary-storage.backup.azure.base-path=basePathPrimaryStorage",
        "camunda.data.primary-storage.backup.azure.create-container=true",
      })
  class WithOnlyPrimaryStorageConfigSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyPrimaryStorageConfigSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetEndpoint() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getEndpoint())
          .isEqualTo("endpointPrimaryStorage");
    }

    @Test
    void shouldSetAccountName() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getAccountName())
          .isEqualTo("accountNamePrimaryStorage");
    }

    @Test
    void shouldSetAccountKey() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getAccountKey())
          .isEqualTo("accountKeyPrimaryStorage");
    }

    @Test
    void shouldSetConnectionString() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getConnectionString())
          .isEqualTo("connectionStringPrimaryStorage");
    }

    @Test
    void shouldSetBasePath() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getBasePath())
          .isEqualTo("basePathPrimaryStorage");
    }

    @Test
    void shouldSetCreateContainer() {
      assertThat(brokerCfg.getData().getBackup().getAzure().isCreateContainer()).isTrue();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // primary-storage
        "camunda.data.primary-storage.backup.azure.endpoint=endpointPrimaryStorage",
        "camunda.data.primary-storage.backup.azure.account-name=accountNamePrimaryStorage",
        "camunda.data.primary-storage.backup.azure.account-key=accountKeyPrimaryStorage",
        "camunda.data.primary-storage.backup.azure.connection-string=connectionStringPrimaryStorage",
        "camunda.data.primary-storage.backup.azure.base-path=basePathPrimaryStorage",
        "camunda.data.primary-storage.backup.azure.create-container=true",
        // backup (unified)
        "camunda.data.backup.azure.endpoint=endpointNew",
        "camunda.data.backup.azure.account-name=accountNameNew",
        "camunda.data.backup.azure.account-key=accountKeyNew",
        "camunda.data.backup.azure.connection-string=connectionStringNew",
        "camunda.data.backup.azure.base-path=basePathNew",
        "camunda.data.backup.azure.create-container=false",
      })
  class WithPrimaryStorageAndBackupSet {
    final BrokerBasedProperties brokerCfg;

    WithPrimaryStorageAndBackupSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetEndpointFromPrimaryStorage() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getEndpoint())
          .isEqualTo("endpointPrimaryStorage");
    }

    @Test
    void shouldSetAccountNameFromPrimaryStorage() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getAccountName())
          .isEqualTo("accountNamePrimaryStorage");
    }

    @Test
    void shouldSetAccountKeyFromPrimaryStorage() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getAccountKey())
          .isEqualTo("accountKeyPrimaryStorage");
    }

    @Test
    void shouldSetConnectionStringFromPrimaryStorage() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getConnectionString())
          .isEqualTo("connectionStringPrimaryStorage");
    }

    @Test
    void shouldSetBasePathFromPrimaryStorage() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getBasePath())
          .isEqualTo("basePathPrimaryStorage");
    }

    @Test
    void shouldSetCreateContainerFromPrimaryStorage() {
      assertThat(brokerCfg.getData().getBackup().getAzure().isCreateContainer()).isTrue();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // primary-storage
        "camunda.data.primary-storage.backup.azure.endpoint=endpointPrimaryStorage",
        "camunda.data.primary-storage.backup.azure.account-name=accountNamePrimaryStorage",
        "camunda.data.primary-storage.backup.azure.account-key=accountKeyPrimaryStorage",
        "camunda.data.primary-storage.backup.azure.connection-string=connectionStringPrimaryStorage",
        "camunda.data.primary-storage.backup.azure.base-path=basePathPrimaryStorage",
        "camunda.data.primary-storage.backup.azure.create-container=true",
        // legacy
        "zeebe.broker.data.backup.azure.endpoint=endpointLegacy",
        "zeebe.broker.data.backup.azure.accountName=accountNameLegacy",
        "zeebe.broker.data.backup.azure.accountKey=accountKeyLegacy",
        "zeebe.broker.data.backup.azure.connectionString=connectionStringLegacy",
        "zeebe.broker.data.backup.azure.basePath=basePathLegacy",
        "zeebe.broker.data.backup.azure.createContainer=false",
      })
  class WithPrimaryStorageAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithPrimaryStorageAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetEndpointFromPrimaryStorage() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getEndpoint())
          .isEqualTo("endpointPrimaryStorage");
    }

    @Test
    void shouldSetAccountNameFromPrimaryStorage() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getAccountName())
          .isEqualTo("accountNamePrimaryStorage");
    }

    @Test
    void shouldSetAccountKeyFromPrimaryStorage() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getAccountKey())
          .isEqualTo("accountKeyPrimaryStorage");
    }

    @Test
    void shouldSetConnectionStringFromPrimaryStorage() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getConnectionString())
          .isEqualTo("connectionStringPrimaryStorage");
    }

    @Test
    void shouldSetBasePathFromPrimaryStorage() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getBasePath())
          .isEqualTo("basePathPrimaryStorage");
    }

    @Test
    void shouldSetCreateContainerFromPrimaryStorage() {
      assertThat(brokerCfg.getData().getBackup().getAzure().isCreateContainer()).isTrue();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // primary-storage
        "camunda.data.primary-storage.backup.azure.endpoint=endpointPrimaryStorage",
        "camunda.data.primary-storage.backup.azure.account-name=accountNamePrimaryStorage",
        "camunda.data.primary-storage.backup.azure.account-key=accountKeyPrimaryStorage",
        "camunda.data.primary-storage.backup.azure.connection-string=connectionStringPrimaryStorage",
        "camunda.data.primary-storage.backup.azure.base-path=basePathPrimaryStorage",
        "camunda.data.primary-storage.backup.azure.create-container=true",
        // backup (unified)
        "camunda.data.backup.azure.endpoint=endpointNew",
        "camunda.data.backup.azure.account-name=accountNameNew",
        "camunda.data.backup.azure.account-key=accountKeyNew",
        "camunda.data.backup.azure.connection-string=connectionStringNew",
        "camunda.data.backup.azure.base-path=basePathNew",
        "camunda.data.backup.azure.create-container=false",
        // legacy
        "zeebe.broker.data.backup.azure.endpoint=endpointLegacy",
        "zeebe.broker.data.backup.azure.accountName=accountNameLegacy",
        "zeebe.broker.data.backup.azure.accountKey=accountKeyLegacy",
        "zeebe.broker.data.backup.azure.connectionString=connectionStringLegacy",
        "zeebe.broker.data.backup.azure.basePath=basePathLegacy",
        "zeebe.broker.data.backup.azure.createContainer=false",
      })
  class WithAllThreeConfigsSet {
    final BrokerBasedProperties brokerCfg;

    WithAllThreeConfigsSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetEndpointFromPrimaryStorage() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getEndpoint())
          .isEqualTo("endpointPrimaryStorage");
    }

    @Test
    void shouldSetAccountNameFromPrimaryStorage() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getAccountName())
          .isEqualTo("accountNamePrimaryStorage");
    }

    @Test
    void shouldSetAccountKeyFromPrimaryStorage() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getAccountKey())
          .isEqualTo("accountKeyPrimaryStorage");
    }

    @Test
    void shouldSetConnectionStringFromPrimaryStorage() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getConnectionString())
          .isEqualTo("connectionStringPrimaryStorage");
    }

    @Test
    void shouldSetBasePathFromPrimaryStorage() {
      assertThat(brokerCfg.getData().getBackup().getAzure().getBasePath())
          .isEqualTo("basePathPrimaryStorage");
    }

    @Test
    void shouldSetCreateContainerFromPrimaryStorage() {
      assertThat(brokerCfg.getData().getBackup().getAzure().isCreateContainer()).isTrue();
    }
  }
}
