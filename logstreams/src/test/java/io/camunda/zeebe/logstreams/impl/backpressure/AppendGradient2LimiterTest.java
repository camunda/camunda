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

public final class AppendGradient2LimiterTest {

  @Test
  public void shouldUseDefaultValues() {
    // given - when
    final AppenderGradient2Cfg gradient2Cfg = new AppenderGradient2Cfg();

    // then
    assertThat(gradient2Cfg.getInitialLimit()).isEqualTo(1024);
    assertThat(gradient2Cfg.getMaxConcurrency()).isEqualTo(1024 * 32);
    assertThat(gradient2Cfg.getLongWindow()).isEqualTo(1200);
    assertThat(gradient2Cfg.getMinLimit()).isEqualTo(256);
    assertThat(gradient2Cfg.getQueueSize()).isEqualTo(32);
    assertThat(gradient2Cfg.getRttTolerance()).isEqualTo(1.5);
  }

  @Test
  public void shouldUseDefaultValuesForNoExistingValues() {
    // given
    final Environment environment = new Environment();
    final AppenderGradient2Cfg gradient2Cfg = new AppenderGradient2Cfg();

    // when
    gradient2Cfg.applyEnvironment(environment);

    // then
    assertThat(gradient2Cfg.getInitialLimit()).isEqualTo(1024);
    assertThat(gradient2Cfg.getMaxConcurrency()).isEqualTo(1024 * 32);
    assertThat(gradient2Cfg.getLongWindow()).isEqualTo(1200);
    assertThat(gradient2Cfg.getMinLimit()).isEqualTo(256);
    assertThat(gradient2Cfg.getQueueSize()).isEqualTo(32);
    assertThat(gradient2Cfg.getRttTolerance()).isEqualTo(1.5);
  }

  @Test
  public void shouldConfigure() {
    // given
    final Map<String, String> cfgMap =
        Map.of(
            BackpressureConstants.ENV_BP_APPENDER_GRADIENT2_INIT_LIMIT,
            "12",
            BackpressureConstants.ENV_BP_APPENDER_GRADIENT2_MAX_CONCURRENCY,
            "24",
            BackpressureConstants.ENV_BP_APPENDER_GRADIENT2_LONG_WINDOW,
            "300",
            BackpressureConstants.ENV_BP_APPENDER_GRADIENT2_QUEUE_SIZE,
            "3",
            BackpressureConstants.ENV_BP_APPENDER_GRADIENT2_RTT_TOLERANCE,
            "0.3",
            BackpressureConstants.ENV_BP_APPENDER_GRADIENT2_MIN_LIMIT,
            "1");
    final Environment environment = new Environment(cfgMap);
    final AppenderGradient2Cfg gradient2Cfg = new AppenderGradient2Cfg();

    // when
    gradient2Cfg.applyEnvironment(environment);

    // then
    assertThat(gradient2Cfg.getInitialLimit()).isEqualTo(12);
    assertThat(gradient2Cfg.getMaxConcurrency()).isEqualTo(24);
    assertThat(gradient2Cfg.getLongWindow()).isEqualTo(300);
    assertThat(gradient2Cfg.getMinLimit()).isEqualTo(1);
    assertThat(gradient2Cfg.getQueueSize()).isEqualTo(3);
    assertThat(gradient2Cfg.getRttTolerance()).isEqualTo(0.3);
  }

  @Test
  public void shouldBuild() {
    // given
    final AppenderGradient2Cfg gradient2Cfg = new AppenderGradient2Cfg();

    // when
    final AbstractLimit abstractLimit = gradient2Cfg.get();

    // then
    assertThat(abstractLimit.getLimit()).isEqualTo(1024);
  }
}
