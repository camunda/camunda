/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.Test;

public class DataCfgTest {

  @Test
  public void shouldThrowExceptionWhenTryingToSetInvalidSnaphotPeriod() {
    // given
    final var sutDataCfg = new DataCfg();

    // when
    final var catchedThrownBy = assertThatThrownBy(() -> sutDataCfg.setSnapshotPeriod("invalid"));

    // then
    catchedThrownBy.isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldConvertSnapshotPeriodToDuration() {
    // given
    final Duration expected = Duration.ofMinutes(13);
    final var sutDataCfg = new DataCfg();
    sutDataCfg.setSnapshotPeriod("13m");

    // when
    final Duration actual = sutDataCfg.getSnapshotPeriodAsDuration();

    // then
    assertThat(actual).isEqualTo(expected);
  }
}
