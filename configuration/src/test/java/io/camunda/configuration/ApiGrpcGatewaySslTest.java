/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static io.camunda.zeebe.gateway.impl.configuration.ConfigurationDefaults.DEFAULT_TLS_ENABLED;
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
  GatewayBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
public class ApiGrpcGatewaySslTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.api.grpc.ssl.enabled=true",
        "camunda.api.grpc.ssl.certificate=certificateNew",
        "camunda.api.grpc.ssl.certificate-private-key=certificatePrivateKeyNew",
      })
  class WithOnlyUnifiedConfigSslSet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyUnifiedConfigSslSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetEnabled() {
      assertThat(gatewayCfg.getSecurity().isEnabled()).isTrue();
    }

    @Test
    void shouldSetCertificateChainPath() {
      assertThat(gatewayCfg.getSecurity().getCertificateChainPath())
          .isEqualTo(new File("certificateNew"));
    }

    @Test
    void shouldSetPrivateKeyPath() {
      assertThat(gatewayCfg.getSecurity().getPrivateKeyPath())
          .isEqualTo(new File("certificatePrivateKeyNew"));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.broker.gateway.security.enabled=true",
        "zeebe.broker.gateway.security.certificateChainPath=certificateChainPathLegacyBroker",
        "zeebe.broker.gateway.security.privateKeyPath=privateKeyPathLegacyBroker",
      })
  class WithOnlyLegacyBrokerSecuritySet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyLegacyBrokerSecuritySet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldNotSetEnabledFromLegacyBroker() {
      assertThat(gatewayCfg.getSecurity().isEnabled()).isEqualTo(DEFAULT_TLS_ENABLED);
    }

    @Test
    void shouldNotSetCertificateChainPathFromLegacyBroker() {
      assertThat(gatewayCfg.getSecurity().getCertificateChainPath()).isNull();
    }

    @Test
    void shouldNotSetPrivateKeyPathFromLegacyBroker() {
      assertThat(gatewayCfg.getSecurity().getPrivateKeyPath()).isNull();
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.gateway.security.enabled=true",
        "zeebe.gateway.security.certificateChainPath=pathToCertificateLegacyGateway",
        "zeebe.gateway.security.privateKeyPath=pathToCertificatePrivateKeyLegacyGateway",
      })
  class WithOnlyLegacyGatewaySecuritySet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyLegacyGatewaySecuritySet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetEnabledFromLegacyGateway() {
      assertThat(gatewayCfg.getSecurity().isEnabled()).isTrue();
    }

    @Test
    void shouldSetCertificateChainPathFromLegacyGateway() {
      assertThat(gatewayCfg.getSecurity().getCertificateChainPath())
          .isEqualTo(new File("pathToCertificateLegacyGateway"));
    }

    @Test
    void shouldSetPrivateKeyPathFromLegacyGateway() {
      assertThat(gatewayCfg.getSecurity().getPrivateKeyPath())
          .isEqualTo(new File("pathToCertificatePrivateKeyLegacyGateway"));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new unified configuration ssl
        "camunda.api.grpc.ssl.enabled=true",
        "camunda.api.grpc.ssl.certificate=certificateNew",
        "camunda.api.grpc.ssl.certificate-private-key=certificatePrivateKeyNew",
        // legacy broker configuration security
        "zeebe.broker.gateway.security.enabled=true",
        "zeebe.broker.gateway.security.certificateChainPath=certificateChainPathLegacyBroker",
        "zeebe.broker.gateway.security.privateKeyPath=privateKeyPathLegacyBroker",
        // legacy gateway configuration security
        "zeebe.gateway.security.enabled=true",
        "zeebe.gateway.security.certificateChainPath=pathToCertificateLegacyGateway",
        "zeebe.gateway.security.privateKeyPath=pathToCertificatePrivateKeyLegacyGateway",
      })
  class WithNewAndLegacySet {
    final GatewayBasedProperties gatewayCfg;

    WithNewAndLegacySet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetEnabledFromNew() {
      assertThat(gatewayCfg.getSecurity().isEnabled()).isTrue();
    }

    @Test
    void shouldSetCertificateChainPathFromNew() {
      assertThat(gatewayCfg.getSecurity().getCertificateChainPath())
          .isEqualTo(new File("certificateNew"));
    }

    @Test
    void shouldSetPrivateKeyPathFromNew() {
      assertThat(gatewayCfg.getSecurity().getPrivateKeyPath())
          .isEqualTo(new File("certificatePrivateKeyNew"));
    }
  }
}
