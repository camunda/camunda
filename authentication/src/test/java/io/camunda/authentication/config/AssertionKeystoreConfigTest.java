/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.*;

import io.camunda.security.configuration.AssertionKeystoreConfiguration.KidCase;
import io.camunda.security.configuration.AssertionKeystoreConfiguration.KidDigestAlgorithm;
import io.camunda.security.configuration.AssertionKeystoreConfiguration.KidEncoding;
import io.camunda.security.configuration.AssertionKeystoreConfiguration.KidSource;
import io.camunda.security.configuration.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@SuppressWarnings("SpringBootApplicationProperties")
@EnableAutoConfiguration
@SpringBootTest(
    classes = AssertionKeystoreConfigTest.TestConfig.class,
    properties = {
      "camunda.security.authentication.oidc.assertionKeystore.path=/opt/keys/thekeystore.p12",
      "camunda.security.authentication.oidc.assertionKeystore.password=password",
      "camunda.security.authentication.oidc.assertionKeystore.keyAlias=thekey",
      "camunda.security.authentication.oidc.assertionKeystore.keyPassword=keypass",
      "camunda.security.authentication.oidc.assertionKeystore.kidSource=certificate",
      "camunda.security.authentication.oidc.assertionKeystore.kidDigestAlgorithm=sha256",
      "camunda.security.authentication.oidc.assertionKeystore.kidEncoding=base64url",
      "camunda.security.authentication.oidc.assertionKeystore.kidCase=lower"
    })
public class AssertionKeystoreConfigTest {

  @Autowired private SecurityConfiguration securityConfiguration;

  @Test
  public void shouldLoadConfiguration() {
    final var keystoreConfig =
        securityConfiguration.getAuthentication().getOidc().getAssertionKeystore();
    assertThat(keystoreConfig.getPath()).isEqualTo("/opt/keys/thekeystore.p12");
    assertThat(keystoreConfig.getPassword()).isEqualTo("password");
    assertThat(keystoreConfig.getKeyAlias()).isEqualTo("thekey");
    assertThat(keystoreConfig.getKeyPassword()).isEqualTo("keypass");
    assertThat(keystoreConfig.getKidSource()).isEqualTo(KidSource.CERTIFICATE);
    assertThat(keystoreConfig.getKidDigestAlgorithm()).isEqualTo(KidDigestAlgorithm.SHA256);
    assertThat(keystoreConfig.getKidEncoding()).isEqualTo(KidEncoding.BASE64URL);
    assertThat(keystoreConfig.getKidCase()).isEqualTo(KidCase.LOWER);
  }

  @SuppressWarnings("ConfigurationProperties")
  @Configuration
  static class TestConfig {
    @Bean
    @ConfigurationProperties("camunda.security")
    public SecurityConfiguration createSecurityConfiguration() {
      return new SecurityConfiguration();
    }
  }
}
