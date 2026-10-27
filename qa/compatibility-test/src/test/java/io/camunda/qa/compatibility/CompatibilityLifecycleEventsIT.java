/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.qa.compatibility;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.spring.event.CamundaClientCreatedSpringEvent;
import io.camunda.process.test.api.CamundaSpringProcessTest;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@SpringBootTest(
    classes = CompatibilityLifecycleEventsIT.TestApplication.class,
    properties = {"spring.main.web-application-type=none"})
@CamundaSpringProcessTest
class CompatibilityLifecycleEventsIT {

  @Autowired private LifecycleTracker lifecycleTracker;

  @Test
  void shouldPublishCamundaClientCreatedEvent() {
    // given

    // when

    // then
    assertThat(lifecycleTracker.isCreatedEventReceived()).isTrue();
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  @Import({LifecycleTracker.class, CompatibilityTestSupportConfiguration.class})
  static class TestApplication {}

  @Component
  public static class LifecycleTracker {

    private final AtomicBoolean createdEventReceived = new AtomicBoolean(false);

    @EventListener
    public void onCamundaClientCreated(final CamundaClientCreatedSpringEvent event) {
      createdEventReceived.set(true);
    }

    boolean isCreatedEventReceived() {
      return createdEventReceived.get();
    }
  }
}
