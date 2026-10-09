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
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.assertj.core.groups.Tuple.tuple;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.api.search.enums.AgentDefinitionType;
import io.camunda.client.api.search.response.AgentDefinition;
import io.camunda.client.protocol.rest.AgentDefinitionResult;
import io.camunda.client.protocol.rest.AgentDefinitionSearchQuery;
import io.camunda.client.protocol.rest.AgentDefinitionSearchQueryResult;
import io.camunda.client.protocol.rest.AgentDefinitionSearchQuerySortRequest;
import io.camunda.client.protocol.rest.AgentDefinitionTypeEnum;
import io.camunda.client.protocol.rest.SearchQueryPageResponse;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.util.Collections;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

class SearchAgentDefinitionTest extends ClientRestTest {

  @Test
  void shouldSearchAgentDefinitions() {
    // when
    client.newAgentDefinitionSearchRequest().send().join();

    // then
    final LoggedRequest restRequest = RestGatewayService.getLastRequest();
    assertThat(restRequest.getMethod()).as("HTTP method").isEqualTo(RequestMethod.POST);
    assertThat(restRequest.getUrl())
        .as("request URL should target the agent definition search endpoint")
        .isEqualTo("/v2/agent-definitions/search");
    final AgentDefinitionSearchQuery request =
        gatewayService.getLastRequest(AgentDefinitionSearchQuery.class);
    assertThat(request.getFilter()).as("no filter should be sent when none is specified").isNull();
  }

  @Test
  void shouldSearchAgentDefinitionsWithAgentDefinitionKeyFilter() {
    // when
    client.newAgentDefinitionSearchRequest().filter(f -> f.agentDefinitionKey(1234L)).send().join();

    // then
    final AgentDefinitionSearchQuery request =
        gatewayService.getLastRequest(AgentDefinitionSearchQuery.class);
    assertThat(request.getFilter().getAgentDefinitionKey().get$Eq())
        .as("agentDefinitionKey filter should be sent as its string-encoded $eq value")
        .isEqualTo("1234");
  }

  @Test
  void shouldSearchAgentDefinitionsWithAgentTypeFilter() {
    // when
    client
        .newAgentDefinitionSearchRequest()
        .filter(f -> f.agentType(AgentDefinitionType.AI_AGENT_TASK))
        .send()
        .join();

    // then
    final AgentDefinitionSearchQuery request =
        gatewayService.getLastRequest(AgentDefinitionSearchQuery.class);
    assertThat(request.getFilter().getAgentType().get$Eq())
        .as("agentType filter should be sent as its $eq value")
        .isEqualTo(AgentDefinitionTypeEnum.AI_AGENT_TASK);
  }

  @Test
  void shouldSearchAgentDefinitionsWithAgentTypeNotInFilter() {
    // when
    client
        .newAgentDefinitionSearchRequest()
        .filter(
            f ->
                f.agentType(
                    b ->
                        b.notIn(
                            AgentDefinitionType.AI_AGENT_TASK, AgentDefinitionType.EXTERNAL_AGENT)))
        .send()
        .join();

    // then
    final AgentDefinitionSearchQuery request =
        gatewayService.getLastRequest(AgentDefinitionSearchQuery.class);
    assertThat(request.getFilter().getAgentType().get$NotIn())
        .as("agentType $notIn filter should carry both excluded values, in order")
        .containsExactly(
            AgentDefinitionTypeEnum.AI_AGENT_TASK, AgentDefinitionTypeEnum.EXTERNAL_AGENT);
  }

  @Test
  void shouldSearchAgentDefinitionsWithNameAndElementIdFilters() {
    // when
    client
        .newAgentDefinitionSearchRequest()
        .filter(f -> f.name("My Agent").elementId("agentElement"))
        .send()
        .join();

    // then
    final AgentDefinitionSearchQuery request =
        gatewayService.getLastRequest(AgentDefinitionSearchQuery.class);
    assertThat(request.getFilter().getName().get$Eq())
        .as("name filter should be sent as its $eq value")
        .isEqualTo("My Agent");
    assertThat(request.getFilter().getElementId().get$Eq())
        .as("elementId filter should be sent as its $eq value")
        .isEqualTo("agentElement");
  }

  @Test
  void shouldSearchAgentDefinitionsWithProcessDefinitionFilters() {
    // when
    client
        .newAgentDefinitionSearchRequest()
        .filter(
            f ->
                f.processDefinitionId("testProcess")
                    .processDefinitionKey(5000L)
                    .processDefinitionVersion(2)
                    .processDefinitionVersionTag("v2"))
        .send()
        .join();

    // then
    final AgentDefinitionSearchQuery request =
        gatewayService.getLastRequest(AgentDefinitionSearchQuery.class);
    assertThat(request.getFilter().getProcessDefinitionId().get$Eq())
        .as("processDefinitionId filter should be sent as its $eq value")
        .isEqualTo("testProcess");
    assertThat(request.getFilter().getProcessDefinitionKey().get$Eq())
        .as("processDefinitionKey filter should be sent as its string-encoded $eq value")
        .isEqualTo("5000");
    assertThat(request.getFilter().getProcessDefinitionVersion().get$Eq())
        .as("processDefinitionVersion filter should be sent as its $eq value")
        .isEqualTo(2);
    assertThat(request.getFilter().getProcessDefinitionVersionTag().get$Eq())
        .as("processDefinitionVersionTag filter should be sent as its $eq value")
        .isEqualTo("v2");
  }

