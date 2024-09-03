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
package io.camunda.zeebe.client.clock;

import static io.camunda.zeebe.client.util.assertions.LoggedRequestAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import io.camunda.zeebe.client.protocol.rest.ClockPinRequest;
import io.camunda.zeebe.client.util.ClientRestTest;
import io.camunda.zeebe.client.util.RestGatewayPaths;
import io.camunda.zeebe.client.util.RestGatewayService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

public final class PinClockTest extends ClientRestTest {

  @Test
  void shouldPinClockToTimestamp() {
    // given
    final long timestamp = 1742461285000L;

    // when
    client.newClockPinCommand().time(timestamp).send().join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getClockPinUrl())
        .extractingBody(ClockPinRequest.class)
        .isEqualTo(new ClockPinRequest().timestamp(timestamp));
  }

  @Test
  void shouldPinClockToInstant() {
    // given
    final Instant instant = Instant.now().plus(Duration.ofDays(7));

    // when
    client.newClockPinCommand().time(instant).send().join();

    // then
    assertThat(RestGatewayService.getLastRequest())
        .hasMethod(RequestMethod.POST)
        .hasUrl(RestGatewayPaths.getClockPinUrl())
        .extractingBody(ClockPinRequest.class)
        .isEqualTo(new ClockPinRequest().timestamp(instant.toEpochMilli()));
  }

  @Test
  void shouldRaiseIllegalArgumentExceptionWhenNullInstantProvided() {
    // when / then
    assertThatThrownBy(() -> client.newClockPinCommand().time(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("instant must not be null");
  }
}
