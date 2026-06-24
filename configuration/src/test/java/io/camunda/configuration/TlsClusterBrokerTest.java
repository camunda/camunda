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
import io.camunda.zeebe.broker.system.configuration.KeyStoreCfg;
import io.camunda.zeebe.broker.system.configuration.SecurityCfg;
import java.io.File;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  UnifiedConfigurationHelper.class,
  BrokerBasedPropertiesOverride.class
})
public class TlsClusterBrokerTest {

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.security.transport-layer-security.cluster.enabled=true",
        "camunda.security.transport-layer-security.cluster.certificate-chain-path=certificateChainPath",
        "camunda.security.transport-layer-security.cluster.certificate-private-key-path=certificatePrivateKeyPath",
        "camunda.security.transport-layer-security.cluster.key-store.file-path=keyStoreFilePath",
        "camunda.security.transport-layer-security.cluster.key-store.password=keyStorePassword",
      })
  @ActiveProfiles({"broker"})
  class WithOnlyUnifiedConfigSet {
    final BrokerBasedProperties brokerBasedProperties;

    WithOnlyUnifiedConfigSet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void testCamundaBrokerProperties() {
      assertThat(brokerBasedProperties.getNetwork().getSecurity())
          .returns(true, SecurityCfg::isEnabled)
          .returns(new File("certificateChainPath"), SecurityCfg::getCertificateChainPath)
          .returns(new File("certificatePrivateKeyPath"), SecurityCfg::getPrivateKeyPath);

      assertThat(brokerBasedProperties.getNetwork().getSecurity().getKeyStore())
          .returns(new File("keyStoreFilePath"), KeyStoreCfg::getFilePath)
          .returns("keyStorePassword", KeyStoreCfg::getPassword);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.security.transport-layer-security.cluster.enabled=true",
        "camunda.security.transport-layer-security.cluster.certificate-chain-path=certificateChainPath",
        "camunda.security.transport-layer-security.cluster.certificate-private-key-path=certificatePrivateKeyPath",
        "camunda.security.transport-layer-security.cluster.key-store.file-path=keyStoreFilePath",
        "camunda.security.transport-layer-security.cluster.key-store.password=keyStorePassword",
        // legacy
        "zeebe.broker.network.security.enabled=false",
        "zeebe.broker.network.security.certificateChainPath=certificateChainPathLegacy",
        "zeebe.broker.network.security.privateKeyPath=certificatePrivateKeyPathLegacy",
        "zeebe.broker.network.security.keyStore.filePath=certificateKeyStoreFilePathLegacy",
        "zeebe.broker.network.security.keyStore.password=certificateKeyStorePasswordLegacy",
      })
  @ActiveProfiles({"broker"})
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerBasedProperties;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void testCamundaBrokerProperties() {
      assertThat(brokerBasedProperties.getNetwork().getSecurity())
          .returns(true, SecurityCfg::isEnabled)
          .returns(new File("certificateChainPath"), SecurityCfg::getCertificateChainPath)
          .returns(new File("certificatePrivateKeyPath"), SecurityCfg::getPrivateKeyPath);

      assertThat(brokerBasedProperties.getNetwork().getSecurity().getKeyStore())
          .returns(new File("keyStoreFilePath"), KeyStoreCfg::getFilePath)
          .returns("keyStorePassword", KeyStoreCfg::getPassword);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // legacy
        "zeebe.broker.network.security.enabled=true",
        "zeebe.broker.network.security.certificateChainPath=certificateChainPathLegacy",
        "zeebe.broker.network.security.privateKeyPath=certificatePrivateKeyPathLegacy",
        "zeebe.broker.network.security.keyStore.filePath=keyStoreFilePathLegacy",
        "zeebe.broker.network.security.keyStore.password=keyStorePasswordLegacy",
      })
  @ActiveProfiles({"broker"})
  class WithOnlyLegacySet {
    final BrokerBasedProperties brokerBasedProperties;

    WithOnlyLegacySet(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      this.brokerBasedProperties = brokerBasedProperties;
    }

    @Test
    void testCamundaBrokerProperties() {
      assertThat(brokerBasedProperties.getNetwork().getSecurity())
          .returns(true, SecurityCfg::isEnabled)
          .returns(new File("certificateChainPathLegacy"), SecurityCfg::getCertificateChainPath)
          .returns(new File("certificatePrivateKeyPathLegacy"), SecurityCfg::getPrivateKeyPath);

      assertThat(brokerBasedProperties.getNetwork().getSecurity().getKeyStore())
          .returns(new File("keyStoreFilePathLegacy"), KeyStoreCfg::getFilePath)
          .returns("keyStorePasswordLegacy", KeyStoreCfg::getPassword);
    }
  }
}
