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
package io.camunda.zeebe.client.resource;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.client.api.command.DeleteResourceCommandStep1;
import io.camunda.zeebe.client.api.response.DeleteResourceResponse;
import io.camunda.zeebe.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeleteResourceRequest;
import java.time.Duration;
import org.junit.Test;

public final class DeleteResourceTest extends ClientTest {

  @Test
  public void shouldSendCommand() {
    // when
    client.newDeleteResourceCommand(123).send().join();

    // then
    final DeleteResourceRequest request = gatewayService.getLastRequest();
    assertThat(request.getResourceKey()).isEqualTo(123);

    rule.verifyDefaultRequestTimeout();
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client.newDeleteResourceCommand(123).requestTimeout(requestTimeout).send().join();

    // then
    rule.verifyRequestTimeout(requestTimeout);
  }

  @Test
  public void shouldNotHaveNullResponse() {
    // given
    final DeleteResourceCommandStep1 command = client.newDeleteResourceCommand(12);

    // when
    final DeleteResourceResponse response = command.send().join();

    // then
    assertThat(response).isNotNull();
  }
}
