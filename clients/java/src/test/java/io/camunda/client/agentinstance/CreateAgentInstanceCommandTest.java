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
package io.camunda.client.agentinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.response.CreateAgentInstanceResponse;
import io.camunda.client.protocol.rest.AgentInstanceCreationRequest;
import io.camunda.client.protocol.rest.AgentInstanceCreationResult;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CreateAgentInstanceCommandTest extends ClientRestTest {

  private static final long ELEMENT_INSTANCE_KEY = 2251799813685248L;
  private static final String MODEL = "gpt-4o";
  private static final String PROVIDER = "openai";
  private static final String SYSTEM_PROMPT = "You are a helpful assistant.";

  // ── Happy-path: request body ──────────────────────────────────────────────

  @Test
  void shouldSendPostToCorrectUrl() {
    // given
    gatewayService.onCreateAgentInstanceRequest(
        new AgentInstanceCreationResult().agentInstanceKey("1"));

    // when
    client
        .newCreateAgentInstanceCommand()
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .model(MODEL)
        .provider(PROVIDER)
        .systemPrompt(SYSTEM_PROMPT)
        .execute();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(request.getUrl()).isEqualTo("/v2/agent-instances");
  }

  @Test
  void shouldSendRequiredFieldsInRequestBody() {
    // given
    gatewayService.onCreateAgentInstanceRequest(
        new AgentInstanceCreationResult().agentInstanceKey("1"));

    // when
    client
        .newCreateAgentInstanceCommand()
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .model(MODEL)
        .provider(PROVIDER)
        .systemPrompt(SYSTEM_PROMPT)
        .execute();

    // then
    final AgentInstanceCreationRequest body =
        gatewayService.getLastRequest(AgentInstanceCreationRequest.class);
    assertThat(body.getElementInstanceKey()).isEqualTo(String.valueOf(ELEMENT_INSTANCE_KEY));
    assertThat(body.getDefinition().getModel()).isEqualTo(MODEL);
    assertThat(body.getDefinition().getProvider()).isEqualTo(PROVIDER);
    assertThat(body.getDefinition().getSystemPrompt()).isEqualTo(SYSTEM_PROMPT);
    assertThat(body.getLimits()).isNull();
  }

  @Test
  void shouldSendRequiredAndOptionalFieldsInRequestBody() {
    // given
    gatewayService.onCreateAgentInstanceRequest(
        new AgentInstanceCreationResult().agentInstanceKey("3"));

    // when
    client
        .newCreateAgentInstanceCommand()
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .model(MODEL)
        .provider(PROVIDER)
        .systemPrompt(SYSTEM_PROMPT)
        .maxTokens(5000L)
        .maxModelCalls(10)
        .maxToolCalls(20)
        .execute();

    // then
    final AgentInstanceCreationRequest body =
        gatewayService.getLastRequest(AgentInstanceCreationRequest.class);
    assertThat(body.getLimits()).isNotNull();
    assertThat(body.getLimits().getMaxTokens()).isEqualTo(5000L);
    assertThat(body.getLimits().getMaxModelCalls()).isEqualTo(10);
    assertThat(body.getLimits().getMaxToolCalls()).isEqualTo(20);
  }

  @Test
  void shouldAcceptMinusOneAsNoLimitSentinel() {
    // given
    gatewayService.onCreateAgentInstanceRequest(
        new AgentInstanceCreationResult().agentInstanceKey("4"));

    // when / then — -1 is the "no limit" sentinel and must not throw
    client
        .newCreateAgentInstanceCommand()
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .model(MODEL)
        .provider(PROVIDER)
        .systemPrompt(SYSTEM_PROMPT)
        .maxTokens(-1L)
        .maxModelCalls(-1)
        .maxToolCalls(-1)
        .execute();
  }

  @Test
  void shouldParseAgentInstanceKeyFromResponse() {
    // given
    gatewayService.onCreateAgentInstanceRequest(
        new AgentInstanceCreationResult().agentInstanceKey("9876543210"));

    // when
    final CreateAgentInstanceResponse response =
        client
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(ELEMENT_INSTANCE_KEY)
            .model(MODEL)
            .provider(PROVIDER)
            .systemPrompt(SYSTEM_PROMPT)
            .execute();

    // then
    assertThat(response.getAgentInstanceKey()).isEqualTo(9876543210L);
  }

  // ── Argument validation: elementInstanceKey ───────────────────────────────

  @ParameterizedTest(name = "elementInstanceKey={0} should be rejected")
  @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
  void shouldRejectNonPositiveElementInstanceKey(final long invalidKey) {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentInstanceCommand()
                    .elementInstanceKey(invalidKey)
                    .model(MODEL)
                    .provider(PROVIDER)
                    .systemPrompt(SYSTEM_PROMPT)
                    .execute())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("elementInstanceKey must be greater than 0");
  }

  // ── Argument validation: required String fields ───────────────────────────

  @ParameterizedTest(name = "model=''{0}'' should be rejected")
  @MethodSource("nullAndEmpty")
  void shouldRejectNullOrEmptyModel(final String model) {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentInstanceCommand()
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .model(model)
                    .provider(PROVIDER)
                    .systemPrompt(SYSTEM_PROMPT)
                    .execute())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "provider=''{0}'' should be rejected")
  @MethodSource("nullAndEmpty")
  void shouldRejectNullOrEmptyProvider(final String provider) {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentInstanceCommand()
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .model(MODEL)
                    .provider(provider)
                    .systemPrompt(SYSTEM_PROMPT)
                    .execute())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "systemPrompt=''{0}'' should be rejected")
  @MethodSource("nullAndEmpty")
  void shouldRejectNullOrEmptySystemPrompt(final String systemPrompt) {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentInstanceCommand()
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .model(MODEL)
                    .provider(PROVIDER)
                    .systemPrompt(systemPrompt)
                    .execute())
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ── Argument validation: limit lower-bounds ───────────────────────────────

  @ParameterizedTest(name = "maxTokens={0} should be rejected")
  @ValueSource(longs = {-2L, Long.MIN_VALUE})
  void shouldRejectTooSmallMaxTokens(final long maxTokens) {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentInstanceCommand()
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .model(MODEL)
                    .provider(PROVIDER)
                    .systemPrompt(SYSTEM_PROMPT)
                    .maxTokens(maxTokens)
                    .execute())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maxTokens must be greater than -2");
  }

  @ParameterizedTest(name = "maxModelCalls={0} should be rejected")
  @ValueSource(ints = {-2, Integer.MIN_VALUE})
  void shouldRejectTooSmallMaxModelCalls(final int maxModelCalls) {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentInstanceCommand()
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .model(MODEL)
                    .provider(PROVIDER)
                    .systemPrompt(SYSTEM_PROMPT)
                    .maxModelCalls(maxModelCalls)
                    .execute())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maxModelCalls must be greater than -2");
  }

  @ParameterizedTest(name = "maxToolCalls={0} should be rejected")
  @ValueSource(ints = {-2, Integer.MIN_VALUE})
  void shouldRejectTooSmallMaxToolCalls(final int maxToolCalls) {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentInstanceCommand()
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .model(MODEL)
                    .provider(PROVIDER)
                    .systemPrompt(SYSTEM_PROMPT)
                    .maxToolCalls(maxToolCalls)
                    .execute())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maxToolCalls must be greater than -2");
  }

  static String[] nullAndEmpty() {
    return new String[] {null, ""};
  }
}
