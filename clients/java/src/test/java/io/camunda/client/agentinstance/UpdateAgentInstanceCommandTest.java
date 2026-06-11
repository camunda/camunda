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
import static org.assertj.core.groups.Tuple.tuple;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.AgentTool;
import io.camunda.client.protocol.rest.AgentInstanceUpdateRequest;
import io.camunda.client.protocol.rest.AgentInstanceUpdateStatusEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UpdateAgentInstanceCommandTest extends ClientRestTest {

  private static final long AGENT_INSTANCE_KEY = 1234L;
  private static final long ELEMENT_INSTANCE_KEY = 5678L;

  // ── Happy-path: request routing ───────────────────────────────────────────

  @Test
  void shouldSendPatchToCorrectUrl() {
    // given
    gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);

    // when
    client
        .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .execute();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.PATCH);
    assertThat(request.getUrl()).isEqualTo("/v2/agent-instances/1234");
  }

  @Test
  void shouldSendAllOptionalFieldsInRequestBody() {
    // given
    gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);

    // when
    client
        .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .status(AgentInstanceUpdateStatus.THINKING)
        .inputTokens(100L)
        .outputTokens(200L)
        .modelCalls(3)
        .toolCalls(2)
        .execute();

    // then
    final AgentInstanceUpdateRequest body =
        gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
    assertThat(body.getElementInstanceKey()).isEqualTo(String.valueOf(ELEMENT_INSTANCE_KEY));
    assertThat(body.getStatus()).isEqualTo(AgentInstanceUpdateStatusEnum.THINKING);
    assertThat(body.getMetrics().getInputTokens()).isEqualTo(100L);
    assertThat(body.getMetrics().getOutputTokens()).isEqualTo(200L);
    assertThat(body.getMetrics().getModelCalls()).isEqualTo(3);
    assertThat(body.getMetrics().getToolCalls()).isEqualTo(2);
  }

  @Test
  void shouldSendOnlyElementInstanceKeyWhenNoOtherFieldsSet() {
    // given
    gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);

    // when
    client
        .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .execute();

    // then
    final AgentInstanceUpdateRequest body =
        gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
    assertThat(body.getElementInstanceKey()).isEqualTo(String.valueOf(ELEMENT_INSTANCE_KEY));
    assertThat(body.getStatus()).isNull();
    assertThat(body.getMetrics()).isNull();
    assertThat(body.getTools()).isNull();
  }

  // ── Tools mapping ─────────────────────────────────────────────────────────

  @Test
  void shouldMapToolsWithAllFields() {
    // given
    gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);

    // when
    client
        .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .tools(Arrays.asList(AgentTool.of("search", "A web search tool", "searchTask")))
        .execute();

    // then
    final AgentInstanceUpdateRequest body =
        gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
    assertThat(body.getTools())
        .singleElement()
        .satisfies(
            tool -> {
              assertThat(tool.getName()).isEqualTo("search");
              assertThat(tool.getDescription()).isEqualTo("A web search tool");
              assertThat(tool.getElementId()).isEqualTo("searchTask");
            });
  }

  @Test
  void shouldMapToolWithNameOnlyOmittingNullOptionalFields() {
    // given
    gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);

    // when
    client
        .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .tools(Arrays.asList(AgentTool.of("summarize")))
        .execute();

    // then
    final AgentInstanceUpdateRequest body =
        gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
    assertThat(body.getTools()).hasSize(1);
    final io.camunda.client.protocol.rest.AgentTool tool = body.getTools().get(0);
    assertThat(tool.getName()).isEqualTo("summarize");
    assertThat(tool.getDescription()).isNull();
    assertThat(tool.getElementId()).isNull();
  }

  @Test
  void shouldSendEmptyToolsList() {
    // given
    gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);

    // when
    client
        .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .tools(Collections.emptyList())
        .execute();

    // then
    final AgentInstanceUpdateRequest body =
        gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
    assertThat(body.getTools()).isEmpty();
  }

  @Test
  void shouldMapMultipleToolsMixingOptionalFields() {
    // given
    gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);

    // when
    client
        .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .tools(
            Arrays.asList(
                AgentTool.of("search", "Search the web", "searchTask"), AgentTool.of("summarize")))
        .execute();

    // then
    final AgentInstanceUpdateRequest body =
        gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
    assertThat(body.getTools())
        .extracting(
            io.camunda.client.protocol.rest.AgentTool::getName,
            io.camunda.client.protocol.rest.AgentTool::getDescription,
            io.camunda.client.protocol.rest.AgentTool::getElementId)
        .containsExactly(
            tuple("search", "Search the web", "searchTask"), tuple("summarize", null, null));
  }

  // ── Argument validation: agentInstanceKey ────────────────────────────────

  @ParameterizedTest(name = "agentInstanceKey={0} should be rejected")
  @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
  void shouldRejectNonPositiveAgentInstanceKey(final long invalidKey) {
    assertThatThrownBy(() -> client.newUpdateAgentInstanceCommand(invalidKey))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("agentInstanceKey must be greater than 0");
  }

  // ── Argument validation: elementInstanceKey ───────────────────────────────

  @ParameterizedTest(name = "elementInstanceKey={0} should be rejected")
  @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
  void shouldRejectNonPositiveElementInstanceKey(final long invalidKey) {
    assertThatThrownBy(
            () ->
                client
                    .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(invalidKey)
                    .execute())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("elementInstanceKey must be greater than 0");
  }
}
