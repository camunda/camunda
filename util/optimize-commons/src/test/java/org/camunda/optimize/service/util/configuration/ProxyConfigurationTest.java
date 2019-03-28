package org.camunda.optimize.service.util.configuration;

import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.junit.Test;

public class ProxyConfigurationTest {

  @Test
  public void testValidateOkOnDefaultConfig() {
    final ProxyConfiguration proxyConfiguration = new ProxyConfiguration();

    proxyConfiguration.validate();
  }

  @Test(expected = OptimizeConfigurationException.class)
  public void testValidateFailOnMissingHost() {
    final ProxyConfiguration proxyConfiguration = new ProxyConfiguration(
      true, null, 80, false
    );

    proxyConfiguration.validate();
  }

  @Test(expected = OptimizeConfigurationException.class)
  public void testValidateFailOnEmptyHost() {
    final ProxyConfiguration proxyConfiguration = new ProxyConfiguration(
      true, "", 80, false
    );

    proxyConfiguration.validate();
  }

  @Test(expected = OptimizeConfigurationException.class)
  public void testValidateFailOnMissingPort() {
    final ProxyConfiguration proxyConfiguration = new ProxyConfiguration(
      true, "localhost", null, false
    );

    proxyConfiguration.validate();
  }

}
