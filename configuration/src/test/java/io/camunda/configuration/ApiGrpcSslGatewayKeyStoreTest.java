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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({
  UnifiedConfiguration.class,
  GatewayBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
public class ApiGrpcSslGatewayKeyStoreTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.api.grpc.ssl.key-store.file-path=filePathNew",
        "camunda.api.grpc.ssl.key-store.password=passwordNew",
      })
  class WithOnlyUnifiedConfigSslSet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyUnifiedConfigSslSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetFilePath() {
      assertThat(gatewayCfg.getSecurity().getKeyStore().getFilePath().getPath())
          .isEqualTo("filePathNew");
    }

    @Test
    void shouldSetPassword() {
      assertThat(gatewayCfg.getSecurity().getKeyStore().getPassword()).isEqualTo("passwordNew");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.gateway.security.keyStore.filePath=filePathLegacyBroker",
        "zeebe.broker.gateway.security.keyStore.password=passwordLegacyBroker"
      })
  class WithOnlyLegacyBrokerSecuritySet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyLegacyBrokerSecuritySet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldNotSetFilePathFromLegacyBroker() {
      assertThat(gatewayCfg.getSecurity().getKeyStore().getFilePath()).isNull();
    }

    @Test
    void shouldNotSetPasswordFromLegacyBroker() {
      assertThat(gatewayCfg.getSecurity().getKeyStore().getPassword()).isNull();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.gateway.security.keyStore.filePath=filePathLegacyGateway",
        "zeebe.gateway.security.keyStore.password=passwordLegacyGateway",
      })
  class WithOnlyLegacyGatewaySecuritySet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyLegacyGatewaySecuritySet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetFilePathFromLegacyGateway() {
      assertThat(gatewayCfg.getSecurity().getKeyStore().getFilePath().getPath())
          .isEqualTo("filePathLegacyGateway");
    }

    @Test
    void shouldSetPasswordFromLegacyGateway() {
      assertThat(gatewayCfg.getSecurity().getKeyStore().getPassword())
          .isEqualTo("passwordLegacyGateway");
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new unified configuration
        "camunda.api.grpc.ssl.key-store.file-path=filePathNew",
        "camunda.api.grpc.ssl.key-store.password=passwordNew",
        // legacy broker configuration
        "zeebe.broker.gateway.security.keyStore.filePath=filePathLegacyBroker",
        "zeebe.broker.gateway.security.keyStore.password=passwordLegacyBroker",
        // legacy gateway configuration
        "zeebe.gateway.security.keyStore.filePath=filePathLegacyBroker",
        "zeebe.gateway.security.keyStore.password=passwordLegacyBroker"
      })
  class WithNewAndLegacySet {
    final GatewayBasedProperties gatewayCfg;

    WithNewAndLegacySet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetFilePathFromNew() {
      assertThat(gatewayCfg.getSecurity().getKeyStore().getFilePath().getPath())
          .isEqualTo("filePathNew");
    }

    @Test
    void shouldSetPasswordFromNew() {
      assertThat(gatewayCfg.getSecurity().getKeyStore().getPassword()).isEqualTo("passwordNew");
    }
  }
}
