/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.Before;
import org.junit.Test;

public class ResponsiveHealthIndicatorPropertiesTest {

  private ResponsiveHealthIndicatorProperties sutProperties;

  @Before
  public void setUp() {
    sutProperties = new ResponsiveHealthIndicatorProperties();
  }

  @Test
  public void shouldHaveDefaultTimeoutOf500Millis() {
    // when
    final Duration actual = sutProperties.getHealthZeebeClientProperties().getRequestTimeout();

    // then
    assertThat(actual).isEqualTo(Duration.ofMillis(500));
  }

  @Test
  public void shouldRejectNegativeTimeout() {
    // when + then
    assertThatThrownBy(
            () ->
                sutProperties
                    .getHealthZeebeClientProperties()
                    .setRequestTimeout(Duration.ofMillis(-5)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(sutProperties.getHealthZeebeClientProperties().getRequestTimeout())
        .isEqualTo(Duration.ofMillis(500));
  }

  @Test
  public void shouldRejectZeroTimeout() {
    // when + then
    assertThatThrownBy(
            () ->
                sutProperties
                    .getHealthZeebeClientProperties()
                    .setRequestTimeout(Duration.ofMillis(0)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(sutProperties.getHealthZeebeClientProperties().getRequestTimeout())
        .isEqualTo(Duration.ofMillis(500));
  }

  @Test
  public void shouldApplyNewThreshold() {
    // when
    sutProperties.getHealthZeebeClientProperties().setRequestTimeout(Duration.ofMillis(123456));
    final Duration actual = sutProperties.getHealthZeebeClientProperties().getRequestTimeout();

    // then
    assertThat(actual).isEqualTo(Duration.ofMillis(123456));
  }

  @Test
  public void shouldSetHealthPropertiesRequestTimeoutWhenSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofSeconds(123);

    // when
    sutProperties.setRequestTimeout(requestTimeout);
    final Duration actual = sutProperties.getHealthZeebeClientProperties().getRequestTimeout();

    // then
    assertThat(actual).isEqualTo(requestTimeout);
  }
}
