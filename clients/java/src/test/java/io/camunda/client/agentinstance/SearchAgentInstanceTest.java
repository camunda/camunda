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
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.assertj.core.groups.Tuple.tuple;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.enums.AgentInstanceStatus;
import io.camunda.client.api.search.response.AgentInstance;
import io.camunda.client.protocol.rest.AgentInstanceDefinition;
import io.camunda.client.protocol.rest.AgentInstanceLimits;
import io.camunda.client.protocol.rest.AgentInstanceMetrics;
import io.camunda.client.protocol.rest.AgentInstanceResult;
import io.camunda.client.protocol.rest.AgentInstanceSearchQuery;
import io.camunda.client.protocol.rest.AgentInstanceSearchQueryResult;
import io.camunda.client.protocol.rest.AgentInstanceSearchQuerySortRequest;
import io.camunda.client.protocol.rest.AgentInstanceStatusEnum;
import io.camunda.client.protocol.rest.AgentTool;
import io.camunda.client.protocol.rest.SearchQueryPageResponse;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.time.OffsetDateTime;
import java.util.Collections;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

class SearchAgentInstanceTest extends ClientRestTest {

  @Test
  void shouldSearchAgentInstances() {
    // when
    client
        .newAgentInstanceSearchRequest()
        .filter(f -> f.status(AgentInstanceStatus.IDLE))
        .sort(s -> s.creationDate().asc())
        .send()
        .join();

    // then
    final LoggedRequest restRequest = RestGatewayService.getLastRequest();
    assertThat(restRequest.getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(restRequest.getUrl()).isEqualTo("/v2/agent-instances/search");
  }

  @Test
  void shouldSearchAgentInstancesWithoutFilter() {
    // when
    client.newAgentInstanceSearchRequest().send().join();

    // then
    final AgentInstanceSearchQuery request =
        gatewayService.getLastRequest(AgentInstanceSearchQuery.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchAgentInstancesWithStatusFilter() {
    // when
    client
        .newAgentInstanceSearchRequest()
        .filter(f -> f.status(AgentInstanceStatus.COMPLETED))
        .send()
        .join();

    // then
    final AgentInstanceSearchQuery request =
        gatewayService.getLastRequest(AgentInstanceSearchQuery.class);
    assertThat(request.getFilter()).isNotNull();
    assertThat(request.getFilter().getStatus().get$Eq())
        .isEqualTo(io.camunda.client.protocol.rest.AgentInstanceStatusEnum.COMPLETED);
  }

  @Test
  void shouldSearchAgentInstancesWithAgentInstanceKeyFilter() {
    // when
    client.newAgentInstanceSearchRequest().filter(f -> f.agentInstanceKey(1234L)).send().join();

    // then
    final AgentInstanceSearchQuery request =
        gatewayService.getLastRequest(AgentInstanceSearchQuery.class);
    assertThat(request.getFilter().getAgentInstanceKey().get$Eq()).isEqualTo("1234");
  }

  @Test
  void shouldSearchAgentInstancesWithProcessInstanceKeyFilter() {
    // when
    client.newAgentInstanceSearchRequest().filter(f -> f.processInstanceKey(9000L)).send().join();

    // then
    final AgentInstanceSearchQuery request =
        gatewayService.getLastRequest(AgentInstanceSearchQuery.class);
    assertThat(request.getFilter().getProcessInstanceKey().get$Eq()).isEqualTo("9000");
  }

  @Test
  void shouldSearchAgentInstancesWithProcessDefinitionKeyFilter() {
    // when
    client.newAgentInstanceSearchRequest().filter(f -> f.processDefinitionKey(5000L)).send().join();

    // then
    final AgentInstanceSearchQuery request =
        gatewayService.getLastRequest(AgentInstanceSearchQuery.class);
    assertThat(request.getFilter().getProcessDefinitionKey().get$Eq()).isEqualTo("5000");
  }

  @Test
  void shouldSearchAgentInstancesWithElementInstanceKeyFilter() {
    // when
    client.newAgentInstanceSearchRequest().filter(f -> f.elementInstanceKey(6000L)).send().join();

    // then
    final AgentInstanceSearchQuery request =
        gatewayService.getLastRequest(AgentInstanceSearchQuery.class);
    assertThat(request.getFilter().getElementInstanceKeys()).hasSize(1);
    assertThat(request.getFilter().getElementInstanceKeys().get(0).get$Eq()).isEqualTo("6000");
  }

  @Test
  void shouldSearchAgentInstancesWithProcessDefinitionFilters() {
    // when
    client
        .newAgentInstanceSearchRequest()
        .filter(
            f ->
                f.processDefinitionId("testProcess")
                    .processDefinitionVersion(2)
                    .processDefinitionVersionTag("v2"))
        .send()
        .join();

    // then
    final AgentInstanceSearchQuery request =
        gatewayService.getLastRequest(AgentInstanceSearchQuery.class);
    assertThat(request.getFilter().getProcessDefinitionId().get$Eq()).isEqualTo("testProcess");
    assertThat(request.getFilter().getProcessDefinitionVersion().get$Eq()).isEqualTo(2);
    assertThat(request.getFilter().getProcessDefinitionVersionTag().get$Eq()).isEqualTo("v2");
  }

  @Test
  void shouldSearchAgentInstancesWithFullSorting() {
    // when
    client
        .newAgentInstanceSearchRequest()
        .sort(
            s ->
                s.creationDate()
                    .asc()
                    .lastUpdatedDate()
                    .desc()
                    .completionDate()
                    .asc()
                    .status()
                    .asc())
        .send()
        .join();

    // then
    final AgentInstanceSearchQuery request =
        gatewayService.getLastRequest(AgentInstanceSearchQuery.class);
    assertThat(request.getSort())
        .as("sort fields and orders")
        .extracting(
            AgentInstanceSearchQuerySortRequest::getField,
            AgentInstanceSearchQuerySortRequest::getOrder)
        .containsExactly(
            tuple(AgentInstanceSearchQuerySortRequest.FieldEnum.CREATION_DATE, SortOrderEnum.ASC),
            tuple(
                AgentInstanceSearchQuerySortRequest.FieldEnum.LAST_UPDATED_DATE,
                SortOrderEnum.DESC),
            tuple(AgentInstanceSearchQuerySortRequest.FieldEnum.COMPLETION_DATE, SortOrderEnum.ASC),
            tuple(AgentInstanceSearchQuerySortRequest.FieldEnum.STATUS, SortOrderEnum.ASC));
  }

  @Test
  void shouldMapSearchAgentInstancesResponse() {
    // given
    final OffsetDateTime now = OffsetDateTime.now();
    final AgentTool tool =
        new AgentTool().name("search").description("A web search tool").elementId("searchTask");
    final AgentInstanceResult provided =
        Instancio.create(AgentInstanceResult.class)
            .agentInstanceKey("42")
            .status(AgentInstanceStatusEnum.COMPLETED)
            .processInstanceKey("10")
            .rootProcessInstanceKey("5")
            .processDefinitionKey("100")
            .processDefinitionId("testProcess")
            .processDefinitionVersion(1)
            .processDefinitionVersionTag("v1")
            .tenantId("<default>")
            .elementId("agentElement")
            .elementInstanceKeys(Collections.singletonList("6000"))
            .tools(Collections.singletonList(tool))
            .definition(
                new AgentInstanceDefinition()
                    .model("gpt-4o")
                    .provider("openai")
                    .systemPrompt("You are helpful"))
            .metrics(
                new AgentInstanceMetrics()
                    .inputTokens(50L)
                    .outputTokens(100L)
                    .modelCalls(2)
                    .toolCalls(1))
            .limits(new AgentInstanceLimits().maxTokens(1000L).maxModelCalls(5).maxToolCalls(10))
            .creationDate(now.toString())
            .lastUpdatedDate(now.toString())
            .completionDate(now.toString());

    gatewayService.onAgentInstanceSearchRequest(
        Instancio.create(AgentInstanceSearchQueryResult.class)
            .page(
                Instancio.create(SearchQueryPageResponse.class)
                    .totalItems(1L)
                    .hasMoreTotalItems(false))
            .items(Collections.singletonList(provided)));

    // when
    final io.camunda.client.api.search.response.SearchResponse<AgentInstance> result =
        client.newAgentInstanceSearchRequest().send().join();

    // then
    assertSoftly(
        softly -> {
          softly.assertThat(result.page().totalItems()).isEqualTo(1);
          softly.assertThat(result.items()).hasSize(1);

          final AgentInstance item = result.items().get(0);
          softly.assertThat(item.getAgentInstanceKey()).as("agentInstanceKey").isEqualTo(42L);
          softly.assertThat(item.getStatus()).as("status").isEqualTo(AgentInstanceStatus.COMPLETED);
          softly.assertThat(item.getProcessInstanceKey()).as("processInstanceKey").isEqualTo(10L);
          softly
              .assertThat(item.getRootProcessInstanceKey())
              .as("rootProcessInstanceKey")
              .isEqualTo(5L);
          softly
              .assertThat(item.getProcessDefinitionKey())
              .as("processDefinitionKey")
              .isEqualTo(100L);
          softly
              .assertThat(item.getProcessDefinitionId())
              .as("processDefinitionId")
              .isEqualTo("testProcess");
          softly
              .assertThat(item.getProcessDefinitionVersion())
              .as("processDefinitionVersion")
              .isEqualTo(1);
          softly
              .assertThat(item.getProcessDefinitionVersionTag())
              .as("processDefinitionVersionTag")
              .isEqualTo("v1");
          softly.assertThat(item.getTenantId()).as("tenantId").isEqualTo("<default>");
          softly.assertThat(item.getElementId()).as("elementId").isEqualTo("agentElement");
          softly
              .assertThat(item.getElementInstanceKeys())
              .as("elementInstanceKeys")
              .containsExactly(6000L);
          softly.assertThat(item.getCreationDate()).as("creationDate").isEqualTo(now);
          softly.assertThat(item.getLastUpdatedDate()).as("lastUpdatedDate").isEqualTo(now);
          softly.assertThat(item.getCompletionDate()).as("completionDate").isEqualTo(now);

          final AgentInstance.Definition definition = item.getDefinition();
          softly.assertThat(definition.getModel()).as("model").isEqualTo("gpt-4o");
          softly.assertThat(definition.getProvider()).as("provider").isEqualTo("openai");
          softly
              .assertThat(definition.getSystemPrompt())
              .as("systemPrompt")
              .isEqualTo("You are helpful");

          final AgentInstance.Metrics metrics = item.getMetrics();
          softly.assertThat(metrics.getInputTokens()).as("inputTokens").isEqualTo(50L);
          softly.assertThat(metrics.getOutputTokens()).as("outputTokens").isEqualTo(100L);
          softly.assertThat(metrics.getModelCalls()).as("modelCalls").isEqualTo(2);
          softly.assertThat(metrics.getToolCalls()).as("toolCalls").isEqualTo(1);

          final AgentInstance.Limits limits = item.getLimits();
          softly.assertThat(limits.getMaxTokens()).as("maxTokens").isEqualTo(1000L);
          softly.assertThat(limits.getMaxModelCalls()).as("maxModelCalls").isEqualTo(5);
          softly.assertThat(limits.getMaxToolCalls()).as("maxToolCalls").isEqualTo(10);

          softly.assertThat(item.getTools()).as("tools").hasSize(1);
          final AgentInstance.Tool resultTool = item.getTools().get(0);
          softly.assertThat(resultTool.getName()).as("tool.name").isEqualTo("search");
          softly
              .assertThat(resultTool.getDescription())
              .as("tool.description")
              .isEqualTo("A web search tool");
          softly.assertThat(resultTool.getElementId()).as("tool.elementId").isEqualTo("searchTask");
        });
  }
}
