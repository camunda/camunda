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
package io.camunda.client.clock;

import static io.camunda.client.util.assertions.LoggedRequestAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.camunda.client.api.response.ClockResponse;
import io.camunda.client.protocol.rest.ClockAddRequest;
import io.camunda.client.protocol.rest.ClockResult;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import io.camunda.client.util.RestGatewayService;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public final class AddClockTest extends ClientRestTest {

  @Test
  void shouldAddTimeToClock() {
    // given
    final String newTime = "2025-03-20T10:01:25.000Z";
    gatewayService.onAddClockRequest(new ClockResult().timestamp(newTime));

    // when
    final ClockResponse response =
        client.newAddClockCommand().offset(Duration.ofHours(1)).send().join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getClockAddUrl());
    assertThat(response.getTimestamp()).isEqualTo(OffsetDateTime.parse(newTime));
  }

  static Stream<Duration> offsets() {
    return Stream.of(
        Duration.ofHours(1),
        Duration.ofMinutes(-30),
        Duration.ofDays(1),
        Duration.ofMillis(1500),
        Duration.ofMillis(-500),
        Duration.ZERO);
  }

  @ParameterizedTest
  @MethodSource("offsets")
  void shouldSendOffsetAsIsoDuration(final Duration offset) {
    // given
    gatewayService.onAddClockRequest(new ClockResult().timestamp("2025-03-20T10:01:25.000Z"));

    // when
    client.newAddClockCommand().offset(offset).send().join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getClockAddUrl())
        .extractingBody(ClockAddRequest.class)
        .satisfies(request -> assertThat(Duration.parse(request.getOffset())).isEqualTo(offset));
  }

  @Test
  void shouldRaiseIllegalArgumentExceptionWhenOffsetIsNull() {
    // when / then
    assertThatThrownBy(() -> client.newAddClockCommand().offset(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("offset must not be null");
  }
}
