/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.StandardEnvironment;

class SpringPropertiesPostProcessorTest {

  private static final SpringApplication OPTIMIZE_APPLICATION = new SpringApplication(Main.class);

  private final SpringPropertiesPostProcessor processor = new SpringPropertiesPostProcessor();

  @Test
  void shouldBridgeServerHttp2EnabledForOptimizesOwnApplication() {
    final StandardEnvironment env = new StandardEnvironment();

    processor.postProcessEnvironment(env, OPTIMIZE_APPLICATION);

    assertThat(env.getProperty(SpringPropertiesPostProcessor.SPRING_HTTP2_ENABLED_PROPERTY))
        .isNotNull();
  }

  @Test
  void shouldSkipEntirelyWhenApplicationIsNotOptimizesOwn() {
    // given a different SpringApplication booted in the same JVM/classpath (e.g. the embedded
    // TestStandaloneBroker Optimize's own CCSM ITs start), which never asked for Optimize's
    // server.http2.enabled default (camunda/camunda#60184)
    final StandardEnvironment env = new StandardEnvironment();
    final SpringApplication notOptimize = new SpringApplication(StandardEnvironment.class);

    processor.postProcessEnvironment(env, notOptimize);

    assertThat(env.getProperty(SpringPropertiesPostProcessor.SPRING_HTTP2_ENABLED_PROPERTY))
        .isNull();
  }

  @Test
  void shouldSkipEntirelyWhenApplicationIsNull() {
    final StandardEnvironment env = new StandardEnvironment();

    processor.postProcessEnvironment(env, null);

    assertThat(env.getProperty(SpringPropertiesPostProcessor.SPRING_HTTP2_ENABLED_PROPERTY))
        .isNull();
  }
}
