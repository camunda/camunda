/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.junit.jupiter.api.Test;

public class ProxyConfigurationTest {

  @Test
  public void testValidateOkOnDefaultConfig() {
    final ProxyConfiguration proxyConfiguration = new ProxyConfiguration();

    proxyConfiguration.validate();
  }

  @Test
  public void testValidateFailOnMissingHost() {
    final ProxyConfiguration proxyConfiguration = new ProxyConfiguration(true, null, 80, false);

    assertThatExceptionOfType(OptimizeConfigurationException.class)
        .isThrownBy(() -> proxyConfiguration.validate());
  }

  @Test
  public void testValidateFailOnEmptyHost() {
    final ProxyConfiguration proxyConfiguration = new ProxyConfiguration(true, "", 80, false);

    assertThatExceptionOfType(OptimizeConfigurationException.class)
        .isThrownBy(() -> proxyConfiguration.validate());
  }

  @Test
  public void testValidateFailOnMissingPort() {
    final ProxyConfiguration proxyConfiguration =
        new ProxyConfiguration(true, "localhost", null, false);

    assertThatExceptionOfType(OptimizeConfigurationException.class)
        .isThrownBy(() -> proxyConfiguration.validate());
  }
}
