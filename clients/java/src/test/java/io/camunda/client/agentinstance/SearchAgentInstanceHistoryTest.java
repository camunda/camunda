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
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryContent.DocumentContent;
import io.camunda.client.api.command.AgentInstanceHistoryContent.ObjectContent;
import io.camunda.client.api.command.AgentInstanceHistoryContent.TextContent;
import io.camunda.client.api.command.AgentInstanceHistoryMetrics;
import io.camunda.client.api.command.AgentInstanceHistoryToolCall;
import io.camunda.client.api.search.enums.AgentInstanceHistoryCommitStatus;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.api.search.response.AgentInstanceHistory;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.client.protocol.rest.AgentInstanceDocumentContent;
import io.camunda.client.protocol.rest.AgentInstanceHistoryCommitStatusEnum;
import io.camunda.client.protocol.rest.AgentInstanceHistoryItemMetrics;
import io.camunda.client.protocol.rest.AgentInstanceHistoryItemResult;
import io.camunda.client.protocol.rest.AgentInstanceHistoryRoleEnum;
import io.camunda.client.protocol.rest.AgentInstanceHistorySearchQuery;
import io.camunda.client.protocol.rest.AgentInstanceHistorySearchQueryResult;
import io.camunda.client.protocol.rest.AgentInstanceHistorySearchQuerySortRequest;
import io.camunda.client.protocol.rest.AgentInstanceMessageContent;
import io.camunda.client.protocol.rest.AgentInstanceObjectContent;
import io.camunda.client.protocol.rest.AgentInstanceTextContent;
import io.camunda.client.protocol.rest.AgentInstanceToolCall;
import io.camunda.client.protocol.rest.DocumentReference;
import io.camunda.client.protocol.rest.SearchQueryPageResponse;
import io.camunda.client.protocol.rest.SortOrderEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;

class SearchAgentInstanceHistoryTest extends ClientRestTest {

  private static final long AGENT_INSTANCE_KEY = 42L;

