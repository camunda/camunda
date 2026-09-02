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
import io.camunda.client.api.command.AgentInstanceHistoryItem;
import io.camunda.client.api.command.AgentInstanceHistoryMetrics;
import io.camunda.client.api.command.AgentInstanceHistoryToolCall;
import io.camunda.client.api.command.AgentInstanceLimits;
import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.client.api.command.AgentTool;
import io.camunda.client.api.response.UpdateAgentInstanceResponse;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UpdateAgentInstanceCommandTest extends ClientRestTest {

  private static final long AGENT_INSTANCE_KEY = 1234L;
  private static final long ELEMENT_INSTANCE_KEY = 5678L;
  private static final long JOB_KEY = 91011L;
  private static final String JOB_LEASE = "lease-token";
  private static final OffsetDateTime PRODUCED_AT = OffsetDateTime.parse("2025-06-01T12:00:00Z");

  private static AgentInstanceHistoryItem historyItem(final String historyItemId) {
    return new AgentInstanceHistoryItem()
        .historyItemId(historyItemId)
        .loopIteration(1)
        .role(AgentInstanceHistoryRole.USER)
        .content(Collections.singletonList(AgentInstanceHistoryContent.text("hello")))
        .producedAt(PRODUCED_AT);
  }

  @Nested
  class RequestRoutingTest {

    @Test
    void shouldSendPatchToCorrectUrl() {
      // given
      gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);

      // when
      client
          .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
          .elementInstanceKey(ELEMENT_INSTANCE_KEY)
          .jobKey(JOB_KEY)
          .jobLease(JOB_LEASE)
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
          .jobKey(JOB_KEY)
          .jobLease(JOB_LEASE)
          .execute();

      // then
      final AgentInstanceUpdateRequest body =
          gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
      assertThat(body.getElementInstanceKey()).isEqualTo(String.valueOf(ELEMENT_INSTANCE_KEY));
      assertThat(body.getStatus()).isEqualTo(AgentInstanceUpdateStatusEnum.THINKING);
    }

    @Test
    void shouldSendOnlyRequiredFieldsWhenOptionalFieldsOmitted() {
      // given
      gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);

      // when
      client
          .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
          .elementInstanceKey(ELEMENT_INSTANCE_KEY)
          .jobKey(JOB_KEY)
          .jobLease(JOB_LEASE)
          .execute();

      // then
      final AgentInstanceUpdateRequest body =
          gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
      assertThat(body.getElementInstanceKey()).isEqualTo(String.valueOf(ELEMENT_INSTANCE_KEY));
      assertThat(body.getJobKey()).isEqualTo(String.valueOf(JOB_KEY));
      assertThat(body.getJobLease()).isEqualTo(JOB_LEASE);
      assertThat(body.getStatus()).isNull();
      assertThat(body.getHistory()).isNull();
    }
  }

  @Nested
  class AgentInstanceKeyValidationTest {

    @ParameterizedTest(name = "agentInstanceKey={0} should be rejected")
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void shouldRejectNonPositiveAgentInstanceKey(final long invalidKey) {
      assertThatThrownBy(() -> client.newUpdateAgentInstanceCommand(invalidKey))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("agentInstanceKey must be greater than 0");
    }
  }

  @Nested
  class ElementInstanceKeyValidationTest {

    @ParameterizedTest(name = "elementInstanceKey={0} should be rejected")
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void shouldRejectNonPositiveElementInstanceKey(final long invalidKey) {
      assertThatThrownBy(
              () ->
                  client
                      .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                      .elementInstanceKey(invalidKey))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("elementInstanceKey must be greater than 0");
    }
  }

  @Nested
  class JobKeyValidationTest {

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
  }

  @Nested
  class JobLeaseValidationTest {

    @Test
    void shouldRejectNullJobLease() {
      assertThatThrownBy(
              () ->
                  client
                      .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                      .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                      .jobKey(JOB_KEY)
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
                      .jobKey(JOB_KEY)
                      .jobLease(jobLease))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("jobLease must not be blank");
    }
  }

  @Nested
  class HistoryBatchMappingTest {

    @Test
    void shouldSendSingleHistoryItemWithRequiredFieldsOnly() {
      // given
      gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);

      // when
      client
          .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
          .elementInstanceKey(ELEMENT_INSTANCE_KEY)
          .jobKey(JOB_KEY)
          .jobLease(JOB_LEASE)
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
      final AgentInstanceHistoryItem item =
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
                      .reasoningTokenCount(30L)
                      .cacheCreationTokenCount(40L)
                      .cacheReadTokenCount(60L)
                      .durationMs(200L));

      // when
      client
          .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
          .elementInstanceKey(ELEMENT_INSTANCE_KEY)
          .jobKey(JOB_KEY)
          .jobLease(JOB_LEASE)
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
      assertThat(mappedItem.getMetrics().getReasoningTokenCount()).isEqualTo(30L);
      assertThat(mappedItem.getMetrics().getCacheCreationTokenCount()).isEqualTo(40L);
      assertThat(mappedItem.getMetrics().getCacheReadTokenCount()).isEqualTo(60L);
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
      final AgentInstanceHistoryItem item =
          historyItem("item-1")
              .content(Collections.singletonList(AgentInstanceHistoryContent.document(doc)));

      // when
      client
          .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
          .elementInstanceKey(ELEMENT_INSTANCE_KEY)
          .jobKey(JOB_KEY)
          .jobLease(JOB_LEASE)
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
          .jobLease(JOB_LEASE)
          .history(
              Arrays.asList(historyItem("item-1"), historyItem("item-2"), historyItem("item-3")))
          .execute();

      // then
      final AgentInstanceUpdateRequest body =
          gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
      assertThat(body.getHistory())
          .extracting(io.camunda.client.protocol.rest.AgentInstanceHistoryItem::getHistoryItemId)
          .containsExactly("item-1", "item-2", "item-3");
    }

    @Test
    void shouldCombineJobKeyJobLeaseStatusAndHistoryInOneRequest() {
      // given
      gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);

      // when
      client
          .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
          .elementInstanceKey(ELEMENT_INSTANCE_KEY)
          .status(AgentInstanceUpdateStatus.THINKING)
          .jobKey(JOB_KEY)
          .jobLease(JOB_LEASE)
          .history(Collections.singletonList(historyItem("item-1")))
          .execute();

      // then
      final AgentInstanceUpdateRequest body =
          gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
      assertThat(body.getJobKey()).isEqualTo("91011");
      assertThat(body.getJobLease()).isEqualTo(JOB_LEASE);
      assertThat(body.getStatus()).isEqualTo(AgentInstanceUpdateStatusEnum.THINKING);
      assertThat(body.getHistory())
          .singleElement()
          .satisfies(item -> assertThat(item.getHistoryItemId()).isEqualTo("item-1"));
    }
  }

  @Nested
  class ConfigurationFieldsTest {

    @Test
    void shouldMapHistoryItemToolsWithAllFields() {
      // given
      gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);
      final AgentInstanceHistoryItem item =
          historyItem("item-1")
              .tools(Arrays.asList(AgentTool.of("search", "A web search tool", "searchTask")));

      // when
      client
          .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
          .elementInstanceKey(ELEMENT_INSTANCE_KEY)
          .jobKey(JOB_KEY)
          .jobLease(JOB_LEASE)
          .history(Collections.singletonList(item))
          .execute();

      // then
      final AgentInstanceUpdateRequest body =
          gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
      final io.camunda.client.protocol.rest.AgentInstanceHistoryItem mappedItem =
          body.getHistory().get(0);
      assertThat(mappedItem.getTools())
          .singleElement()
          .satisfies(
              tool -> {
                assertThat(tool.getName()).isEqualTo("search");
                assertThat(tool.getDescription()).isEqualTo("A web search tool");
                assertThat(tool.getElementId()).isEqualTo("searchTask");
              });
    }

    @Test
    void shouldMapHistoryItemToolWithNameOnlyOmittingNullOptionalFields() {
      // given
      gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);
      final AgentInstanceHistoryItem item =
          historyItem("item-1").tools(Arrays.asList(AgentTool.of("summarize")));

      // when
      client
          .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
          .elementInstanceKey(ELEMENT_INSTANCE_KEY)
          .jobKey(JOB_KEY)
          .jobLease(JOB_LEASE)
          .history(Collections.singletonList(item))
          .execute();

      // then
      final AgentInstanceUpdateRequest body =
          gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
      final io.camunda.client.protocol.rest.AgentTool tool =
          body.getHistory().get(0).getTools().get(0);
      assertThat(tool.getName()).isEqualTo("summarize");
      assertThat(tool.getDescription()).isNull();
      assertThat(tool.getElementId()).isNull();
    }

    @Test
    void shouldMapHistoryItemModelAndProvider() {
      // given
      gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);
      final AgentInstanceHistoryItem item = historyItem("item-1").model("gpt-4").provider("openai");

      // when
      client
          .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
          .elementInstanceKey(ELEMENT_INSTANCE_KEY)
          .jobKey(JOB_KEY)
          .jobLease(JOB_LEASE)
          .history(Collections.singletonList(item))
          .execute();

      // then
      final AgentInstanceUpdateRequest body =
          gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
      final io.camunda.client.protocol.rest.AgentInstanceHistoryItem mappedItem =
          body.getHistory().get(0);
      assertThat(mappedItem.getModel()).isEqualTo("gpt-4");
      assertThat(mappedItem.getProvider()).isEqualTo("openai");
    }

    @Test
    void shouldMapHistoryItemLimits() {
      // given
      gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);
      final AgentInstanceHistoryItem item =
          historyItem("item-1").limits(AgentInstanceLimits.of(1000L, 10, 20));

      // when
      client
          .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
          .elementInstanceKey(ELEMENT_INSTANCE_KEY)
          .jobKey(JOB_KEY)
          .jobLease(JOB_LEASE)
          .history(Collections.singletonList(item))
          .execute();

      // then
      final AgentInstanceUpdateRequest body =
          gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
      final io.camunda.client.protocol.rest.AgentInstanceLimits limits =
          body.getHistory().get(0).getLimits();
      assertThat(limits.getMaxTokens()).isEqualTo(1000L);
      assertThat(limits.getMaxModelCalls()).isEqualTo(10);
      assertThat(limits.getMaxToolCalls()).isEqualTo(20);
    }

    @Test
    void shouldRejectHistoryItemLimitsBelowMinimum() {
      assertThatThrownBy(
              () ->
                  client
                      .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                      .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                      .jobKey(JOB_KEY)
                      .jobLease(JOB_LEASE)
                      .history(
                          Collections.singletonList(
                              historyItem("item-1").limits(AgentInstanceLimits.of(-2L, 0, 0)))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("maxTokens must be >= -1");
    }

    @Test
    void shouldRejectHistoryItemMaxModelCallsBelowMinimum() {
      assertThatThrownBy(
              () ->
                  client
                      .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                      .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                      .jobKey(JOB_KEY)
                      .jobLease(JOB_LEASE)
                      .history(
                          Collections.singletonList(
                              historyItem("item-1").limits(AgentInstanceLimits.of(1000L, -2, 0)))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("maxModelCalls must be >= -1");
    }

    @Test
    void shouldRejectHistoryItemMaxToolCallsBelowMinimum() {
      assertThatThrownBy(
              () ->
                  client
                      .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                      .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                      .jobKey(JOB_KEY)
                      .jobLease(JOB_LEASE)
                      .history(
                          Collections.singletonList(
                              historyItem("item-1").limits(AgentInstanceLimits.of(1000L, 10, -2)))))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("maxToolCalls must be >= -1");
    }

    @Test
    void shouldMapHistoryItemSystemPrompt() {
      // given
      gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);
      final AgentInstanceHistoryItem item =
          historyItem("item-1")
              .systemPrompt(Collections.singletonList(AgentInstanceHistoryContent.text("be nice")));

      // when
      client
          .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
          .elementInstanceKey(ELEMENT_INSTANCE_KEY)
          .jobKey(JOB_KEY)
          .jobLease(JOB_LEASE)
          .history(Collections.singletonList(item))
          .execute();

      // then
      final AgentInstanceUpdateRequest body =
          gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
      final io.camunda.client.protocol.rest.AgentInstanceHistoryItem mappedItem =
          body.getHistory().get(0);
      assertThat(mappedItem.getSystemPrompt()).hasSize(1);
      assertThat(mappedItem.getSystemPrompt().get(0)).isInstanceOf(AgentInstanceTextContent.class);
      assertThat(((AgentInstanceTextContent) mappedItem.getSystemPrompt().get(0)).getText())
          .isEqualTo("be nice");
    }

    @Test
    void shouldSendExplicitEmptyToolsAndSystemPromptDistinctFromAbsent() {
      // given
      gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);
      final AgentInstanceHistoryItem item =
          historyItem("item-1")
              .tools(Collections.emptyList())
              .systemPrompt(Collections.emptyList());

      // when
      client
          .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
          .elementInstanceKey(ELEMENT_INSTANCE_KEY)
          .jobKey(JOB_KEY)
          .jobLease(JOB_LEASE)
          .history(Collections.singletonList(item))
          .execute();

      // then
      final AgentInstanceUpdateRequest body =
          gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
      final io.camunda.client.protocol.rest.AgentInstanceHistoryItem mappedItem =
          body.getHistory().get(0);
      assertThat(mappedItem.getTools()).isNotNull().isEmpty();
      assertThat(mappedItem.getSystemPrompt()).isNotNull().isEmpty();
    }

    @Test
    void shouldLeaveConfigurationFieldsNullWhenAbsentFromHistoryItem() {
      // given
      gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);

      // when
      client
          .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
          .elementInstanceKey(ELEMENT_INSTANCE_KEY)
          .jobKey(JOB_KEY)
          .jobLease(JOB_LEASE)
          .history(Collections.singletonList(historyItem("item-1")))
          .execute();

      // then
      final AgentInstanceUpdateRequest body =
          gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
      final io.camunda.client.protocol.rest.AgentInstanceHistoryItem mappedItem =
          body.getHistory().get(0);
      assertThat(mappedItem.getTools()).isNull();
      assertThat(mappedItem.getModel()).isNull();
      assertThat(mappedItem.getProvider()).isNull();
      assertThat(mappedItem.getLimits()).isNull();
      assertThat(mappedItem.getSystemPrompt()).isNull();
    }

    @Test
    void shouldMapAllConfigurationFieldsOnConfigurationHistoryItem() {
      // given
      gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);
      final AgentInstanceHistoryItem item =
          historyItem("item-1")
              .role(AgentInstanceHistoryRole.CONFIGURATION)
              .tools(Arrays.asList(AgentTool.of("search", "A web search tool", "searchTask")))
              .model("gpt-5")
              .provider("openai")
              .limits(AgentInstanceLimits.of(1000L, 10, 20))
              .systemPrompt(Collections.singletonList(AgentInstanceHistoryContent.text("be nice")));

      // when
      client
          .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
          .elementInstanceKey(ELEMENT_INSTANCE_KEY)
          .jobKey(JOB_KEY)
          .jobLease(JOB_LEASE)
          .history(Collections.singletonList(item))
          .execute();

      // then
      final AgentInstanceUpdateRequest body =
          gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
      final io.camunda.client.protocol.rest.AgentInstanceHistoryItem mappedItem =
          body.getHistory().get(0);
      assertThat(mappedItem.getRole()).isEqualTo(AgentInstanceHistoryRoleEnum.CONFIGURATION);
      assertThat(mappedItem.getTools())
          .singleElement()
          .satisfies(tool -> assertThat(tool.getName()).isEqualTo("search"));
      assertThat(mappedItem.getModel()).isEqualTo("gpt-5");
      assertThat(mappedItem.getProvider()).isEqualTo("openai");
      assertThat(mappedItem.getLimits().getMaxTokens()).isEqualTo(1000L);
      assertThat(mappedItem.getLimits().getMaxModelCalls()).isEqualTo(10);
      assertThat(mappedItem.getLimits().getMaxToolCalls()).isEqualTo(20);
      assertThat(mappedItem.getSystemPrompt()).hasSize(1);
      assertThat(((AgentInstanceTextContent) mappedItem.getSystemPrompt().get(0)).getText())
          .isEqualTo("be nice");
    }
  }

  @Nested
  class HistoryBatchValidationTest {

    @Test
    void shouldRejectNullHistoryList() {
      assertThatThrownBy(
              () ->
                  client
                      .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                      .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                      .jobKey(JOB_KEY)
                      .jobLease(JOB_LEASE)
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
                      .jobLease(JOB_LEASE)
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
                      .jobLease(JOB_LEASE)
                      .history(
                          Collections.singletonList(historyItem("item-1").historyItemId(null))))
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
                      .jobLease(JOB_LEASE)
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
                      .jobLease(JOB_LEASE)
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
                      .jobLease(JOB_LEASE)
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
                      .jobLease(JOB_LEASE)
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
                      .jobLease(JOB_LEASE)
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
                      .jobLease(JOB_LEASE)
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
    void shouldAllowEmptyContentListInHistoryItem() {
      // given
      gatewayService.onUpdateAgentInstanceRequest(AGENT_INSTANCE_KEY);
      final AgentInstanceHistoryItem item = historyItem("item-1").content(Collections.emptyList());

      // when
      client
          .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
          .elementInstanceKey(ELEMENT_INSTANCE_KEY)
          .jobKey(JOB_KEY)
          .jobLease(JOB_LEASE)
          .history(Collections.singletonList(item))
          .execute();

      // then
      final AgentInstanceUpdateRequest body =
          gatewayService.getLastRequest(AgentInstanceUpdateRequest.class);
      assertThat(body.getHistory().get(0).getContent()).isEmpty();
    }

    @Test
    void shouldRejectBlankToolCallIdInHistoryItem() {
      assertThatThrownBy(
              () ->
                  client
                      .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                      .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                      .jobKey(JOB_KEY)
                      .jobLease(JOB_LEASE)
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

    @Test
    void shouldRejectNullElementInToolsList() {
      assertThatThrownBy(
              () ->
                  client
                      .newUpdateAgentInstanceCommand(AGENT_INSTANCE_KEY)
                      .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                      .jobKey(JOB_KEY)
                      .jobLease(JOB_LEASE)
                      .history(
                          Collections.singletonList(
                              historyItem("item-1").tools(Collections.singletonList(null))))
                      .execute())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("tools must not contain null elements");
    }
  }

  @Nested
  class ResponseParsingTest {

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
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .execute();

      // then
      assertThat(result.getCreatedHistory())
          .isNotNull()
          .extracting(
              item -> item.getHistoryItemId(),
              item -> item.getHistoryItemKey(),
              item -> item.isDuplicate())
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
              .jobKey(JOB_KEY)
              .jobLease(JOB_LEASE)
              .execute();

      // then
      assertThat(result.getCreatedHistory()).isNotNull().isEmpty();
    }
  }
}
