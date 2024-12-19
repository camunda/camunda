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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.of;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.camunda.client.protocol.rest.ClockPinRequest;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayPaths;
import io.camunda.client.util.RestGatewayService;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public final class PinClockTest extends ClientRestTest {

  @Test
  void shouldPinClockToTimestamp() {
    // given
    final long timestamp = 1742461285000L;

    // when
    client.newClockPinCommand().time(timestamp).send().join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.PUT)
        .hasUrl(RestGatewayPaths.getClockPinUrl())
        .extractingBody(ClockPinRequest.class)
        .isEqualTo(new ClockPinRequest().timestamp(timestamp));
  }

  @Test
  void shouldRaiseIllegalArgumentExceptionWhenNegativeTimestampProvided() {
    // when / then
    assertThatThrownBy(() -> client.newClockPinCommand().time(-1L).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("timestamp must be not negative");
  }

  static Stream<Instant> validInstantValues() {
    return Stream.of(Instant.EPOCH, Instant.now(), Instant.now().plus(Duration.ofDays(365 * 100)));
  }

  @ParameterizedTest
  @MethodSource("validInstantValues")
  void shouldPinClockToInstant(final Instant validInstant) {
    // when
    client.newClockPinCommand().time(validInstant).send().join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.PUT)
        .hasUrl(RestGatewayPaths.getClockPinUrl())
        .extractingBody(ClockPinRequest.class)
        .isEqualTo(new ClockPinRequest().timestamp(validInstant.toEpochMilli()));
  }

  static Stream<Arguments> invalidInstantValues() {
    return Stream.of(
        of(null, "instant must not be null"),
        of(
            Instant.EPOCH.minus(Duration.ofMillis(1)),
            "instant must be equal to or after 1970-01-01T00:00:00Z"));
  }

  @ParameterizedTest
  @MethodSource("invalidInstantValues")
  void shouldRaiseIllegalArgumentExceptionWhenInvalidInstantProvided(
      final Instant invalidInstant, final String expectedMessage) {
    // when / then
    assertThatThrownBy(() -> client.newClockPinCommand().time(invalidInstant).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(expectedMessage);
  }
}
