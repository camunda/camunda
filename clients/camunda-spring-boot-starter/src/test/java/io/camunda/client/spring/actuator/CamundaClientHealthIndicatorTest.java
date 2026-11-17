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
package io.camunda.client.spring.actuator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.response.StatusResponse;
import io.camunda.client.health.HealthCheck;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;

@ExtendWith(MockitoExtension.class)
final class CamundaClientHealthIndicatorTest {

  @Mock private HealthCheck healthCheck;

  @Test
  void shouldReportUpWhenHealthCheckReportsUp() {
    // given
    when(healthCheck.health()).thenReturn(StatusResponse.Status.UP);
    final CamundaClientHealthIndicator indicator = new CamundaClientHealthIndicator(healthCheck);

    // when
    final Health health = indicator.health();

    // then
    assertThat(health.getStatus().getCode()).isEqualTo("UP");
  }

  @Test
  void shouldReportDownWhenHealthCheckReportsDown() {
    // given
    when(healthCheck.health()).thenReturn(StatusResponse.Status.DOWN);
    final CamundaClientHealthIndicator indicator = new CamundaClientHealthIndicator(healthCheck);

    // when
    final Health health = indicator.health();

    // then
    assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
  }

  @Test
  void shouldReportDownWithErrorDetailWhenHealthCheckThrows() {
    // given
    final ClientStatusException failure =
        new ClientStatusException(
            Status.UNAVAILABLE.withDescription("unreachable"), new RuntimeException());
    when(healthCheck.health()).thenThrow(failure);
    final CamundaClientHealthIndicator indicator = new CamundaClientHealthIndicator(healthCheck);

    // when
    final Health health = indicator.health();

    // then: AbstractHealthIndicator.health() catches the exception thrown by doHealthCheck and
    // maps it to DOWN with the exception recorded as the "error" detail, which is the behaviour
    // HealthCheck.health() relies on instead of catching failures itself.
    assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
    assertThat(health.getDetails()).containsKey("error");
  }
}