  @Test
  void shouldSearchAgentInstanceHistory() {
    // when
    client.newAgentInstanceHistorySearchRequest(AGENT_INSTANCE_KEY).send().join();

    // then
    final LoggedRequest restRequest = RestGatewayService.getLastRequest();
    assertThat(restRequest.getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(restRequest.getUrl()).isEqualTo("/v2/agent-instances/42/history/search");
    final AgentInstanceHistorySearchQuery request =
        gatewayService.getLastRequest(AgentInstanceHistorySearchQuery.class);
    assertThat(request.getFilter()).isNull();
  }

  @Test
  void shouldSearchWithRoleFilter() {
    // when
    client
        .newAgentInstanceHistorySearchRequest(AGENT_INSTANCE_KEY)
        .filter(f -> f.role(AgentInstanceHistoryRole.USER))
        .send()
        .join();

    // then
    final AgentInstanceHistorySearchQuery request =
        gatewayService.getLastRequest(AgentInstanceHistorySearchQuery.class);
    assertThat(request.getFilter()).isNotNull();
    assertThat(request.getFilter().getRole().get$Eq()).isEqualTo(AgentInstanceHistoryRoleEnum.USER);
  }

  @Test
  void shouldSearchWithHistoryItemKeyFilter() {
    // when
    client
        .newAgentInstanceHistorySearchRequest(AGENT_INSTANCE_KEY)
        .filter(f -> f.historyItemKey(100L))
        .send()
        .join();

    // then
    final AgentInstanceHistorySearchQuery request =
        gatewayService.getLastRequest(AgentInstanceHistorySearchQuery.class);
    assertThat(request.getFilter().getHistoryItemKey().get$Eq()).isEqualTo("100");
  }

  @Test
  void shouldSearchWithElementInstanceKeyFilter() {
    // when
    client
        .newAgentInstanceHistorySearchRequest(AGENT_INSTANCE_KEY)
        .filter(f -> f.elementInstanceKey(200L))
        .send()
        .join();

    // then
    final AgentInstanceHistorySearchQuery request =
        gatewayService.getLastRequest(AgentInstanceHistorySearchQuery.class);
    assertThat(request.getFilter().getElementInstanceKey().get$Eq()).isEqualTo("200");
  }

  @Test
  void shouldSearchWithJobKeyFilter() {
    // when
    client
        .newAgentInstanceHistorySearchRequest(AGENT_INSTANCE_KEY)
        .filter(f -> f.jobKey(300L))
        .send()
        .join();

    // then
    final AgentInstanceHistorySearchQuery request =
        gatewayService.getLastRequest(AgentInstanceHistorySearchQuery.class);
    assertThat(request.getFilter().getJobKey().get$Eq()).isEqualTo("300");
  }

  @Test
  void shouldSearchWithCommitStatusFilter() {
    // when
    client
        .newAgentInstanceHistorySearchRequest(AGENT_INSTANCE_KEY)
        .filter(f -> f.commitStatus(AgentInstanceHistoryCommitStatus.COMMITTED))
        .send()
        .join();

    // then
    final AgentInstanceHistorySearchQuery request =
        gatewayService.getLastRequest(AgentInstanceHistorySearchQuery.class);
    assertThat(request.getFilter().getCommitStatus().get$Eq())
        .isEqualTo(AgentInstanceHistoryCommitStatusEnum.COMMITTED);
  }

  @Test
  void shouldSearchWithLoopIterationFilter() {
    // when
    client
        .newAgentInstanceHistorySearchRequest(AGENT_INSTANCE_KEY)
        .filter(f -> f.loopIteration(1))
        .send()
        .join();

    // then
    final AgentInstanceHistorySearchQuery request =
        gatewayService.getLastRequest(AgentInstanceHistorySearchQuery.class);
    assertThat(request.getFilter().getLoopIteration().get$Eq()).isEqualTo(1);
  }

  @Test
  void shouldSearchWithProducedAtFilter() {
    // given
    final OffsetDateTime from = OffsetDateTime.parse("2025-01-01T00:00:00Z");
    final OffsetDateTime to = OffsetDateTime.parse("2025-12-31T23:59:59Z");

    // when
    client
        .newAgentInstanceHistorySearchRequest(AGENT_INSTANCE_KEY)
        .filter(f -> f.producedAt(p -> p.gt(from).lt(to)))
        .send()
        .join();

    // then
    final AgentInstanceHistorySearchQuery request =
        gatewayService.getLastRequest(AgentInstanceHistorySearchQuery.class);
    assertThat(request.getFilter().getProducedAt().get$Gt()).isEqualTo(from.toString());
    assertThat(request.getFilter().getProducedAt().get$Lt()).isEqualTo(to.toString());
  }

  @Test
  void shouldSearchWithLoopIterationSort() {
    // when
    client
        .newAgentInstanceHistorySearchRequest(AGENT_INSTANCE_KEY)
        .sort(s -> s.loopIteration().asc())
        .send()
        .join();

    // then
    final AgentInstanceHistorySearchQuery request =
        gatewayService.getLastRequest(AgentInstanceHistorySearchQuery.class);
    assertThat(request.getSort())
        .as("sort fields and orders")
        .extracting(
            AgentInstanceHistorySearchQuerySortRequest::getField,
            AgentInstanceHistorySearchQuerySortRequest::getOrder)
        .containsExactly(
            tuple(
                AgentInstanceHistorySearchQuerySortRequest.FieldEnum.LOOP_ITERATION,
                SortOrderEnum.ASC));
  }

  @Test
  void shouldSearchWithPage() {
    // when
    client
        .newAgentInstanceHistorySearchRequest(AGENT_INSTANCE_KEY)
        .page(p -> p.limit(10).from(5))
        .send()
        .join();

    // then
    final AgentInstanceHistorySearchQuery request =
        gatewayService.getLastRequest(AgentInstanceHistorySearchQuery.class);
    assertThat(request.getPage()).isNotNull();
    assertThat(request.getPage().getLimit()).isEqualTo(10);
    assertThat(request.getPage().getFrom()).isEqualTo(5);
  }

  @Test
  void shouldSearchWithFullSorting() {
    // when
    client
        .newAgentInstanceHistorySearchRequest(AGENT_INSTANCE_KEY)
        .sort(s -> s.producedAt().asc().historyItemKey().desc())
        .send()
        .join();

    // then
    final AgentInstanceHistorySearchQuery request =
        gatewayService.getLastRequest(AgentInstanceHistorySearchQuery.class);
    assertThat(request.getSort())
        .as("sort fields and orders")
        .extracting(
            AgentInstanceHistorySearchQuerySortRequest::getField,
            AgentInstanceHistorySearchQuerySortRequest::getOrder)
        .containsExactly(
            tuple(
                AgentInstanceHistorySearchQuerySortRequest.FieldEnum.PRODUCED_AT,
                SortOrderEnum.ASC),
            tuple(
                AgentInstanceHistorySearchQuerySortRequest.FieldEnum.HISTORY_ITEM_KEY,
                SortOrderEnum.DESC));
  }

  @Test
  void shouldMapSearchResponse() {
    // given
    final OffsetDateTime now = OffsetDateTime.now();
    final AgentInstanceHistoryItemResult provided =
        Instancio.create(AgentInstanceHistoryItemResult.class)
            .historyItemKey("100")
            .agentInstanceKey("42")
            .elementInstanceKey("200")
            .jobKey("300")
            .jobLease("lease-abc")
            .loopIteration(3)
            .role(AgentInstanceHistoryRoleEnum.USER)
            .commitStatus(AgentInstanceHistoryCommitStatusEnum.COMMITTED)
            .producedAt(now.toString())
            .metrics(
                new AgentInstanceHistoryItemMetrics()
                    .inputTokens(10L)
                    .outputTokens(20L)
                    .durationMs(100L))
            .toolCalls(
                Collections.singletonList(
                    new AgentInstanceToolCall()
                        .toolCallId("tc1")
                        .toolName("search")
                        .elementId("searchTask")
                        .arguments(Collections.<String, Object>singletonMap("query", "camunda"))))
            .content(
                Collections.singletonList(
                    (AgentInstanceMessageContent)
                        new AgentInstanceTextContent().contentType("TEXT").text("hello")));

    gatewayService.onAgentInstanceHistorySearchRequest(
        AGENT_INSTANCE_KEY,
        Instancio.create(AgentInstanceHistorySearchQueryResult.class)
            .page(
                Instancio.create(SearchQueryPageResponse.class)
                    .totalItems(1L)
                    .hasMoreTotalItems(false))
            .items(Collections.singletonList(provided)));

    // when
    final SearchResponse<AgentInstanceHistory> result =
        client.newAgentInstanceHistorySearchRequest(AGENT_INSTANCE_KEY).send().join();

    // then
    assertSoftly(
        softly -> {
          softly.assertThat(result.page().totalItems()).isEqualTo(1);
          softly.assertThat(result.items()).hasSize(1);

          final AgentInstanceHistory item = result.items().get(0);
          softly.assertThat(item.getHistoryItemKey()).as("historyItemKey").isEqualTo(100L);
          softly.assertThat(item.getAgentInstanceKey()).as("agentInstanceKey").isEqualTo(42L);
          softly.assertThat(item.getElementInstanceKey()).as("elementInstanceKey").isEqualTo(200L);
          softly.assertThat(item.getJobKey()).as("jobKey").isEqualTo(300L);
          softly.assertThat(item.getJobLease()).as("jobLease").isEqualTo("lease-abc");
          softly.assertThat(item.getLoopIteration()).as("loopIteration").isEqualTo(3);
          softly.assertThat(item.getRole()).as("role").isEqualTo(AgentInstanceHistoryRole.USER);
          softly
              .assertThat(item.getCommitStatus())
              .as("commitStatus")
              .isEqualTo(AgentInstanceHistoryCommitStatus.COMMITTED);
          softly.assertThat(item.getProducedAt()).as("producedAt").isEqualTo(now);

          final AgentInstanceHistoryMetrics metrics = item.getMetrics();
          softly.assertThat(metrics.getInputTokens()).as("inputTokens").isEqualTo(10L);
          softly.assertThat(metrics.getOutputTokens()).as("outputTokens").isEqualTo(20L);
          softly.assertThat(metrics.getDurationMs()).as("durationMs").isEqualTo(100L);

          softly.assertThat(item.getToolCalls()).as("toolCalls").hasSize(1);
          final AgentInstanceHistoryToolCall toolCall = item.getToolCalls().get(0);
          softly.assertThat(toolCall.getToolCallId()).as("toolCallId").isEqualTo("tc1");
          softly.assertThat(toolCall.getToolName()).as("toolName").isEqualTo("search");
          softly.assertThat(toolCall.getElementId()).as("elementId").isEqualTo("searchTask");
          softly
              .assertThat(toolCall.getArguments())
              .as("arguments")
              .containsEntry("query", "camunda");

          softly.assertThat(item.getContent()).as("content").hasSize(1);
          final AgentInstanceHistoryContent content = item.getContent().get(0);
          softly.assertThat(content.getContentType()).as("contentType").isEqualTo("TEXT");
          softly.assertThat(content).as("content is TextContent").isInstanceOf(TextContent.class);
          softly.assertThat(((TextContent) content).getText()).as("text").isEqualTo("hello");
        });
  }

  @Test
  void shouldMapDocumentContent() {
    // given
    final DocumentReference ref = new DocumentReference().documentId("doc-1").storeId("store-a");
    gatewayService.onAgentInstanceHistorySearchRequest(
        AGENT_INSTANCE_KEY,
        Instancio.create(AgentInstanceHistorySearchQueryResult.class)
            .items(
                Collections.singletonList(
                    Instancio.create(AgentInstanceHistoryItemResult.class)
                        .historyItemKey("1")
                        .agentInstanceKey("1")
                        .elementInstanceKey("1")
                        .jobKey("1")
                        .producedAt(null)
                        .content(
                            Collections.singletonList(
                                (AgentInstanceMessageContent)
                                    new AgentInstanceDocumentContent()
                                        .contentType("DOCUMENT")
                                        .documentReference(ref))))));

    // when
    final SearchResponse<AgentInstanceHistory> result =
        client.newAgentInstanceHistorySearchRequest(AGENT_INSTANCE_KEY).send().join();

    // then
    final AgentInstanceHistoryContent content = result.items().get(0).getContent().get(0);
    assertThat(content).isInstanceOf(DocumentContent.class);
    final DocumentContent docContent = (DocumentContent) content;
    assertThat(docContent.getDocumentReference().getDocumentId()).isEqualTo("doc-1");
    assertThat(docContent.getDocumentReference().getStoreId()).isEqualTo("store-a");
  }

  @Test
  void shouldMapObjectContent() {
    // given
    final Map<String, Object> obj = Collections.<String, Object>singletonMap("key", "value");
    gatewayService.onAgentInstanceHistorySearchRequest(
        AGENT_INSTANCE_KEY,
        Instancio.create(AgentInstanceHistorySearchQueryResult.class)
            .items(
                Collections.singletonList(
                    Instancio.create(AgentInstanceHistoryItemResult.class)
                        .historyItemKey("1")
                        .agentInstanceKey("1")
                        .elementInstanceKey("1")
                        .jobKey("1")
                        .producedAt(null)
                        .content(
                            Collections.singletonList(
                                (AgentInstanceMessageContent)
                                    new AgentInstanceObjectContent()
                                        .contentType("OBJECT")
                                        ._object(obj))))));

    // when
    final SearchResponse<AgentInstanceHistory> result =
        client.newAgentInstanceHistorySearchRequest(AGENT_INSTANCE_KEY).send().join();

    // then
    final AgentInstanceHistoryContent content = result.items().get(0).getContent().get(0);
    assertThat(content).isInstanceOf(ObjectContent.class);
    assertThat(((ObjectContent) content).getObject()).isEqualTo(obj);
  }

  @Test
  void shouldMapArrayObjectContent() {
    // given — OBJECT content with a JSON array value
    final List<Integer> arr = Arrays.asList(10, 20, 30);
    gatewayService.onAgentInstanceHistorySearchRequest(
        AGENT_INSTANCE_KEY,
        Instancio.create(AgentInstanceHistorySearchQueryResult.class)
            .items(
                Collections.singletonList(
                    Instancio.create(AgentInstanceHistoryItemResult.class)
                        .historyItemKey("1")
                        .agentInstanceKey("1")
                        .elementInstanceKey("1")
                        .jobKey("1")
                        .producedAt(null)
                        .content(
                            Collections.singletonList(
                                (AgentInstanceMessageContent)
                                    new AgentInstanceObjectContent()
                                        .contentType("OBJECT")
                                        ._object(arr))))));

    // when
    final SearchResponse<AgentInstanceHistory> result =
        client.newAgentInstanceHistorySearchRequest(AGENT_INSTANCE_KEY).send().join();

    // then
    assertThat(result.items())
        .first()
        .satisfies(
            item ->
                assertThat(item.getContent())
                    .first()
                    .satisfies(
                        c -> {
                          assertThat(c).isInstanceOf(ObjectContent.class);
                          assertThat(((ObjectContent) c).getObject()).isEqualTo(arr);
                        }));
  }

  @Test
  void shouldMapScalarObjectContent() {
    // given — OBJECT content with a scalar integer value
    gatewayService.onAgentInstanceHistorySearchRequest(
        AGENT_INSTANCE_KEY,
        Instancio.create(AgentInstanceHistorySearchQueryResult.class)
            .items(
                Collections.singletonList(
                    Instancio.create(AgentInstanceHistoryItemResult.class)
                        .historyItemKey("1")
                        .agentInstanceKey("1")
                        .elementInstanceKey("1")
                        .jobKey("1")
                        .producedAt(null)
                        .content(
                            Collections.singletonList(
                                (AgentInstanceMessageContent)
                                    new AgentInstanceObjectContent()
                                        .contentType("OBJECT")
                                        ._object(42))))));

    // when
    final SearchResponse<AgentInstanceHistory> result =
        client.newAgentInstanceHistorySearchRequest(AGENT_INSTANCE_KEY).send().join();

    // then
    assertThat(result.items())
        .first()
        .satisfies(
            item ->
                assertThat(item.getContent())
                    .first()
                    .satisfies(
                        c -> {
                          assertThat(c).isInstanceOf(ObjectContent.class);
                          assertThat(((ObjectContent) c).getObject()).isEqualTo(42);
                        }));
  }
}
