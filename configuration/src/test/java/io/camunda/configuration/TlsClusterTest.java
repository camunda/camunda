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
import io.camunda.configuration.beanoverrides.GatewayBasedPropertiesOverride;
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
  BrokerBasedPropertiesOverride.class,
  GatewayBasedPropertiesOverride.class,
})
public class TlsClusterTest {

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
  class WithOnlyUnifiedConfigSetBroker {
    final BrokerBasedProperties brokerBasedProperties;

    WithOnlyUnifiedConfigSetBroker(@Autowired final BrokerBasedProperties brokerBasedProperties) {
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

  // TODO KPO remove
  //  @Nested
  //  @TestPropertySource(
  //      properties = {
  //        "camunda.security.transport-layer-security.cluster.enabled=true",
  //
  // "camunda.security.transport-layer-security.cluster.certificate-chain-path=certificateChainPath",
  //
  // "camunda.security.transport-layer-security.cluster.certificate-private-key-path=certificatePrivateKeyPath",
  //
  // "camunda.security.transport-layer-security.cluster.key-store.file-path=keyStoreFilePath",
  //        "camunda.security.transport-layer-security.cluster.key-store.password=keyStorePassword",
  //      })
  //  class WithOnlyUnifiedConfigSetGateway {
  //    final GatewayBasedProperties gatewayBasedProperties;
  //
  //    WithOnlyUnifiedConfigSetGateway(
  //        @Autowired final GatewayBasedProperties gatewayBasedProperties) {
  //      this.gatewayBasedProperties = gatewayBasedProperties;
  //    }
  //
  //    @Test
  //    void testCamundaGatewayProperties() {
  //      assertThat(gatewayBasedProperties.getSecurity())
  //          .returns(true, io.camunda.zeebe.gateway.impl.configuration.SecurityCfg::isEnabled)
  //          .returns(
  //              new File("certificateChainPath"),
  //              io.camunda.zeebe.gateway.impl.configuration.SecurityCfg::getCertificateChainPath)
  //          .returns(
  //              new File("certificatePrivateKeyPath"),
  //              io.camunda.zeebe.gateway.impl.configuration.SecurityCfg::getPrivateKeyPath);
  //
  //      assertThat(gatewayBasedProperties.getSecurity().getKeyStore())
  //          .returns(
  //              new File("keyStoreFilePath"),
  //              io.camunda.zeebe.gateway.impl.configuration.KeyStoreCfg::getFilePath)
  //          .returns(
  //              "keyStorePassword",
  //              io.camunda.zeebe.gateway.impl.configuration.KeyStoreCfg::getPassword);
  //    }
  //  }
}
