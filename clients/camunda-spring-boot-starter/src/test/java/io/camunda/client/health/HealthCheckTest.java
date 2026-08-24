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
package io.camunda.client.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.command.StatusRequestStep1;
import io.camunda.client.api.response.StatusResponse;
import io.grpc.Status;
import org.junit.jupiter.api.Test;

final class HealthCheckTest {

  @Test
  void shouldReportUpWhenStatusRequestReportsUp() {
    // given
    final CamundaClient camundaClient = mock(CamundaClient.class);
    final StatusRequestStep1 statusRequestStep1 = mock(StatusRequestStep1.class);
    final StatusResponse statusResponse = mock(StatusResponse.class);
    when(camundaClient.newStatusRequest()).thenReturn(statusRequestStep1);
    when(statusRequestStep1.execute()).thenReturn(statusResponse);
    when(statusResponse.getStatus()).thenReturn(StatusResponse.Status.UP);
    final HealthCheck healthCheck = new HealthCheck(camundaClient);

    // when
    final StatusResponse.Status result = healthCheck.health();

    // then
    assertThat(result).isEqualTo(StatusResponse.Status.UP);
  }

  @Test
  void shouldReportDownWhenStatusRequestReportsDown() {
    // given
    final CamundaClient camundaClient = mock(CamundaClient.class);
    final StatusRequestStep1 statusRequestStep1 = mock(StatusRequestStep1.class);
    final StatusResponse statusResponse = mock(StatusResponse.class);
    when(camundaClient.newStatusRequest()).thenReturn(statusRequestStep1);
    when(statusRequestStep1.execute()).thenReturn(statusResponse);
    when(statusResponse.getStatus()).thenReturn(StatusResponse.Status.DOWN);
    final HealthCheck healthCheck = new HealthCheck(camundaClient);

    // when
    final StatusResponse.Status result = healthCheck.health();

    // then
    assertThat(result).isEqualTo(StatusResponse.Status.DOWN);
  }

  @Test
  void shouldPropagateExceptionWhenStatusRequestFailsWithConnectivityIssue() {
    // given
    final CamundaClient camundaClient = mock(CamundaClient.class);
    final StatusRequestStep1 statusRequestStep1 = mock(StatusRequestStep1.class);
    final ClientStatusException failure =
        new ClientStatusException(
            Status.UNAVAILABLE.withDescription("unreachable"), new RuntimeException());
    when(camundaClient.newStatusRequest()).thenReturn(statusRequestStep1);
    when(statusRequestStep1.execute()).thenThrow(failure);
    final HealthCheck healthCheck = new HealthCheck(camundaClient);

    // when then
    assertThatThrownBy(healthCheck::health).isSameAs(failure);
  }
}
