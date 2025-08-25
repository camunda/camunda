/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = MutualTlsPropertiesTest.TestConfig.class)
@TestPropertySource(
    properties = {
      "camunda.security.authentication.mtls.enabled=true",
      "camunda.security.authentication.mtls.default-roles=ROLE_USER,ROLE_ADMIN",
      "camunda.security.authentication.mtls.trusted-certificates=/path/to/ca.pem",
      "camunda.security.authentication.mtls.allow-self-signed=false",
      "camunda.security.authentication.mtls.require-valid-chain=true"
    })
class MutualTlsPropertiesTest {

  @Autowired private MutualTlsProperties properties;

  @Test
  void shouldLoadConfigurationFromProperties() {
    assertThat(properties.isEnabled()).isTrue();
    assertThat(properties.getDefaultRoles()).containsExactly("ROLE_USER", "ROLE_ADMIN");
    assertThat(properties.getTrustedCertificates()).containsExactly("/path/to/ca.pem");
    assertThat(properties.isAllowSelfSigned()).isFalse();
    assertThat(properties.isRequireValidChain()).isTrue();
  }

  @Test
  void shouldHaveDefaultValues() {
    final var defaultProperties = new MutualTlsProperties();
    assertThat(defaultProperties.getDefaultRoles()).isEqualTo(List.of("ROLE_USER"));
    assertThat(defaultProperties.isAllowSelfSigned()).isTrue();
    assertThat(defaultProperties.isRequireValidChain()).isTrue();
    assertThat(defaultProperties.isCheckRevocation()).isFalse();
  }

  @EnableConfigurationProperties(MutualTlsProperties.class)
  static class TestConfig {}
}
