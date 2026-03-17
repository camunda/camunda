/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.security.CamundaSecurityConfiguration;
import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.gatekeeper.config.AuthenticationConfig;
import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
import io.camunda.gatekeeper.spring.config.GatekeeperProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@SpringBootTest(
    classes = DefaultConfigurationTest.TestConfig.class,
    properties = {"spring.config.location="})
class DefaultConfigurationTest {

  @Autowired private CamundaSecurityProperties camundaSecurityProperties;
  @Autowired private AuthenticationConfig authenticationConfig;

  @Test
  void verifySecurityConfigurations() {
    assertThat(authenticationConfig.method()).isEqualTo(AuthenticationMethod.BASIC);
    assertThat(authenticationConfig.unprotectedApi()).isFalse();

    final var authorizationsConfig = camundaSecurityProperties.getAuthorizations();
    assertThat(authorizationsConfig.isEnabled()).isTrue();

    final var multiTenancyConfig = camundaSecurityProperties.getMultiTenancy();
    assertThat(multiTenancyConfig.isChecksEnabled()).isFalse();
    assertThat(multiTenancyConfig.isApiEnabled()).isTrue();
  }

  @Configuration
  @Import({CamundaSecurityConfiguration.class})
  @EnableConfigurationProperties(GatekeeperProperties.class)
  static class TestConfig {

    @Bean
    @Primary
    AuthenticationConfig authenticationConfig(final GatekeeperProperties properties) {
      return properties.toAuthenticationConfig();
    }
  }
}
