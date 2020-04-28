/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.it.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import io.zeebe.broker.it.gateway.GatewayHealthIndicatorsIntegrationTest.Config;
import io.zeebe.gateway.impl.probes.health.MemoryHealthIndicator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = Config.class)
public class GatewayHealthIndicatorsIntegrationTest {

  @Autowired ApplicationContext applicationContext;

  @Autowired MemoryHealthIndicator memoryHealthIndicator;

  @Test
  public void shouldStart() {
    assertThat(applicationContext).isNotNull();
  }

  @Test
  public void shouldInitializeMemoryHealthIndicatorWithDefaults() {
    assertThat(memoryHealthIndicator.getThreshold()).isEqualTo(0.1, offset(0.001));
  }

  @Configuration
  @ComponentScan({"io.zeebe.gateway.impl.probes.health"})
  static class Config {}
}
