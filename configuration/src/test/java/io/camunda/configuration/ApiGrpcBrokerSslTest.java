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

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import java.io.File;
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
public class ApiGrpcBrokerSslTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.api.grpc.ssl.enabled=true",
        "camunda.api.grpc.ssl.certificate=certificateNew",
        "camunda.api.grpc.ssl.certificate-private-key=certificatePrivateKeyNew",
      })
  class WithOnlyUnifiedConfigSslSet {
    final BrokerBasedProperties brokerCfg;

    WithOnlyUnifiedConfigSslSet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetEnabled() {
      assertThat(brokerCfg.getGateway().getSecurity().isEnabled()).isTrue();
    }

    @Test
    void shouldSetCertificateChainPath() {
      assertThat(brokerCfg.getGateway().getSecurity().getCertificateChainPath())
          .isEqualTo(new File("certificateNew"));
    }

    @Test
    void shouldSetPrivateKeyPath() {
      assertThat(brokerCfg.getGateway().getSecurity().getPrivateKeyPath())
          .isEqualTo(new File("certificatePrivateKeyNew"));
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
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacyGatewaySecuritySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldNotSetEnabledFromLegacyGateway() {
      assertThat(brokerCfg.getGateway().getSecurity().isEnabled()).isEqualTo(DEFAULT_TLS_ENABLED);
    }

    @Test
    void shouldNotSetCertificateChainPathFromLegacyGateway() {
      assertThat(brokerCfg.getGateway().getSecurity().getCertificateChainPath()).isNull();
    }

    @Test
    void shouldNotSetPrivateKeyPathFromLegacyGateway() {
      assertThat(brokerCfg.getGateway().getSecurity().getPrivateKeyPath()).isNull();
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
    final BrokerBasedProperties brokerCfg;

    WithOnlyLegacyBrokerSecuritySet(@Autowired final BrokerBasedProperties brokerCfg) {
      this.brokerCfg = brokerCfg;
    }

    @Test
    void shouldSetEnabledFromLegacyBroker() {
      assertThat(brokerCfg.getGateway().getSecurity().isEnabled()).isTrue();
    }

    @Test
    void shouldSetCertificateChainPathFromLegacyBroker() {
      assertThat(brokerCfg.getGateway().getSecurity().getCertificateChainPath())
          .isEqualTo(new File("certificateChainPathLegacyBroker"));
    }

    @Test
    void shouldSetPrivateKeyPathFromLegacyBroker() {
      assertThat(brokerCfg.getGateway().getSecurity().getPrivateKeyPath())
          .isEqualTo(new File("privateKeyPathLegacyBroker"));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new unified configuration ssl
        "camunda.api.grpc.ssl.enabled=true",
        "camunda.api.grpc.ssl.certificate=certificateNew",
        "camunda.api.grpc.ssl.certificate-private-key=certificatePrivateKeyNew",
        // legacy gateway configuration security
        "zeebe.gateway.security.enabled=true",
        "zeebe.gateway.security.certificateChainPath=pathToCertificateLegacyGateway",
        "zeebe.gateway.security.privateKeyPath=pathToCertificatePrivateKeyLegacyGateway",
        // legacy broker configuration security
        "zeebe.broker.gateway.security.enabled=true",
        "zeebe.broker.gateway.security.certificateChainPath=certificateChainPathLegacyBroker",
        "zeebe.broker.gateway.security.privateKeyPath=privateKeyPathLegacyBroker",
      })
  class WithNewAndLegacySet {
    final BrokerBasedProperties brokerCfg;

    WithNewAndLegacySet(@Autowired final BrokerBasedProperties gatewayCfg) {
      brokerCfg = gatewayCfg;
    }

    @Test
    void shouldSetEnabledFromNew() {
      assertThat(brokerCfg.getGateway().getSecurity().isEnabled()).isTrue();
    }

    @Test
    void shouldSetCertificateChainPathFromNew() {
      assertThat(brokerCfg.getGateway().getSecurity().getCertificateChainPath())
          .isEqualTo(new File("certificateNew"));
    }

    @Test
    void shouldSetPrivateKeyPathFromNew() {
      assertThat(brokerCfg.getGateway().getSecurity().getPrivateKeyPath())
          .isEqualTo(new File("certificatePrivateKeyNew"));
    }
  }
}
