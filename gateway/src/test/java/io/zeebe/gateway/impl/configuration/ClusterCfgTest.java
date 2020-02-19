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

public class ClusterCfgTest {

  @Test
  public void shouldThrowExceptionWhenTryingToSetInvalidRequestTimeout() {
    // given
    final var sutClusterCfg = new ClusterCfg();

    // when
    final var catchedThrownBy =
        assertThatThrownBy(() -> sutClusterCfg.setRequestTimeout("invalid"));

    // then
    catchedThrownBy.isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldConvertRequestTimeoutToDuration() {
    // given
    final Duration expected = Duration.ofMinutes(13);
    final var sutClusterCfg = new ClusterCfg();
    sutClusterCfg.setRequestTimeout("13m");

    // when
    final Duration actual = sutClusterCfg.getRequestTimeout();

    // then
    assertThat(actual).isEqualTo(expected);
  }
}
