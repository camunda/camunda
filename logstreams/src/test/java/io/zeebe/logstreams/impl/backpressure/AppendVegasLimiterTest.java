/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.logstreams.impl.backpressure;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.concurrency.limits.limit.AbstractLimit;
import io.zeebe.util.Environment;
import java.util.Map;
import org.junit.Test;

public final class AppendVegasLimiterTest {

  @Test
  public void shouldUseDefaultValues() {
    // given - when
    final AppenderVegasCfg vegasCfg = new AppenderVegasCfg();

    // then
    assertThat(vegasCfg.getAlphaLimit()).isEqualTo(0.7);
    assertThat(vegasCfg.getBetaLimit()).isEqualTo(0.95);
    assertThat(vegasCfg.getInitialLimit()).isEqualTo(1024);
    assertThat(vegasCfg.getMaxConcurrency()).isEqualTo(1024 * 32);
  }

  @Test
  public void shouldUseDefaultValuesForNoExistingValues() {
    // given
    final Environment environment = new Environment();
    final AppenderVegasCfg vegasCfg = new AppenderVegasCfg();

    // when
    vegasCfg.applyEnvironment(environment);

    // then
    assertThat(vegasCfg.getAlphaLimit()).isEqualTo(0.7);
    assertThat(vegasCfg.getBetaLimit()).isEqualTo(0.95);
    assertThat(vegasCfg.getInitialLimit()).isEqualTo(1024);
    assertThat(vegasCfg.getMaxConcurrency()).isEqualTo(1024 * 32);
  }

  @Test
  public void shouldConfigure() {
    // given
    final Map<String, String> cfgMap =
        Map.of(
            BackpressureConstants.ENV_BP_APPENDER_VEGAS_INIT_LIMIT,
            "12",
            BackpressureConstants.ENV_BP_APPENDER_VEGAS_MAX_CONCURRENCY,
            "24",
            BackpressureConstants.ENV_BP_APPENDER_VEGAS_ALPHA_LIMIT,
            "0.1",
            BackpressureConstants.ENV_BP_APPENDER_VEGAS_BETA_LIMIT,
            "0.5");
    final Environment environment = new Environment(cfgMap);

    final AppenderVegasCfg vegasCfg = new AppenderVegasCfg();

    // when
    vegasCfg.applyEnvironment(environment);

    // then
    assertThat(vegasCfg.getAlphaLimit()).isEqualTo(0.1);
    assertThat(vegasCfg.getBetaLimit()).isEqualTo(0.5);
    assertThat(vegasCfg.getInitialLimit()).isEqualTo(12);
    assertThat(vegasCfg.getMaxConcurrency()).isEqualTo(24);
  }

  @Test
  public void shouldBuild() {
    // given
    final AppenderVegasCfg vegasCfg = new AppenderVegasCfg();

    // when
    final AbstractLimit abstractLimit = vegasCfg.get();

    // then
    assertThat(abstractLimit.getLimit()).isEqualTo(1024);
  }
}
