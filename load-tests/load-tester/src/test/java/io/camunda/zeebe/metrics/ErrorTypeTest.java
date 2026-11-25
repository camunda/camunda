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

import io.camunda.zeebe.client.api.command.ClientHttpException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class ErrorTypeTest {

  @Test
  void shouldDetectWrappedBackpressure() {
    // given
    final var exhausted =
        new CompletionException(new StatusRuntimeException(Status.RESOURCE_EXHAUSTED));
    final var tooManyRequests = new ClientHttpException(429, "Too Many Requests");

    // when / then
    assertThat(ErrorType.isBackpressure(exhausted)).isTrue();
    assertThat(ErrorType.isBackpressure(tooManyRequests)).isTrue();
  }

  @Test
  void shouldNotTreatOtherFailuresAsBackpressure() {
    // given
    final var unauthenticated = new StatusRuntimeException(Status.UNAUTHENTICATED);
    final var unavailable = new StatusRuntimeException(Status.UNAVAILABLE);

    // when / then
    assertThat(ErrorType.isBackpressure(unauthenticated)).isFalse();
    assertThat(ErrorType.isBackpressure(unavailable)).isFalse();
    assertThat(ErrorType.isBackpressure(new RuntimeException("boom"))).isFalse();
  }

  @Test
  void shouldClassifyHttpFailureByStatusCode() {
    // given
    final var error = new ClientHttpException(503, "Service Unavailable");

    // when / then
    assertThat(ErrorType.of(error)).isEqualTo("http_503");
  }
}
