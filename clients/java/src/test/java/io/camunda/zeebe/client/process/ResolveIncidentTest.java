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
package io.camunda.zeebe.client.process;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.command.ResolveIncidentCommandStep1;
import io.camunda.zeebe.client.api.response.ResolveIncidentResponse;
import io.camunda.zeebe.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ResolveIncidentRequest;
import java.time.Duration;
import org.junit.Test;

public final class ResolveIncidentTest extends ClientTest {

  @Test
  public void shouldSendCommand() {
    // when
    client.newResolveIncidentCommand(123).send().join();

    // then
    final ResolveIncidentRequest request = gatewayService.getLastRequest();
    assertThat(request.getIncidentKey()).isEqualTo(123);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client.newResolveIncidentCommand(123).requestTimeout(requestTimeout).send().join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  @Test
  public void shouldNotHaveNullResponse() {
    // given
    final ResolveIncidentCommandStep1 command = client.newResolveIncidentCommand(12);

    // when
    final ResolveIncidentResponse response = command.send().join();

    // then
    assertThat(response).isNotNull();
  }
}
