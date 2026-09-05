/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.businessvalue;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.AutomationRateBlock;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.CycleTimeBlock;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the {@code business-value-overview} block helpers. The L1 {@code verdict}
 * function is exercised in {@link BusinessValueVerdictContractTest}; here we cover the L0 rollup
 * counterparts, which mirror the same null-input rules but drop {@code gapPct} and direction label
 * (those are derived on the read path).
 */
class BusinessValueVerdictBlockTest {

  @Test
  void cycleTimeBlockShouldReturnMetTrueWhenValueAtOrUnderTarget() {
    assertThat(BusinessValueVerdict.cycleTimeBlock(10_000_000L, 20_000_000L))
        .isEqualTo(new CycleTimeBlock(10_000_000L, 20_000_000L, true));
    assertThat(BusinessValueVerdict.cycleTimeBlock(20_000_000L, 20_000_000L))
        .isEqualTo(new CycleTimeBlock(20_000_000L, 20_000_000L, true));
  }

  @Test
  void cycleTimeBlockShouldReturnMetFalseWhenValueOverTarget() {
    assertThat(BusinessValueVerdict.cycleTimeBlock(30_000_000L, 20_000_000L))
        .isEqualTo(new CycleTimeBlock(30_000_000L, 20_000_000L, false));
  }

  @Test
  void cycleTimeBlockShouldReturnMetNullWhenValueNull() {
    assertThat(BusinessValueVerdict.cycleTimeBlock(null, 20_000_000L))
        .isEqualTo(new CycleTimeBlock(null, 20_000_000L, null));
  }

  @Test
  void cycleTimeBlockShouldReturnMetNullWhenTargetNull() {
    assertThat(BusinessValueVerdict.cycleTimeBlock(20_000_000L, null))
        .isEqualTo(new CycleTimeBlock(20_000_000L, null, null));
  }

  @Test
  void cycleTimeBlockShouldReturnMetNullWhenBothNull() {
    assertThat(BusinessValueVerdict.cycleTimeBlock(null, null))
        .isEqualTo(new CycleTimeBlock(null, null, null));
  }

  @Test
  void automationRateBlockShouldReturnMetTrueWhenValueAtOrAboveTarget() {
    assertThat(BusinessValueVerdict.automationRateBlock(90.0, 85))
        .isEqualTo(new AutomationRateBlock(90.0, 85, true));
    assertThat(BusinessValueVerdict.automationRateBlock(85.0, 85))
        .isEqualTo(new AutomationRateBlock(85.0, 85, true));
  }

  @Test
  void automationRateBlockShouldReturnMetFalseWhenValueBelowTarget() {
    assertThat(BusinessValueVerdict.automationRateBlock(70.0, 85))
        .isEqualTo(new AutomationRateBlock(70.0, 85, false));
  }

  @Test
  void automationRateBlockShouldReturnMetNullWhenValueNull() {
    assertThat(BusinessValueVerdict.automationRateBlock(null, 85))
        .isEqualTo(new AutomationRateBlock(null, 85, null));
  }

  @Test
  void automationRateBlockShouldReturnMetNullWhenTargetNull() {
    assertThat(BusinessValueVerdict.automationRateBlock(85.0, null))
        .isEqualTo(new AutomationRateBlock(85.0, null, null));
  }

  @Test
  void automationRateBlockShouldReturnMetNullWhenBothNull() {
    assertThat(BusinessValueVerdict.automationRateBlock(null, null))
        .isEqualTo(new AutomationRateBlock(null, null, null));
  }
}
