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
package io.camunda.client.agentdefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.enums.AgentDefinitionType;
import io.camunda.client.api.search.response.AgentDefinition;
import io.camunda.client.protocol.rest.AgentDefinitionResult;
import io.camunda.client.protocol.rest.AgentDefinitionTypeEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

class GetAgentDefinitionTest extends ClientRestTest {

  @Test
  void shouldGetAgentDefinitionByKey() {
    // given
    final long agentDefinitionKey = 1234L;

    gatewayService.onAgentDefinitionGetRequest(
        agentDefinitionKey,
        Instancio.create(AgentDefinitionResult.class)
            .agentDefinitionKey(String.valueOf(agentDefinitionKey))
            .agentType(AgentDefinitionTypeEnum.AI_AGENT_SUB_PROCESS)
            .name("My Agent")
            .elementId("agentElement")
            .processDefinitionId("testProcess")
            .processDefinitionKey("5000")
            .processDefinitionVersion(2)
            .processDefinitionVersionTag("v2")
            .tenantId("<default>"));

    // when
    final AgentDefinition result =
        client.newAgentDefinitionGetRequest(agentDefinitionKey).send().join();

    // then it sends the correct request
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).as("HTTP method").isEqualTo(RequestMethod.GET);
    assertThat(request.getUrl())
        .as("request URL should target the agent definition by key")
        .isEqualTo("/v2/agent-definitions/1234");
    assertThat(request.getBodyAsString()).as("GET request should have no body").isEmpty();

    // and maps the response correctly
    assertSoftly(
        softly -> {
          softly
              .assertThat(result.getAgentDefinitionKey())
              .as("agentDefinitionKey")
              .isEqualTo(agentDefinitionKey);
          softly
              .assertThat(result.getAgentType())
              .as("agentType")
              .isEqualTo(AgentDefinitionType.AI_AGENT_SUB_PROCESS);
          softly.assertThat(result.getName()).as("name").isEqualTo("My Agent");
          softly.assertThat(result.getElementId()).as("elementId").isEqualTo("agentElement");
          softly
              .assertThat(result.getProcessDefinitionId())
              .as("processDefinitionId")
              .isEqualTo("testProcess");
          softly
              .assertThat(result.getProcessDefinitionKey())
              .as("processDefinitionKey")
              .isEqualTo(5000L);
          softly
              .assertThat(result.getProcessDefinitionVersion())
              .as("processDefinitionVersion")
              .isEqualTo(2);
          softly
              .assertThat(result.getProcessDefinitionVersionTag())
              .as("processDefinitionVersionTag")
              .isEqualTo("v2");
          softly.assertThat(result.getTenantId()).as("tenantId").isEqualTo("<default>");
        });
  }

  @Test
  void shouldThrowOnInvalidAgentDefinitionKey() {
    assertThatThrownBy(() -> client.newAgentDefinitionGetRequest(0).send().join())
        .as("a key of 0 is not a valid agent definition key")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("agentDefinitionKey must be greater than 0");

    assertThatThrownBy(() -> client.newAgentDefinitionGetRequest(-1).send().join())
        .as("a negative key is not a valid agent definition key")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("agentDefinitionKey must be greater than 0");
  }
}
