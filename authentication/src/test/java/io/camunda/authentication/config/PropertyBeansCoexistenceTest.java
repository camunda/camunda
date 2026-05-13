/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

/**
 * Smoke check that OC's {@link SecurityConfiguration} and CSL's {@link
 * CamundaSecurityLibraryProperties} both bind to the {@code camunda.security} namespace and coexist
 * in the same Spring context without a property-binding conflict.
 */
@SpringBootTest(classes = PropertyBeansCoexistenceTest.MinimalConfig.class)
class PropertyBeansCoexistenceTest {

  @Autowired private SecurityConfiguration ocConfig;
  @Autowired private CamundaSecurityLibraryProperties cslProperties;

  @Test
  void shouldExposeBothPropertyBeansBoundToTheSameNamespace() {
    assertThat(ocConfig).isNotNull();
    assertThat(cslProperties).isNotNull();
  }

  /**
   * Minimal context that binds both property beans without bringing up the security filter chains.
   * The point of the test is to confirm OC's {@link SecurityConfiguration} and CSL's {@link
   * CamundaSecurityLibraryProperties} can coexist on the same {@code camunda.security} namespace,
   * not to exercise any filter chain wiring.
   */
  @SpringBootConfiguration
  @EnableConfigurationProperties(CamundaSecurityLibraryProperties.class)
  static class MinimalConfig {

    @SuppressWarnings("ConfigurationProperties")
    @Bean
    @ConfigurationProperties("camunda.security")
    public SecurityConfiguration createSecurityConfiguration() {
      return new SecurityConfiguration();
    }
  }
}
