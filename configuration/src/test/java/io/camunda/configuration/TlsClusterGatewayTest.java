/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.GatewayBasedPropertiesOverride;
import io.camunda.configuration.beans.GatewayBasedProperties;
import java.io.File;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  UnifiedConfigurationHelper.class,
  GatewayBasedPropertiesOverride.class,
})
public class TlsClusterGatewayTest {

  @Nested
  @TestPropertySource(
      properties = {
        "camunda.security.transport-layer-security.cluster.enabled=true",
        "camunda.security.transport-layer-security.cluster.certificate-chain-path=certificateChainPath",
        "camunda.security.transport-layer-security.cluster.certificate-private-key-path=certificatePrivateKeyPath",
        "camunda.security.transport-layer-security.cluster.key-store.file-path=keyStoreFilePath",
        "camunda.security.transport-layer-security.cluster.key-store.password=keyStorePassword",
      })
  class WithOnlyUnifiedConfigSet {
    final GatewayBasedProperties gatewayBasedProperties;

    WithOnlyUnifiedConfigSet(@Autowired final GatewayBasedProperties gatewayBasedProperties) {
      this.gatewayBasedProperties = gatewayBasedProperties;
    }

    @Test
    void testCamundaGatewayProperties() {
      assertThat(gatewayBasedProperties.getCluster().getSecurity())
          .returns(true, io.camunda.zeebe.gateway.impl.configuration.SecurityCfg::isEnabled)
          .returns(
              new File("certificateChainPath"),
              io.camunda.zeebe.gateway.impl.configuration.SecurityCfg::getCertificateChainPath)
          .returns(
              new File("certificatePrivateKeyPath"),
              io.camunda.zeebe.gateway.impl.configuration.SecurityCfg::getPrivateKeyPath);

      assertThat(gatewayBasedProperties.getCluster().getSecurity().getKeyStore())
          .returns(
              new File("keyStoreFilePath"),
              io.camunda.zeebe.gateway.impl.configuration.KeyStoreCfg::getFilePath)
          .returns(
              "keyStorePassword",
              io.camunda.zeebe.gateway.impl.configuration.KeyStoreCfg::getPassword);
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
        "zeebe.gateway.cluster.security.enabled=false",
        "zeebe.gateway.cluster.security.certificateChainPath=certificateChainPathLegacy",
        "zeebe.gateway.cluster.security.privateKeyPath=certificatePrivateKeyPathLegacy",
        "zeebe.gateway.cluster.security.keyStore.filePath=certificateKeyStoreFilePathLegacy",
        "zeebe.gateway.cluster.security.keyStore.password=certificateKeyStorePasswordLegacy",
      })
  class WithNewAndLegacySet {
    final GatewayBasedProperties gatewayBasedProperties;

    WithNewAndLegacySet(@Autowired final GatewayBasedProperties gatewayBasedProperties) {
      this.gatewayBasedProperties = gatewayBasedProperties;
    }

    @Test
    void testCamundaGatewayProperties() {
      assertThat(gatewayBasedProperties.getCluster().getSecurity())
          .returns(true, io.camunda.zeebe.gateway.impl.configuration.SecurityCfg::isEnabled)
          .returns(
              new File("certificateChainPath"),
              io.camunda.zeebe.gateway.impl.configuration.SecurityCfg::getCertificateChainPath)
          .returns(
              new File("certificatePrivateKeyPath"),
              io.camunda.zeebe.gateway.impl.configuration.SecurityCfg::getPrivateKeyPath);

      assertThat(gatewayBasedProperties.getCluster().getSecurity().getKeyStore())
          .returns(
              new File("keyStoreFilePath"),
              io.camunda.zeebe.gateway.impl.configuration.KeyStoreCfg::getFilePath)
          .returns(
              "keyStorePassword",
              io.camunda.zeebe.gateway.impl.configuration.KeyStoreCfg::getPassword);
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // legacy
        "zeebe.gateway.cluster.security.enabled=false",
        "zeebe.gateway.cluster.security.certificateChainPath=certificateChainPathLegacy",
        "zeebe.gateway.cluster.security.privateKeyPath=certificatePrivateKeyPathLegacy",
        "zeebe.gateway.cluster.security.keyStore.filePath=certificateKeyStoreFilePathLegacy",
        "zeebe.gateway.cluster.security.keyStore.password=certificateKeyStorePasswordLegacy",
      })
  class WithOnlyLegacySet {
    final GatewayBasedProperties gatewayBasedProperties;

    WithOnlyLegacySet(@Autowired final GatewayBasedProperties gatewayBasedProperties) {
      this.gatewayBasedProperties = gatewayBasedProperties;
    }

    @Test
    void testCamundaGatewayProperties() {
      assertThat(gatewayBasedProperties.getCluster().getSecurity())
          .returns(true, io.camunda.zeebe.gateway.impl.configuration.SecurityCfg::isEnabled)
          .returns(
              new File("certificateChainPathLegacy"),
              io.camunda.zeebe.gateway.impl.configuration.SecurityCfg::getCertificateChainPath)
          .returns(
              new File("certificatePrivateKeyPathLegacy"),
              io.camunda.zeebe.gateway.impl.configuration.SecurityCfg::getPrivateKeyPath);

      assertThat(gatewayBasedProperties.getCluster().getSecurity().getKeyStore())
          .returns(
              new File("keyStoreFilePathLegacy"),
              io.camunda.zeebe.gateway.impl.configuration.KeyStoreCfg::getFilePath)
          .returns(
              "keyStorePasswordLegacy",
              io.camunda.zeebe.gateway.impl.configuration.KeyStoreCfg::getPassword);
    }
  }
}
