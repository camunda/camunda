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
public class ApiGrpcSslBrokerKeyStoreTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.api.grpc.ssl.key-store.file-path=filePathNew",
        "camunda.api.grpc.ssl.key-store.password=passwordNew",
      })
  class WithOnlyUnifiedConfigSslSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSslSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetFilePath() {
      assertThat(brokerCfg.getGateway().getSecurity().getKeyStore().getFilePath().getPath())
          .isEqualTo("filePathNew");
    }

    @Test
    void shouldSetPassword() {
      assertThat(brokerCfg.getGateway().getSecurity().getKeyStore().getPassword())
          .isEqualTo("passwordNew");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.gateway.security.keyStore.filePath=filePathLegacyGateway",
        "zeebe.gateway.security.keyStore.password=passwordLegacyGateway"
      })
  class WithOnlyLegacyBrokerSecuritySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacyBrokerSecuritySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldNotSetFilePathFromLegacyGateway() {
      assertThat(brokerCfg.getGateway().getSecurity().getKeyStore().getFilePath()).isNull();
    }

    @Test
    void shouldNotSetPasswordFromLegacyGateway() {
      assertThat(brokerCfg.getGateway().getSecurity().getKeyStore().getPassword()).isNull();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.gateway.security.keyStore.filePath=filePathLegacyBroker",
        "zeebe.broker.gateway.security.keyStore.password=passwordLegacyBroker",
      })
  class WithOnlyLegacyGatewaySecuritySet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacyGatewaySecuritySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetFilePathFromLegacyBroker() {
      assertThat(brokerCfg.getGateway().getSecurity().getKeyStore().getFilePath().getPath())
          .isEqualTo("filePathLegacyBroker");
    }

    @Test
    void shouldSetPasswordFromLegacyBroker() {
      assertThat(brokerCfg.getGateway().getSecurity().getKeyStore().getPassword())
          .isEqualTo("passwordLegacyBroker");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new unified configuration
        "camunda.api.grpc.ssl.key-store.file-path=filePathNew",
        "camunda.api.grpc.ssl.key-store.password=passwordNew",
        // legacy gateway configuration
        "zeebe.gateway.security.keyStore.filePath=filePathLegacyGateway",
        "zeebe.gateway.security.keyStore.password=passwordLegacyGateway",
        // legacy broker configuration
        "zeebe.broker.gateway.security.keyStore.filePath=filePathLegacyBroker",
        "zeebe.broker.gateway.security.keyStore.password=passwordLegacyBroker",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetFilePathFromNew() {
      assertThat(brokerCfg.getGateway().getSecurity().getKeyStore().getFilePath().getPath())
          .isEqualTo("filePathNew");
    }

    @Test
    void shouldSetPasswordFromNew() {
      assertThat(brokerCfg.getGateway().getSecurity().getKeyStore().getPassword())
          .isEqualTo("passwordNew");
    }
  }
}
