/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.pipeline.stage;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.streaming.state.rocksdb.StoreTuning;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

final class AnalyticsPipelineConfigTest {

  @AfterEach
  void tearDown() {
    System.clearProperty("analytics.state.consistencyChecks");
    System.clearProperty("analytics.state.deleteAwareCompaction");
  }

  @Test
  void shouldDefaultToTunedStateStores() {
    // when - no analytics.state.* properties set
    final AnalyticsPipelineConfig config = AnalyticsPipelineConfig.fromSystemProperties("stage1");

    // then - analytics state is a disposable projection of the source log, so the analytics
    // default opts into the tuning (unlike the provider's own DEFAULTS, which stay stock)
    assertThat(config.storeTuning()).isEqualTo(new StoreTuning(false, true));
  }

  @Test
  void shouldHonourExplicitStateTuningProperties() {
    // given
    System.setProperty("analytics.state.consistencyChecks", "true");
    System.setProperty("analytics.state.deleteAwareCompaction", "false");

    // when
    final AnalyticsPipelineConfig config = AnalyticsPipelineConfig.fromSystemProperties("stage1");

    // then
    assertThat(config.storeTuning()).isEqualTo(new StoreTuning(true, false));
  }
}
