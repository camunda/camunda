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
package io.camunda.zeebe.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.availability.ApplicationAvailability;

class ConnectionReadyHealthIndicatorTest {

  @Test
  void shouldRefuseTrafficUntilConnected() {
    // given
    final var monitor = mock(ConnectionMonitor.class);
    when(monitor.isConnected()).thenReturn(false);
    final var indicator =
        new ConnectionReadyHealthIndicator(mock(ApplicationAvailability.class), monitor);

    // when
    final var health = indicator.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
  }

  @Test
  void shouldAcceptTrafficOnceConnected() {
    // given
    final var monitor = mock(ConnectionMonitor.class);
    when(monitor.isConnected()).thenReturn(true);
    final var indicator =
        new ConnectionReadyHealthIndicator(mock(ApplicationAvailability.class), monitor);

    // when
    final var health = indicator.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
  }
}
