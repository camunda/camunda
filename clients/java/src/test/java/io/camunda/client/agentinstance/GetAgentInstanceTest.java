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
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.enums.AgentInstanceStatus;
import io.camunda.client.api.search.response.AgentInstance;
import io.camunda.client.protocol.rest.AgentInstanceDefinition;
import io.camunda.client.protocol.rest.AgentInstanceLimits;
import io.camunda.client.protocol.rest.AgentInstanceMetrics;
import io.camunda.client.protocol.rest.AgentInstanceResult;
import io.camunda.client.protocol.rest.AgentInstanceStatusEnum;
import io.camunda.client.protocol.rest.AgentTool;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.time.OffsetDateTime;
import java.util.Collections;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

class GetAgentInstanceTest extends ClientRestTest {

  @Test
  void shouldGetAgentInstanceByKey() {
    // given
    final long agentInstanceKey = 1234L;
    final OffsetDateTime now = OffsetDateTime.now();
    final AgentTool tool =
        new AgentTool().name("search").description("A web search tool").elementId("searchTask");

    gatewayService.onAgentInstanceGetRequest(
        agentInstanceKey,
        Instancio.create(AgentInstanceResult.class)
            .agentInstanceKey(String.valueOf(agentInstanceKey))
            .status(AgentInstanceStatusEnum.IDLE)
            .processInstanceKey("9000")
            .rootProcessInstanceKey("50")
            .processDefinitionKey("5000")
            .elementId("agentElement")
            .processDefinitionId("testProcess")
            .processDefinitionVersion(2)
            .processDefinitionVersionTag("v2")
            .tenantId("<default>")
            .creationDate(now.toString())
            .lastUpdatedDate(now.toString())
            .completionDate(null)
            .elementInstanceKeys(Collections.singletonList("6000"))
            .tools(Collections.singletonList(tool))
            .definition(
                new AgentInstanceDefinition()
                    .model("gpt-4o")
                    .provider("openai")
                    .systemPrompt("You are a helpful agent"))
            .metrics(
                new AgentInstanceMetrics()
                    .inputTokens(100L)
                    .outputTokens(200L)
                    .modelCalls(5)
                    .toolCalls(3))
            .limits(new AgentInstanceLimits().maxModelCalls(10).maxToolCalls(20).maxTokens(5000L)));

    // when
    final AgentInstance result = client.newAgentInstanceGetRequest(agentInstanceKey).send().join();

    // then it sends the correct request
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.GET);
    assertThat(request.getUrl()).isEqualTo("/v2/agent-instances/1234");
    assertThat(request.getBodyAsString()).isEmpty();

    // and maps the response correctly
    assertSoftly(
        softly -> {
          softly
              .assertThat(result.getAgentInstanceKey())
              .as("agentInstanceKey")
              .isEqualTo(agentInstanceKey);
          softly.assertThat(result.getStatus()).as("status").isEqualTo(AgentInstanceStatus.IDLE);
          softly
              .assertThat(result.getProcessInstanceKey())
              .as("processInstanceKey")
              .isEqualTo(9000L);
          softly
              .assertThat(result.getRootProcessInstanceKey())
              .as("rootProcessInstanceKey")
              .isEqualTo(50L);
          softly
              .assertThat(result.getProcessDefinitionKey())
              .as("processDefinitionKey")
              .isEqualTo(5000L);
          softly.assertThat(result.getElementId()).as("elementId").isEqualTo("agentElement");
          softly
              .assertThat(result.getProcessDefinitionId())
              .as("processDefinitionId")
              .isEqualTo("testProcess");
          softly
              .assertThat(result.getProcessDefinitionVersion())
              .as("processDefinitionVersion")
              .isEqualTo(2);
          softly
              .assertThat(result.getProcessDefinitionVersionTag())
              .as("processDefinitionVersionTag")
              .isEqualTo("v2");
          softly.assertThat(result.getTenantId()).as("tenantId").isEqualTo("<default>");
          softly.assertThat(result.getCreationDate()).as("creationDate").isEqualTo(now);
          softly.assertThat(result.getLastUpdatedDate()).as("lastUpdatedDate").isEqualTo(now);
          softly.assertThat(result.getCompletionDate()).as("completionDate").isNull();
          softly
              .assertThat(result.getElementInstanceKeys())
              .as("elementInstanceKeys")
              .containsExactly(6000L);

          final AgentInstance.Definition definition = result.getDefinition();
          softly.assertThat(definition.getModel()).as("model").isEqualTo("gpt-4o");
          softly.assertThat(definition.getProvider()).as("provider").isEqualTo("openai");
          softly
              .assertThat(definition.getSystemPrompt())
              .as("systemPrompt")
              .isEqualTo("You are a helpful agent");

          final AgentInstance.Metrics metrics = result.getMetrics();
          softly.assertThat(metrics.getInputTokens()).as("inputTokens").isEqualTo(100L);
          softly.assertThat(metrics.getOutputTokens()).as("outputTokens").isEqualTo(200L);
          softly.assertThat(metrics.getModelCalls()).as("modelCalls").isEqualTo(5);
          softly.assertThat(metrics.getToolCalls()).as("toolCalls").isEqualTo(3);

          final AgentInstance.Limits limits = result.getLimits();
          softly.assertThat(limits.getMaxModelCalls()).as("maxModelCalls").isEqualTo(10);
          softly.assertThat(limits.getMaxToolCalls()).as("maxToolCalls").isEqualTo(20);
          softly.assertThat(limits.getMaxTokens()).as("maxTokens").isEqualTo(5000L);

          softly.assertThat(result.getTools()).as("tools").hasSize(1);
          final AgentInstance.Tool resultTool = result.getTools().get(0);
          softly.assertThat(resultTool.getName()).as("tool[0].name").isEqualTo("search");
          softly
              .assertThat(resultTool.getDescription())
              .as("tool[0].description")
              .isEqualTo("A web search tool");
          softly
              .assertThat(resultTool.getElementId())
              .as("tool[0].elementId")
              .isEqualTo("searchTask");
        });
  }

  @Test
  void shouldThrowOnInvalidAgentInstanceKey() {
    assertThatThrownBy(() -> client.newAgentInstanceGetRequest(0).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("agentInstanceKey must be greater than 0");

    assertThatThrownBy(() -> client.newAgentInstanceGetRequest(-1).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("agentInstanceKey must be greater than 0");
  }
}
