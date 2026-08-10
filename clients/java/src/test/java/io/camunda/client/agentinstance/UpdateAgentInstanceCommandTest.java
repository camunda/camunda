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
import io.camunda.client.api.command.AgentInstanceHistoryContent;
import io.camunda.client.api.command.AgentInstanceHistoryMetrics;
import io.camunda.client.api.command.AgentInstanceHistoryToolCall;
import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.AgentTool;
import io.camunda.client.api.command.UpdateAgentInstanceCommandStep1.HistoryItem;
import io.camunda.client.api.response.UpdateAgentInstanceResponse;
import io.camunda.client.api.response.UpdateAgentInstanceResponse.CreatedHistoryItem;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.impl.response.DocumentReferenceResponseImpl;
import io.camunda.client.protocol.rest.AgentInstanceCreatedHistoryItem;
import io.camunda.client.protocol.rest.AgentInstanceDocumentContent;
import io.camunda.client.protocol.rest.AgentInstanceHistoryRoleEnum;
import io.camunda.client.protocol.rest.AgentInstanceTextContent;
import io.camunda.client.protocol.rest.AgentInstanceToolCall;
import io.camunda.client.protocol.rest.AgentInstanceUpdateRequest;
import io.camunda.client.protocol.rest.AgentInstanceUpdateResult;
import io.camunda.client.protocol.rest.AgentInstanceUpdateStatusEnum;
import io.camunda.client.protocol.rest.DocumentReference;
import io.camunda.client.protocol.rest.DocumentReference.CamundaDocumentTypeEnum;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UpdateAgentInstanceCommandTest extends ClientRestTest {

  private static final long AGENT_INSTANCE_KEY = 1234L;
  private static final long ELEMENT_INSTANCE_KEY = 5678L;
  private static final long JOB_KEY = 91011L;
  private static final String JOB_LEASE = "lease-token";
  private static final OffsetDateTime PRODUCED_AT = OffsetDateTime.parse("2025-06-01T12:00:00Z");

  private static HistoryItem historyItem(final String historyItemId) {
    return new HistoryItem()
        .historyItemId(historyItemId)
        .loopIteration(1)
        .role(AgentInstanceHistoryRole.USER)
        .content(Collections.singletonList(AgentInstanceHistoryContent.text("hello")))
        .producedAt(PRODUCED_AT);
  }

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
    assertThat(body.getJobKey()).isNull();
    assertThat(body.getJobLease()).isNull();
    assertThat(body.getHistory()).isNull();
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

  // ── Argument validation: jobKey ───────────────────────────────────────────

  @ParameterizedTest(name = "jobKey={0} should be rejected")
  @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
  void shouldRejectNonPositiveJobKey(final long invalidKey) {
    assertThatThrownBy(
            () ->
                client
                    .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(invalidKey))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("jobKey must be greater than 0");
  }

  // ── Argument validation: jobLease ─────────────────────────────────────────

  @Test
  void shouldRejectNullJobLease() {
    assertThatThrownBy(
            () ->
                client
                    .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobLease(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("jobLease must not be null");
  }

  @ParameterizedTest(name = "jobLease=''{0}'' should be rejected")
  @ValueSource(strings = {"", " "})
  void shouldRejectBlankJobLease(final String jobLease) {
    assertThatThrownBy(
            () ->
                client
                    .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobLease(jobLease))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("jobLease must not be blank");
  }

  // ── Happy-path: history batch ─────────────────────────────────────────────

  @Test
  void shouldSendSingleHistoryItemWithRequiredFieldsOnly() {
    // given
    gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);

    // when
    client
        .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .jobKey(JOB_KEY)
        .history(Collections.singletonList(historyItem("item-1")))
        .execute();

    // then
    final AgentInstanceUpdateRequest body =
        gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
    assertThat(body.getHistory())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.getHistoryItemId()).isEqualTo("item-1");
              assertThat(item.getLoopIteration()).isEqualTo(1);
              assertThat(item.getRole()).isEqualTo(AgentInstanceHistoryRoleEnum.USER);
              assertThat(item.getContent()).hasSize(1);
              assertThat(item.getContent().get(0)).isInstanceOf(AgentInstanceTextContent.class);
              assertThat(((AgentInstanceTextContent) item.getContent().get(0)).getText())
                  .isEqualTo("hello");
              assertThat(item.getProducedAt()).isEqualTo("2025-06-01T12:00Z");
              assertThat(item.getToolCalls()).isNull();
              assertThat(item.getMetrics()).isNull();
            });
  }

  @Test
  void shouldMapHistoryItemWithToolCallsAndMetrics() {
    // given
    gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);
    final HistoryItem item =
        historyItem("item-1")
            .toolCalls(
                Collections.singletonList(
                    new AgentInstanceHistoryToolCall()
                        .toolCallId("call-1")
                        .toolName("search")
                        .elementId("searchTask")
                        .arguments(Collections.<String, Object>singletonMap("query", "weather"))))
            .metrics(
                new AgentInstanceHistoryMetrics()
                    .inputTokens(100L)
                    .outputTokens(50L)
                    .durationMs(200L));

    // when
    client
        .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .jobKey(JOB_KEY)
        .history(Collections.singletonList(item))
        .execute();

    // then
    final AgentInstanceUpdateRequest body =
        gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
    assertThat(body.getHistory()).isNotNull().hasSize(1);
    final io.camunda.client.protocol.rest.AgentInstanceHistoryItem mappedItem =
        body.getHistory().get(0);
    assertThat(mappedItem.getToolCalls())
        .singleElement()
        .satisfies(
            (final AgentInstanceToolCall toolCall) -> {
              assertThat(toolCall.getToolCallId()).isEqualTo("call-1");
              assertThat(toolCall.getToolName()).isEqualTo("search");
              assertThat(toolCall.getElementId()).isEqualTo("searchTask");
              assertThat(toolCall.getArguments()).containsEntry("query", "weather");
            });
    assertThat(mappedItem.getMetrics().getInputTokens()).isEqualTo(100L);
    assertThat(mappedItem.getMetrics().getOutputTokens()).isEqualTo(50L);
    assertThat(mappedItem.getMetrics().getDurationMs()).isEqualTo(200L);
  }

  @Test
  void shouldMapHistoryItemWithDocumentContent() {
    // given
    gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);
    final DocumentReferenceResponseImpl doc =
        new DocumentReferenceResponseImpl(
            new DocumentReference()
                .camundaDocumentType(CamundaDocumentTypeEnum.CAMUNDA)
                .documentId("doc-abc")
                .storeId("store-1"));
    final HistoryItem item =
        historyItem("item-1")
            .content(Collections.singletonList(AgentInstanceHistoryContent.document(doc)));

    // when
    client
        .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .jobKey(JOB_KEY)
        .history(Collections.singletonList(item))
        .execute();

    // then
    final AgentInstanceUpdateRequest body =
        gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
    assertThat(body.getHistory())
        .singleElement()
        .satisfies(
            mappedItem -> {
              assertThat(mappedItem.getContent()).hasSize(1);
              assertThat(mappedItem.getContent().get(0))
                  .isInstanceOf(AgentInstanceDocumentContent.class);
              final AgentInstanceDocumentContent docContent =
                  (AgentInstanceDocumentContent) mappedItem.getContent().get(0);
              assertThat(docContent.getDocumentReference().getCamundaDocumentType())
                  .isEqualTo(CamundaDocumentTypeEnum.CAMUNDA);
              assertThat(docContent.getDocumentReference().getDocumentId()).isEqualTo("doc-abc");
              assertThat(docContent.getDocumentReference().getStoreId()).isEqualTo("store-1");
            });
  }

  @Test
  void shouldSendMultipleHistoryItemsInOrder() {
    // given
    gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);

    // when
    client
        .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .jobKey(JOB_KEY)
        .history(Arrays.asList(historyItem("item-1"), historyItem("item-2"), historyItem("item-3")))
        .execute();

    // then
    final AgentInstanceUpdateRequest body =
        gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
    assertThat(body.getHistory())
        .extracting(io.camunda.client.protocol.rest.AgentInstanceHistoryItem::getHistoryItemId)
        .containsExactly("item-1", "item-2", "item-3");
  }

  @Test
  void shouldCombineJobKeyJobLeaseStatusToolsAndHistoryInOneRequest() {
    // given
    gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);

    // when
    client
        .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .jobKey(JOB_KEY)
        .jobLease(JOB_LEASE)
        .status(AgentInstanceUpdateStatus.THINKING)
        .tools(Collections.singletonList(AgentTool.of("search")))
        .history(Collections.singletonList(historyItem("item-1")))
        .execute();

    // then
    final AgentInstanceUpdateRequest body =
        gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
    assertThat(body.getJobKey()).isEqualTo("91011");
    assertThat(body.getJobLease()).isEqualTo(JOB_LEASE);
    assertThat(body.getStatus()).isEqualTo(AgentInstanceUpdateStatusEnum.THINKING);
    assertThat(body.getTools())
        .singleElement()
        .satisfies(tool -> assertThat(tool.getName()).isEqualTo("search"));
    assertThat(body.getHistory())
        .singleElement()
        .satisfies(item -> assertThat(item.getHistoryItemId()).isEqualTo("item-1"));
  }

  // ── Argument validation: history batch ────────────────────────────────────

  @Test
  void shouldRejectNullHistoryList() {
    assertThatThrownBy(
            () ->
                client
                    .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .history(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("history must not be null");
  }

  @Test
  void shouldRejectNullElementInHistoryList() {
    assertThatThrownBy(
            () ->
                client
                    .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .history(Collections.singletonList(null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("history must not contain null elements");
  }

  @Test
  void shouldRejectNullHistoryItemId() {
    assertThatThrownBy(
            () ->
                client
                    .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .history(Collections.singletonList(historyItem("item-1").historyItemId(null))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("historyItemId must not be null");
  }

  @ParameterizedTest(name = "historyItemId=''{0}'' should be rejected")
  @ValueSource(strings = {"", " "})
  void shouldRejectBlankHistoryItemId(final String historyItemId) {
    assertThatThrownBy(
            () ->
                client
                    .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .history(
                        Collections.singletonList(
                            historyItem("item-1").historyItemId(historyItemId))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("historyItemId must not be blank");
  }

  @ParameterizedTest(name = "loopIteration={0} should be rejected")
  @ValueSource(ints = {0, -1, Integer.MIN_VALUE})
  void shouldRejectNonPositiveLoopIteration(final int loopIteration) {
    assertThatThrownBy(
            () ->
                client
                    .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .history(
                        Collections.singletonList(
                            historyItem("item-1").loopIteration(loopIteration))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("loopIteration must be greater than 0");
  }

  @Test
  void shouldRejectNullRoleInHistoryItem() {
    assertThatThrownBy(
            () ->
                client
                    .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .history(Collections.singletonList(historyItem("item-1").role(null))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("role must not be null");
  }

  @Test
  void shouldRejectNullContentInHistoryItem() {
    assertThatThrownBy(
            () ->
                client
                    .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .history(Collections.singletonList(historyItem("item-1").content(null))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("content must not be null");
  }

  @Test
  void shouldRejectNullProducedAtInHistoryItem() {
    assertThatThrownBy(
            () ->
                client
                    .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .history(Collections.singletonList(historyItem("item-1").producedAt(null))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("producedAt must not be null");
  }

  @Test
  void shouldRejectBlankTextContentInHistoryItem() {
    assertThatThrownBy(
            () ->
                client
                    .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .history(
                        Collections.singletonList(
                            historyItem("item-1")
                                .content(
                                    Collections.singletonList(
                                        AgentInstanceHistoryContent.text(" ")))))
                    .execute())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("text content value must not be null or blank");
  }

  @Test
  void shouldRejectEmptyContentListInHistoryItem() {
    assertThatThrownBy(
            () ->
                client
                    .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .history(
                        Collections.singletonList(
                            historyItem("item-1").content(Collections.emptyList())))
                    .execute())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("content must not be empty");
  }

  @Test
  void shouldRejectBlankToolCallIdInHistoryItem() {
    assertThatThrownBy(
            () ->
                client
                    .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .history(
                        Collections.singletonList(
                            historyItem("item-1")
                                .toolCalls(
                                    Collections.singletonList(
                                        new AgentInstanceHistoryToolCall()
                                            .toolCallId(" ")
                                            .toolName("search")))))
                    .execute())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("toolCallId must not be null or blank");
  }

  // ── Argument validation: jobKey required when history is set ─────────────

  @Test
  void shouldRejectHistoryWithoutJobKey() {
    assertThatThrownBy(
            () ->
                client
                    .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .history(Collections.singletonList(historyItem("item-1")))
                    .execute())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("jobKey must be set when history is not empty");
  }

  // ── Response parsing: createdHistory ──────────────────────────────────────

  @Test
  void shouldParseCreatedHistoryInResponseOrderWithDuplicateFlag() {
    // given
    final AgentInstanceUpdateResult response =
        new AgentInstanceUpdateResult()
            .createdHistory(
                Arrays.asList(
                    new AgentInstanceCreatedHistoryItem()
                        .historyItemId("item-1")
                        .historyItemKey("100")
                        .isDuplicate(false),
                    new AgentInstanceCreatedHistoryItem()
                        .historyItemId("item-2")
                        .historyItemKey("101")
                        .isDuplicate(true),
                    new AgentInstanceCreatedHistoryItem()
                        .historyItemId("item-3")
                        .historyItemKey("102")
                        .isDuplicate(false)));
    gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY, response);

    // when
    final UpdateAgentInstanceResponse result =
        client
            .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
            .elementInstanceKey(ELEMENT_INSTANCE_KEY)
            .execute();

    // then
    assertThat(result.getCreatedHistory())
        .isNotNull()
        .extracting(
            CreatedHistoryItem::getHistoryItemId,
            CreatedHistoryItem::getHistoryItemKey,
            CreatedHistoryItem::isDuplicate)
        .containsExactly(
            tuple("item-1", 100L, false),
            tuple("item-2", 101L, true),
            tuple("item-3", 102L, false));
  }

  @Test
  void shouldReturnEmptyCreatedHistoryWhenResponseHasNoBody() {
    // given
    gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);

    // when
    final UpdateAgentInstanceResponse result =
        client
            .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
            .elementInstanceKey(ELEMENT_INSTANCE_KEY)
            .execute();

    // then
    assertThat(result.getCreatedHistory()).isNotNull().isEmpty();
  }
}
