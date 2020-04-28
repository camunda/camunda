/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.probes.health;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Test;

public class MemoryHealthIndicatorAutoConfigurationTest {

  private MemoryHealthIndicatorProperties helperProperties;

  private MemoryHealthIndicatorAutoConfiguration sutAutoConfig;

  @Before
  public void setUp() {
    sutAutoConfig = new MemoryHealthIndicatorAutoConfiguration();
    helperProperties = new MemoryHealthIndicatorProperties();
  }

  @Test
  public void shouldPassThresholdIntoConstructor() {
    // given
    helperProperties.setThreshold(0.5);

    // when
    final MemoryHealthIndicator actual = sutAutoConfig.memoryHealthIndicator(helperProperties);

    // then
    assertThat(actual).isNotNull();

    assertThat(actual.getThreshold()).isEqualTo(0.5, Offset.offset(0.001));
  }
}
