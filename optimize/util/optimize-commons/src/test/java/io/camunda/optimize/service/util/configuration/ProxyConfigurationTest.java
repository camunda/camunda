/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import static org.assertj.core.api.Assertions.assertThat;
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

  @Test
  public void testUsernameAndPasswordFieldsAreStoredAndRetrieved() {
    final ProxyConfiguration proxyConfiguration =
        new ProxyConfiguration(true, "proxy.example.com", 8080, false, "user", "pass");

    assertThat(proxyConfiguration.getUsername()).isEqualTo("user");
    assertThat(proxyConfiguration.getPassword()).isEqualTo("pass");
    assertThat(proxyConfiguration.getHost()).isEqualTo("proxy.example.com");
    assertThat(proxyConfiguration.getPort()).isEqualTo(8080);
    assertThat(proxyConfiguration.isEnabled()).isTrue();
    assertThat(proxyConfiguration.isSslEnabled()).isFalse();
  }

  @Test
  public void testValidatePassesWithUsernameAndPassword() {
    final ProxyConfiguration proxyConfiguration =
        new ProxyConfiguration(true, "proxy.example.com", 8080, true, "user", "pass");

    proxyConfiguration.validate();
  }

  @Test
  public void testValidatePassesWithoutUsernameAndPassword() {
    final ProxyConfiguration proxyConfiguration =
        new ProxyConfiguration(true, "proxy.example.com", 8080, false);

    proxyConfiguration.validate();
  }

  @Test
  public void testValidateWithConfigPath() {
    final ProxyConfiguration proxyConfiguration = new ProxyConfiguration(true, null, 80, false);

    assertThatExceptionOfType(OptimizeConfigurationException.class)
        .isThrownBy(() -> proxyConfiguration.validate("$.opensearch.connection.proxy"))
        .withMessageContaining("$.opensearch.connection.proxy.host");
  }

  @Test
  public void testEqualsAndHashCodeIncludeUsernameAndPassword() {
    final ProxyConfiguration config1 =
        new ProxyConfiguration(true, "host", 80, false, "user1", "pass1");
    final ProxyConfiguration config2 =
        new ProxyConfiguration(true, "host", 80, false, "user1", "pass1");
    final ProxyConfiguration config3 =
        new ProxyConfiguration(true, "host", 80, false, "user2", "pass2");

    assertThat(config1).isEqualTo(config2);
    assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    assertThat(config1).isNotEqualTo(config3);
  }

  @Test
  public void testToStringDoesNotContainPassword() {
    final ProxyConfiguration proxyConfiguration =
        new ProxyConfiguration(true, "host", 80, false, "user", "secret");

    final String toString = proxyConfiguration.toString();
    assertThat(toString).contains("username=user");
    assertThat(toString).doesNotContain("secret");
    assertThat(toString).doesNotContain("password");
  }

  @Test
  public void testSettersWork() {
    final ProxyConfiguration proxyConfiguration = new ProxyConfiguration();
    proxyConfiguration.setUsername("user");
    proxyConfiguration.setPassword("pass");

    assertThat(proxyConfiguration.getUsername()).isEqualTo("user");
    assertThat(proxyConfiguration.getPassword()).isEqualTo("pass");
  }
}
