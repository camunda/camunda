/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.spring.actuator.CamundaClientHealthIndicator;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(
    classes = CompatibilityHealthIndicatorIT.TestApplication.class,
    properties = {"spring.main.web-application-type=none"})
@CamundaSpringProcessTest
class CompatibilityHealthIndicatorIT {

  @Autowired private CamundaClientHealthIndicator healthIndicator;

  @Test
  void shouldReportClientHealthUp() {
    // given

    // when
    final var health = healthIndicator.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import(CompatibilityTestSupportConfiguration.class)
  static class TestApplication {}
}
