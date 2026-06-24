/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke check that CSL's {@link CamundaSecurityLibraryProperties} binds to the {@code
 * camunda.security} namespace in a minimal Spring context without a property-binding conflict.
 */
@SpringBootTest(classes = PropertyBeansCoexistenceTest.MinimalConfig.class)
class PropertyBeansCoexistenceTest {

  @Autowired private CamundaSecurityLibraryProperties cslProperties;

  @Test
  void shouldExposePropertyBeanBoundToTheCamundaSecurityNamespace() {
    assertThat(cslProperties).isNotNull();
  }

  /**
   * Minimal context that binds the CSL property bean without bringing up the security filter
   * chains.
   */
  @SpringBootConfiguration
  @EnableConfigurationProperties(CamundaSecurityLibraryProperties.class)
  static class MinimalConfig {}
}
