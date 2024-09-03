/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.client.command;

import static io.camunda.zeebe.it.util.ZeebeAssertHelper.assertClockPinned;
import static io.camunda.zeebe.protocol.record.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
@AutoCloseResources
class PinClockTest {

  private static final long FIXED_TIME = 1742461285000L;

  @AutoCloseResource ZeebeClient client;

  @TestZeebe
  private final TestStandaloneBroker zeebe = new TestStandaloneBroker().withRecordingExporter(true);

  @BeforeEach
  void initClientAndInstances() {
    client = zeebe.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
  }

  @Test
  void shouldPinClockToTimestamp() {
    // when
    client.newClockPinCommand().time(FIXED_TIME).send().join();

    // then
    assertClockPinned(c -> assertThat(c).hasTime(FIXED_TIME));
  }

  @Test
  void shouldPinClockToInstant() {
    // when
    client.newClockPinCommand().time(Instant.ofEpochMilli(FIXED_TIME)).send().join();

    // then
    assertClockPinned(c -> assertThat(c).hasTime(FIXED_TIME));
  }

  @Test
  void shouldRejectIfPinTimeIsNotProvided() {
    // when / then
    assertThatThrownBy(() -> client.newClockPinCommand().send().join())
        .hasCauseInstanceOf(ProblemException.class)
        .extracting(e -> (ProblemException) e.getCause())
        .satisfies(
            e -> {
              assertThat(e.getMessage()).startsWith("Failed with code 400: 'Bad Request'");
              assertThat(e.details().getTitle()).isEqualTo("INVALID_ARGUMENT");
              assertThat(e.details().getDetail()).isEqualTo("No timestamp provided.");
            });
  }

  @Test
  void shouldRaiseIllegalArgumentExceptionWhenNullInstantProvided() {
    // when / then
    assertThatThrownBy(() -> client.newClockPinCommand().time(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("instant must not be null");
  }
}
