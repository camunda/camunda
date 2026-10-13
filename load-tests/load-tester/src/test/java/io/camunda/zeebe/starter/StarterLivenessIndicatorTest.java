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
package io.camunda.zeebe.starter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.config.StarterProperties;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class StarterLivenessIndicatorTest {

  private final AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);
  private final InstantSource clock = now::get;

  private StarterLivenessIndicator indicator() {
    final var properties = new StarterProperties();
    properties.setLivenessMaxNoSuccessAge(Duration.ofSeconds(60));
    return new StarterLivenessIndicator(properties, clock);
  }

  private void advance(final long seconds) {
    now.set(now.get().plusSeconds(seconds));
  }

  @Test
  void shouldBeUpBeforeTheStarterIsConnected() {
    // given
    final var indicator = indicator();

    // when
    advance(3600);

    // then
    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldBeUpWhileRequestsSucceed() {
    // given
    final var indicator = indicator();
    indicator.recordStarted();

    // when
    advance(50);
    indicator.recordResult(null);
    advance(50);

    // then
    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldBeDownWhenNothingSucceededWithinTheAgeAfterStart() {
    // given
    final var indicator = indicator();
    indicator.recordStarted();

    // when
    advance(61);

    // then
    assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void shouldBeDownWhenOnlyFailuresArrive() {
    // given
    final var indicator = indicator();
    indicator.recordStarted();

    // when
    for (int i = 0; i < 61; i++) {
      advance(1);
      indicator.recordResult(
          new CompletionException(new StatusRuntimeException(io.grpc.Status.UNAUTHENTICATED)));
    }

    // then
    assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void shouldTreatBackpressureAsAlive() {
    // given
    final var indicator = indicator();
    indicator.recordStarted();

    // when
    for (int i = 0; i < 120; i++) {
      advance(1);
      indicator.recordResult(new StatusRuntimeException(io.grpc.Status.RESOURCE_EXHAUSTED));
    }

    // then
    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldBeUpAfterRunFinished() {
    // given
    final var indicator = indicator();
    indicator.recordStarted();
    indicator.recordFinished();

    // when
    advance(3600);

    // then
    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
  }
}
