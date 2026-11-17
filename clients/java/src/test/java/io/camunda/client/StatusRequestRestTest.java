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
package io.camunda.client;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.StatusResponse;
import io.camunda.client.api.response.StatusResponse.Status;
import io.camunda.client.util.ClientRestTest;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

public final class StatusRequestRestTest extends ClientRestTest {

  @Test
  void shouldRequestStatusFromClusterScopedEndpoint() {
    // given
    gatewayService.onStatusRequestHealthy();

    // when
    client.newStatusRequest().send().join();

    // then — the cluster status is not physical-tenant scoped, so it is not served below /v2
    verify(getRequestedFor(urlEqualTo("/cluster/v2/status")));
  }

  @Test
  void shouldRequestStatusWhenHealthy() throws ExecutionException, InterruptedException {
    // given - the whole cluster is healthy (200 with HEALTHY)
    gatewayService.onStatusRequestHealthy();

    // when
    final Future<StatusResponse> response = client.newStatusRequest().send();

    // then
    assertThat(response).succeedsWithin(Duration.ofSeconds(5));
    final StatusResponse status = response.get();
    assertThat(status.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldRequestStatusWhenDegraded() throws ExecutionException, InterruptedException {
    // given - part of the cluster is degraded but it still processes work (200 with DEGRADED)
    gatewayService.onStatusRequestDegraded();

    // when
    final Future<StatusResponse> response = client.newStatusRequest().send();

    // then - a degraded cluster still serves traffic, so it must not be reported as down
    assertThat(response).succeedsWithin(Duration.ofSeconds(5));
    final StatusResponse status = response.get();
    assertThat(status.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldRequestStatusWhenUnhealthy() throws ExecutionException, InterruptedException {
    // given - no physical tenant can process work (503 with DOWN)
    gatewayService.onStatusRequestUnhealthy();

    // when
    final Future<StatusResponse> response = client.newStatusRequest().send();

    // then
    assertThat(response).succeedsWithin(Duration.ofSeconds(5));
    final StatusResponse status = response.get();
    assertThat(status.getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void shouldHandleServerErrorAsClientException() {
    // given - server returns 500 Internal Server Error
    gatewayService.onStatusRequest(500);

    // when & then - should throw ClientException for non-503 errors
    assertThatThrownBy(() -> client.newStatusRequest().send().join())
        .isInstanceOf(ClientException.class);
  }

  @Test
  void shouldHandleBadRequestAsClientException() {
    // given - server returns 400 Bad Request
    gatewayService.onStatusRequest(400);

    // when & then - should throw ClientException for non-503 errors
    assertThatThrownBy(() -> client.newStatusRequest().send().join())
        .isInstanceOf(ClientException.class);
  }

  @Test
  void shouldHandleUnauthorizedAsClientException() {
    // given - server returns 401 Unauthorized
    gatewayService.onStatusRequest(401);

    // when & then - should throw ClientException for authentication errors
    assertThatThrownBy(() -> client.newStatusRequest().send().join())
        .isInstanceOf(ClientException.class);
  }

  @Test
  void shouldHandleForbiddenAsClientException() {
    // given - server returns 403 Forbidden
    gatewayService.onStatusRequest(403);

    // when & then - should throw ClientException for authorization errors
    assertThatThrownBy(() -> client.newStatusRequest().send().join())
        .isInstanceOf(ClientException.class);
  }

  @Test
  void shouldHandleNotFoundAsClientException() {
    // given - server returns 404 Not Found
    gatewayService.onStatusRequest(404);

    // when & then - should throw ClientException for endpoint not found
    assertThatThrownBy(() -> client.newStatusRequest().send().join())
        .isInstanceOf(ClientException.class);
  }
}
