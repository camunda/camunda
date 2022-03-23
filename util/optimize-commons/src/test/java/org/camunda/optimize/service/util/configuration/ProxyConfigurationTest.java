/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProxyConfigurationTest {

  @Test
  public void testValidateOkOnDefaultConfig() {
    final ProxyConfiguration proxyConfiguration = new ProxyConfiguration();

    proxyConfiguration.validate();
  }

  @Test
  public void testValidateFailOnMissingHost() {
    final ProxyConfiguration proxyConfiguration = new ProxyConfiguration(
      true, null, 80, false
    );

    assertThrows(OptimizeConfigurationException.class, () -> proxyConfiguration.validate());
  }

  @Test
  public void testValidateFailOnEmptyHost() {
    final ProxyConfiguration proxyConfiguration = new ProxyConfiguration(
      true, "", 80, false
    );

    assertThrows(OptimizeConfigurationException.class, () -> proxyConfiguration.validate());
  }

  @Test
  public void testValidateFailOnMissingPort() {
    final ProxyConfiguration proxyConfiguration = new ProxyConfiguration(
      true, "localhost", null, false
    );

    assertThrows(OptimizeConfigurationException.class, () -> proxyConfiguration.validate());
  }

}
