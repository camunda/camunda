/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.config;

import static org.assertj.core.api.Assertions.*;

import io.camunda.spring.client.configuration.PropertyUtil;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

public class PropertyUtilTest {
  @Test
  void shouldNotPreferLegacy() {
    final String property =
        PropertyUtil.getProperty("Test", new HashMap<>(), "default", () -> "prop", () -> "legacy");
    assertThat(property).isEqualTo("prop");
  }

  @Test
  void shouldApplyDefault() {
    final String property =
        PropertyUtil.getProperty("Test", new HashMap<>(), "default", () -> null, () -> null);
    assertThat(property).isEqualTo("default");
  }

  @Test
  void shouldIgnoreDefaultOnLegacy() {
    final String property =
        PropertyUtil.getProperty("Test", new HashMap<>(), "default", () -> "prop", () -> "default");
    assertThat(property).isEqualTo("prop");
  }

  @Test
  void shouldHandleExceptionOnPropertySupplier() {
    final String property =
        PropertyUtil.getProperty(
            "Test",
            new HashMap<>(),
            "default",
            () -> {
              throw new NullPointerException();
            },
            () -> null);
    assertThat(property).isEqualTo("default");
  }

  @Test
  void shouldHandleExceptionOnLegacyPropertySupplier() {
    final String property =
        PropertyUtil.getProperty(
            "Test",
            new HashMap<>(),
            "default",
            () -> null,
            () -> {
              throw new NullPointerException();
            });
    assertThat(property).isEqualTo("default");
  }
}
