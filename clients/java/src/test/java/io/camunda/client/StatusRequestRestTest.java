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
  void shouldRequestStatusWhenHealthy() throws ExecutionException, InterruptedException {
    // given - cluster is healthy (returns 204 No Content)
    gatewayService.onStatusRequestHealthy();

    // when
    final Future<StatusResponse> response = client.newStatusRequest().send();

    // then
    assertThat(response).succeedsWithin(Duration.ofSeconds(5));
    final StatusResponse status = response.get();
    assertThat(status.getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldRequestStatusWhenUnhealthy() throws ExecutionException, InterruptedException {
    // given - cluster is unhealthy (returns 503 Service Unavailable)
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
