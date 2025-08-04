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
  GatewayBasedPropertiesOverride.class,
  UnifiedConfigurationHelper.class
})
public class ApiGrpcSslTest {
  @Nested
  @TestPropertySource(
      properties = {
        "camunda.api.grpc.ssl.enabled=true",
        "camunda.api.grpc.ssl.certificate=pathToCertificateNew",
        "camunda.api.grpc.ssl.certificate-private-key=pathToCertificatePrivateKeyNew",
      })
  class WithOnlyUnifiedConfigSet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyUnifiedConfigSet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetEnabled() {
      assertThat(gatewayCfg.getSecurity().isEnabled()).isTrue();
    }

    @Test
    void shouldSetCertificateChainPath() {
      assertThat(gatewayCfg.getSecurity().getCertificateChainPath())
          .isEqualTo(new File("pathToCertificateNew"));
    }

    @Test
    void shouldSetPrivateKeyPath() {
      assertThat(gatewayCfg.getSecurity().getPrivateKeyPath())
          .isEqualTo(new File("pathToCertificatePrivateKeyNew"));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        "zeebe.gateway.security.enabled=true",
        "zeebe.gateway.security.certificatechainpath=pathToCertificateLegacy",
        "zeebe.gateway.security.privatekeypath=pathToCertificatePrivateKeyLegacy",
      })
  class WithOnlyLegacySet {
    final GatewayBasedProperties gatewayCfg;

    WithOnlyLegacySet(@Autowired final GatewayBasedProperties gatewayCfg) {
      this.gatewayCfg = gatewayCfg;
    }

    @Test
    void shouldSetEnabled() {
      assertThat(gatewayCfg.getSecurity().isEnabled()).isTrue();
    }

    @Test
    void shouldSetCertificateChainPath() {
      assertThat(gatewayCfg.getSecurity().getCertificateChainPath())
          .isEqualTo(new File("pathToCertificateLegacy"));
    }

    @Test
    void shouldSetPrivateKeyPath() {
      assertThat(gatewayCfg.getSecurity().getPrivateKeyPath())
          .isEqualTo(new File("pathToCertificatePrivateKeyLegacy"));
    }
  }

  @Nested
  @TestPropertySource(
      properties = {
        // new
        "camunda.api.grpc.ssl.enabled=true",
        "camunda.api.grpc.ssl.certificate=pathToCertificateNew",
        "camunda.api.grpc.ssl.certificate-private-key=pathToCertificatePrivateKeyNew",
        // legacy
        "zeebe.gateway.security.enabled=true",
        "zeebe.gateway.security.certificatechainpath=pathToCertificateLegacy",
        "zeebe.gateway.security.privatekeypath=pathToCertificatePrivateKeyLegacy",
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
          .isEqualTo(new File("pathToCertificateNew"));
    }

    @Test
    void shouldSetPrivateKeyPathFromNew() {
      assertThat(gatewayCfg.getSecurity().getPrivateKeyPath())
          .isEqualTo(new File("pathToCertificatePrivateKeyNew"));
    }
  }
}
