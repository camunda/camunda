/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.Test;

public class NetworkCfgTest {

  @Test
  public void shouldThrowExceptionWhenTryingToSetInvalidMinKeepAliveInterval() {
    // given
    final var sutNetworkCfg = new NetworkCfg();

    // when
    final var catchedThrownBy =
        assertThatThrownBy(() -> sutNetworkCfg.setMinKeepAliveInterval("invalid"));

    // then
    catchedThrownBy.isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldConvertMinKeepAliveInterval() {
    // given
    final Duration expected = Duration.ofMinutes(13);
    final var sutNetworkCfg = new NetworkCfg();
    sutNetworkCfg.setMinKeepAliveInterval("13m");

    // when
    final Duration actual = sutNetworkCfg.getMinKeepAliveIntervalAsDuration();

    // then
    assertThat(actual).isEqualTo(expected);
  }
}
