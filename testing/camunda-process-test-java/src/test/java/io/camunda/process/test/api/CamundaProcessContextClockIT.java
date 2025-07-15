/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.client.CamundaClient;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@CamundaProcessTest
public class CamundaProcessContextClockIT {

  private static final Duration TIMER_DURATION = Duration.ofHours(1);

  private static Instant testStartTime;

  // to be injected
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @BeforeAll
  static void setUp() {
    testStartTime = Instant.now();
  }

  @Test
  @Order(1)
  void setupTestThatManipulatesTheClock() {
    // when
    final Instant timeBefore = processTestContext.getCurrentTime();
    processTestContext.increaseTime(TIMER_DURATION);
    final Instant timeAfter = processTestContext.getCurrentTime();

    // then
    assertThat(Duration.between(timeBefore, timeAfter))
        .isCloseTo(TIMER_DURATION, Duration.ofSeconds(10));
  }

  @Test
  @Order(2)
  void ensureClockHasBeenReset() {
    final Instant currentTime = processTestContext.getCurrentTime();

    assertThat(Duration.between(testStartTime, currentTime))
        .isCloseTo(Duration.ofSeconds(10), Duration.ofMinutes(1));
  }

  @Test
  @Order(3)
  public void shouldSetTheTime() {
    // when
    final Instant timeToSet = Instant.parse("2025-05-28T13:30:00Z");
    processTestContext.setTime(timeToSet);

    // then
    final Instant processTime = processTestContext.getCurrentTime();

    assertThat(processTime).isCloseTo(timeToSet, within(10, ChronoUnit.SECONDS));
  }

  @Test
  @Order(3)
  public void shouldSetTheTimeMultipleTimes() {
    // given
    final Instant timeToSet = Instant.parse("2025-05-28T13:30:00Z");

    // when
    processTestContext.increaseTime(Duration.ofHours(4));

    processTestContext.setTime(timeToSet);

    // then
    final Instant processTime = processTestContext.getCurrentTime();

    assertThat(processTime).isCloseTo(timeToSet, within(10, ChronoUnit.SECONDS));
  }
}