  @Test
  void shouldSearchAgentDefinitionsWithTenantIdFilter() {
    // when
    client.newAgentDefinitionSearchRequest().filter(f -> f.tenantId("<default>")).send().join();

    // then
    final AgentDefinitionSearchQuery request =
        gatewayService.getLastRequest(AgentDefinitionSearchQuery.class);
    assertThat(request.getFilter().getTenantId().get$Eq())
        .as("tenantId filter should be sent as its $eq value")
        .isEqualTo("<default>");
  }

  @Test
  void shouldSearchAgentDefinitionsWithFullSorting() {
    // when
    client
        .newAgentDefinitionSearchRequest()
        .sort(
            s ->
                s.agentDefinitionKey()
                    .asc()
                    .agentType()
                    .asc()
                    .name()
                    .asc()
                    .elementId()
                    .asc()
                    .processDefinitionId()
                    .asc()
                    .processDefinitionKey()
                    .asc()
                    .processDefinitionVersion()
                    .asc()
                    .processDefinitionVersionTag()
                    .asc()
                    .tenantId()
                    .desc())
        .send()
        .join();

    // then
    final AgentDefinitionSearchQuery request =
        gatewayService.getLastRequest(AgentDefinitionSearchQuery.class);
    assertThat(request.getSort())
        .as("sort fields and orders")
        .extracting(
            AgentDefinitionSearchQuerySortRequest::getField,
            AgentDefinitionSearchQuerySortRequest::getOrder)
        .containsExactly(
            tuple(
                AgentDefinitionSearchQuerySortRequest.FieldEnum.AGENT_DEFINITION_KEY,
                SortOrderEnum.ASC),
            tuple(AgentDefinitionSearchQuerySortRequest.FieldEnum.AGENT_TYPE, SortOrderEnum.ASC),
            tuple(AgentDefinitionSearchQuerySortRequest.FieldEnum.NAME, SortOrderEnum.ASC),
            tuple(AgentDefinitionSearchQuerySortRequest.FieldEnum.ELEMENT_ID, SortOrderEnum.ASC),
            tuple(
                AgentDefinitionSearchQuerySortRequest.FieldEnum.PROCESS_DEFINITION_ID,
                SortOrderEnum.ASC),
            tuple(
                AgentDefinitionSearchQuerySortRequest.FieldEnum.PROCESS_DEFINITION_KEY,
                SortOrderEnum.ASC),
            tuple(
                AgentDefinitionSearchQuerySortRequest.FieldEnum.PROCESS_DEFINITION_VERSION,
                SortOrderEnum.ASC),
            tuple(
                AgentDefinitionSearchQuerySortRequest.FieldEnum.PROCESS_DEFINITION_VERSION_TAG,
                SortOrderEnum.ASC),
            tuple(AgentDefinitionSearchQuerySortRequest.FieldEnum.TENANT_ID, SortOrderEnum.DESC));
  }

  @Test
  void shouldMapSearchAgentDefinitionsResponse() {
    // given
    final AgentDefinitionResult provided =
        Instancio.create(AgentDefinitionResult.class)
            .agentDefinitionKey("84")
            .agentType(AgentDefinitionTypeEnum.EXTERNAL_AGENT)
            .name("My Agent")
            .elementId("agentElement")
            .processDefinitionId("testProcess")
            .processDefinitionKey("100")
            .processDefinitionVersion(1)
            .processDefinitionVersionTag("v1")
            .tenantId("<default>");

    gatewayService.onAgentDefinitionSearchRequest(
        Instancio.create(AgentDefinitionSearchQueryResult.class)
            .page(
                Instancio.create(SearchQueryPageResponse.class)
                    .totalItems(1L)
                    .hasMoreTotalItems(false))
            .items(Collections.singletonList(provided)));

    // when
    final io.camunda.client.api.search.response.SearchResponse<AgentDefinition> result =
        client.newAgentDefinitionSearchRequest().send().join();

    // then
    assertSoftly(
        softly -> {
          softly.assertThat(result.page().totalItems()).as("page.totalItems").isEqualTo(1);
          softly.assertThat(result.items()).as("items").hasSize(1);

          final AgentDefinition item = result.items().get(0);
          softly.assertThat(item.getAgentDefinitionKey()).as("agentDefinitionKey").isEqualTo(84L);
          softly
              .assertThat(item.getAgentType())
              .as("agentType")
              .isEqualTo(AgentDefinitionType.EXTERNAL_AGENT);
          softly.assertThat(item.getName()).as("name").isEqualTo("My Agent");
          softly.assertThat(item.getElementId()).as("elementId").isEqualTo("agentElement");
          softly
              .assertThat(item.getProcessDefinitionId())
              .as("processDefinitionId")
              .isEqualTo("testProcess");
          softly
              .assertThat(item.getProcessDefinitionKey())
              .as("processDefinitionKey")
              .isEqualTo(100L);
          softly
              .assertThat(item.getProcessDefinitionVersion())
              .as("processDefinitionVersion")
              .isEqualTo(1);
          softly
              .assertThat(item.getProcessDefinitionVersionTag())
              .as("processDefinitionVersionTag")
              .isEqualTo("v1");
          softly.assertThat(item.getTenantId()).as("tenantId").isEqualTo("<default>");
        });
  }
}
