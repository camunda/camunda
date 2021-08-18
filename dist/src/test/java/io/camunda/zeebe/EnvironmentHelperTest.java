/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.shared.EnvironmentHelper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;

final class EnvironmentHelperTest {

  @Test
  void shouldReturnFalseWhenEnvironmentIsNull() {
    // when
    final boolean actual = EnvironmentHelper.isProductionEnvironment(null);

    // then
    assertThat(actual).isFalse();
  }

  @Test
  void shouldReturnTrueWhenEnvironmentReturnsNullForActiveProfiles() {
    // given
    final Environment mockEnvironment = Mockito.mock(Environment.class);

    // when
    final boolean actual = EnvironmentHelper.isProductionEnvironment(mockEnvironment);

    // then
    assertThat(actual).isTrue();
  }

  @Test
  void shouldReturnTrueWhenEnvironmentReturnsEmptyListForActiveProfiles() {
    // given
    final Environment mockEnvironment = Mockito.mock(Environment.class);
    when(mockEnvironment.getActiveProfiles()).thenReturn(new String[] {});

    // when
    final boolean actual = EnvironmentHelper.isProductionEnvironment(mockEnvironment);

    // then
    assertThat(actual).isTrue();
  }

  @Test
  void shouldReturnFalseWhenWhenEnvironmentReturnsListContainingDevForActiveProfiles() {
    // given
    final Environment mockEnvironment = Mockito.mock(Environment.class);
    when(mockEnvironment.getActiveProfiles()).thenReturn(new String[] {"dev"});

    // when
    final boolean actual = EnvironmentHelper.isProductionEnvironment(mockEnvironment);

    // then
    assertThat(actual).isFalse();
  }

  @Test
  void shouldReturnFalseWhenWhenEnvironmentReturnsListContainingTestForActiveProfiles() {
    // given
    final Environment mockEnvironment = Mockito.mock(Environment.class);
    when(mockEnvironment.getActiveProfiles()).thenReturn(new String[] {"test"});

    // when
    final boolean actual = EnvironmentHelper.isProductionEnvironment(mockEnvironment);

    // then
    assertThat(actual).isFalse();
  }
}
