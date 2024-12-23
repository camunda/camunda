/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.spring.client.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
    classes = ZeebeClientSpringConfigurationDefaultPropertiesTest.TestConfig.class)
public class ZeebeClientSpringConfigurationDefaultPropertiesTest {

  @Autowired private CamundaClientConfigurationProperties properties;

  @Test
  public void hasRequestTimeout() {
    assertThat(properties.getRequestTimeout()).isEqualTo(Duration.ofSeconds(10));
  }

  @Test
  public void hasNoWorkerName() {
    assertThat(properties.getDefaultJobWorkerName()).isNull();
  }

  @Test
  public void hasJobTimeout() {
    assertThat(properties.getDefaultJobTimeout()).isEqualTo(Duration.ofSeconds(300));
  }

  @Test
  public void hasWorkerMaxJobsActive() {
    assertThat(properties.getDefaultJobWorkerMaxJobsActive()).isEqualTo(32);
  }

  @Test
  public void hasJobPollInterval() {
    assertThat(properties.getDefaultJobPollInterval()).isEqualTo(Duration.ofNanos(100000000));
  }

  @Test
  public void hasWorkerThreads() {
    assertThat(properties.getNumJobWorkerExecutionThreads()).isEqualTo(1);
  }

  @Test
  public void hasMessageTimeToLeave() {
    assertThat(properties.getDefaultMessageTimeToLive()).isEqualTo(Duration.ofSeconds(3600));
  }

  @Test
  public void hasSecurityCertificatePath() {
    assertThat(properties.getCaCertificatePath()).isNull();
  }

  @EnableConfigurationProperties(CamundaClientConfigurationProperties.class)
  public static class TestConfig {}
}
