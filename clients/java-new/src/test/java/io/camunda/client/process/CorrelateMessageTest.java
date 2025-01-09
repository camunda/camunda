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
package io.camunda.client.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.InternalClientException;
import io.camunda.client.protocol.rest.MessageCorrelationRequest;
import io.camunda.client.util.ClientRestTest;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class CorrelateMessageTest extends ClientRestTest {

  @Test
  void shouldCorrelateMessageWithCorrelationKey() {
    // given
    final String messageName = "name";
    final String correlationKey = "correlationKey";
    final String tenantId = "tenant";
    final Map<String, Object> variables = Collections.singletonMap("foo", "bar");

    // when
    client
        .newCorrelateMessageCommand()
        .messageName(messageName)
        .correlationKey(correlationKey)
        .tenantId(tenantId)
        .variables(variables)
        .send()
        .join();

    // then
    final MessageCorrelationRequest request =
        gatewayService.getLastRequest(MessageCorrelationRequest.class);
    assertThat(request.getName()).isEqualTo(messageName);
    assertThat(request.getCorrelationKey()).isEqualTo(correlationKey);
    assertThat(request.getTenantId()).isEqualTo(tenantId);
    assertThat(request.getVariables()).isEqualTo(variables);
  }

  @Test
  void shouldCorrelateMessageWithoutCorrelationKey() {
    // given
    final String messageName = "name";
    final String tenantId = "tenant";
    final Map<String, Object> variables = Collections.singletonMap("foo", "bar");

    // when
    client
        .newCorrelateMessageCommand()
        .messageName(messageName)
        .withoutCorrelationKey()
        .tenantId(tenantId)
        .variables(variables)
        .send()
        .join();

    // then
    final MessageCorrelationRequest request =
        gatewayService.getLastRequest(MessageCorrelationRequest.class);
    assertThat(request.getName()).isEqualTo(messageName);
    assertThat(request.getCorrelationKey()).isEmpty();
    assertThat(request.getTenantId()).isEqualTo(tenantId);
    assertThat(request.getVariables()).isEqualTo(variables);
  }

  @Test
  void shouldThrowExceptionWhenVariablesAreNotInMapStructure() {
    // given
    final String messageName = "name";
    final String tenantId = "tenant";
    final String variables = "[]";

    // when then
    assertThatThrownBy(
            () ->
                client
                    .newCorrelateMessageCommand()
                    .messageName(messageName)
                    .withoutCorrelationKey()
                    .tenantId(tenantId)
                    .variables(variables))
        .isInstanceOf(InternalClientException.class)
        .hasMessageContaining(
            String.format("Failed to deserialize json '%s' to 'Map<String, Object>'", variables));
  }
}
