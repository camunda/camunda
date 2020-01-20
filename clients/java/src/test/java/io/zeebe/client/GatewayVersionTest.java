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
package io.zeebe.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.client.util.ClientTest;
import java.time.Duration;
import org.junit.Test;

public class GatewayVersionTest extends ClientTest {

  @Test
  public void shouldSendCommand() {
    // given
    final String expected = "expected";
    gatewayService.onGatewayVersion(expected);

    // when
    final String version = client.newGatewayVersionCommand().send().join();

    // then
    rule.verifyDefaultRequestTimeout();
    assertThat(version).isEqualTo(expected);
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client.newGatewayVersionCommand().requestTimeout(requestTimeout).send().join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }
}
