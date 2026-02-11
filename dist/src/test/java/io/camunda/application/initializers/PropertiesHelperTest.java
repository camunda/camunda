/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.initializers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.ConfigurableEnvironment;

public class PropertiesHelperTest {
  @Test
  void shouldReturnSetWithTrimmedValues() {
    final ConfigurableEnvironment env = mock(ConfigurableEnvironment.class);
    when(env.getProperty("my.group", "")).thenReturn("apple, banana , cherry");

    final Set<String> result = PropertiesHelper.loadListProperty(env, "my.group");

    assertThat(result.size()).isEqualTo(3);
    assertThat(result.contains("apple")).isTrue();
    assertThat(result.contains("banana")).isTrue();
    assertThat(result.contains("cherry")).isTrue();
  }

  @Test
  void shouldIgnoreBlankAndEmptyElements() {
    final ConfigurableEnvironment env = mock(ConfigurableEnvironment.class);
    when(env.getProperty("my.group", "")).thenReturn("apple, , , banana,   ,cherry,");

    final Set<String> result = PropertiesHelper.loadListProperty(env, "my.group");

    assertThat(result.size()).isEqualTo(3);
    assertThat(result.contains("apple")).isTrue();
    assertThat(result.contains("banana")).isTrue();
    assertThat(result.contains("cherry")).isTrue();
  }

  @Test
  void shouldReturnEmptySetWhenPropertyIsEmpty() {
    final ConfigurableEnvironment env = mock(ConfigurableEnvironment.class);
    when(env.getProperty("my.group", "")).thenReturn("");

    final Set<String> result = PropertiesHelper.loadListProperty(env, "my.group");

    assertThat(result.isEmpty()).isTrue();
  }

  @Test
  void shouldHandleSingleItemWithoutCommas() {
    final ConfigurableEnvironment env = mock(ConfigurableEnvironment.class);
    when(env.getProperty("my.group", "")).thenReturn("singleItem");

    final Set<String> result = PropertiesHelper.loadListProperty(env, "my.group");

    assertThat(result.size()).isEqualTo(1);
    assertThat(result.contains("singleItem")).isTrue();
  }
}
