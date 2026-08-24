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
import io.camunda.client.api.response.AgentInstanceCreatedHistoryItem;
import io.camunda.client.api.response.CreateAgentInstanceResponse;
import io.camunda.client.api.search.enums.AgentInstanceHistoryRole;
import io.camunda.client.protocol.rest.AgentInstanceCreationRequest;
import io.camunda.client.protocol.rest.AgentInstanceCreationResult;
import io.camunda.client.util.ClientRestTest;
import io.camunda.client.util.RestGatewayService;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CreateAgentInstanceCommandTest extends ClientRestTest {

  private static final long ELEMENT_INSTANCE_KEY = 2251799813685248L;
  private static final String MODEL = "gpt-4o";
  private static final String PROVIDER = "openai";
  private static final String SYSTEM_PROMPT = "You are a helpful assistant.";
  private static final long JOB_KEY = 91011L;
  private static final String JOB_LEASE = "lease-token";
  private static final OffsetDateTime PRODUCED_AT = OffsetDateTime.parse("2025-06-01T12:00:00Z");

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
        .jobKey(JOB_KEY)
        .jobLease(JOB_LEASE)
        .history(
            Collections.singletonList(
                new AgentInstanceHistoryItem()
                    .historyItemId("item-0")
                    .loopIteration(1)
                    .role(AgentInstanceHistoryRole.CONFIGURATION)
                    .content(
                        Collections.singletonList(
                            AgentInstanceHistoryContent.text("configuration")))
                    .producedAt(PRODUCED_AT)
                    .model(MODEL)
                    .provider(PROVIDER)
                    .systemPrompt(
                        Collections.singletonList(
                            AgentInstanceHistoryContent.text(SYSTEM_PROMPT)))))
        .execute();

    // then
    final LoggedRequest request = RestGatewayService.getLastRequest();
    assertThat(request.getMethod()).isEqualTo(RequestMethod.POST);
    assertThat(request.getUrl()).isEqualTo("/v2/agent-instances");
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
            .jobKey(JOB_KEY)
            .jobLease(JOB_LEASE)
            .history(
                Collections.singletonList(
                    new AgentInstanceHistoryItem()
                        .historyItemId("item-0")
                        .loopIteration(1)
                        .role(AgentInstanceHistoryRole.CONFIGURATION)
                        .content(
                            Collections.singletonList(
                                AgentInstanceHistoryContent.text("configuration")))
                        .producedAt(PRODUCED_AT)
                        .model(MODEL)
                        .provider(PROVIDER)
                        .systemPrompt(
                            Collections.singletonList(
                                AgentInstanceHistoryContent.text(SYSTEM_PROMPT)))))
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
                    .jobKey(JOB_KEY)
                    .jobLease(JOB_LEASE)
                    .history(
                        Collections.singletonList(
                            new AgentInstanceHistoryItem()
                                .historyItemId("item-0")
                                .loopIteration(1)
                                .role(AgentInstanceHistoryRole.CONFIGURATION)
                                .content(
                                    Collections.singletonList(
                                        AgentInstanceHistoryContent.text("configuration")))
                                .producedAt(PRODUCED_AT)
                                .model(MODEL)
                                .provider(PROVIDER)
                                .systemPrompt(
                                    Collections.singletonList(
                                        AgentInstanceHistoryContent.text(SYSTEM_PROMPT)))))
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
                    .newCreateAgentInstanceCommand()
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
                    .newCreateAgentInstanceCommand()
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
                    .newCreateAgentInstanceCommand()
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .jobLease(jobLease))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("jobLease must not be blank");
  }

  // ── History batch mapping ─────────────────────────────────────────────────

  @Test
  void shouldSendJobKeyJobLeaseAndHistoryInRequestBody() {
    // given
    gatewayService.onCreateAgentInstanceRequest(
        new AgentInstanceCreationResult().agentInstanceKey("1"));

    // when
    client
        .newCreateAgentInstanceCommand()
        .elementInstanceKey(ELEMENT_INSTANCE_KEY)
        .jobKey(JOB_KEY)
        .jobLease(JOB_LEASE)
        .history(
            Arrays.asList(
                new AgentInstanceHistoryItem()
                    .historyItemId("item-0")
                    .loopIteration(1)
                    .role(AgentInstanceHistoryRole.CONFIGURATION)
                    .content(
                        Collections.singletonList(
                            AgentInstanceHistoryContent.text("configuration")))
                    .producedAt(PRODUCED_AT)
                    .model(MODEL)
                    .provider(PROVIDER)
                    .systemPrompt(
                        Collections.singletonList(AgentInstanceHistoryContent.text(SYSTEM_PROMPT))),
                new AgentInstanceHistoryItem()
                    .historyItemId("item-1")
                    .loopIteration(1)
                    .role(AgentInstanceHistoryRole.USER)
                    .content(Collections.singletonList(AgentInstanceHistoryContent.text("hello")))
                    .producedAt(PRODUCED_AT),
                new AgentInstanceHistoryItem()
                    .historyItemId("item-2")
                    .loopIteration(1)
                    .role(AgentInstanceHistoryRole.USER)
                    .content(Collections.singletonList(AgentInstanceHistoryContent.text("hello")))
                    .producedAt(PRODUCED_AT)))
        .execute();

    // then
    final AgentInstanceCreationRequest body =
        gatewayService.getLastRequest(AgentInstanceCreationRequest.class);
    assertThat(body.getElementInstanceKey()).isEqualTo(String.valueOf(ELEMENT_INSTANCE_KEY));
    assertThat(body.getJobKey()).isEqualTo(String.valueOf(JOB_KEY));
    assertThat(body.getJobLease()).isEqualTo(JOB_LEASE);
    assertThat(body.getHistory())
        .extracting(item -> item.getHistoryItemId())
        .containsExactly("item-0", "item-1", "item-2");
  }

  @Test
  void shouldRejectHistoryWithoutConfigurationItemEstablishingDefinition() {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentInstanceCommand()
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .jobLease(JOB_LEASE)
                    .history(
                        Collections.singletonList(
                            new AgentInstanceHistoryItem()
                                .historyItemId("item-1")
                                .loopIteration(1)
                                .role(AgentInstanceHistoryRole.USER)
                                .content(
                                    Collections.singletonList(
                                        AgentInstanceHistoryContent.text("hello")))
                                .producedAt(PRODUCED_AT)))
                    .execute())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "history must include a CONFIGURATION item establishing model, provider, and"
                + " systemPrompt when history is not empty");
  }

  @Test
  void shouldRejectNullHistoryList() {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentInstanceCommand()
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .jobLease(JOB_LEASE)
                    .history(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("history must not be null");
  }

  @Test
  void shouldRejectEmptyHistoryList() {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentInstanceCommand()
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .jobLease(JOB_LEASE)
                    .history(Collections.emptyList())
                    .execute())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("history must not be empty");
  }

  @Test
  void shouldRejectNullElementInHistoryList() {
    assertThatThrownBy(
            () ->
                client
                    .newCreateAgentInstanceCommand()
                    .elementInstanceKey(ELEMENT_INSTANCE_KEY)
                    .jobKey(JOB_KEY)
                    .jobLease(JOB_LEASE)
                    .history(Collections.singletonList(null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("history must not contain null elements");
  }

  // ── Response parsing: createdHistory ──────────────────────────────────────

  @Test
  void shouldParseCreatedHistoryInResponseOrderWithDuplicateFlag() {
    // given
    final AgentInstanceCreationResult response =
        new AgentInstanceCreationResult()
            .agentInstanceKey("1")
            .createdHistory(
                Arrays.asList(
                    new io.camunda.client.protocol.rest.AgentInstanceCreatedHistoryItem()
                        .historyItemId("item-1")
                        .historyItemKey("100")
                        .isDuplicate(false),
                    new io.camunda.client.protocol.rest.AgentInstanceCreatedHistoryItem()
                        .historyItemId("item-2")
                        .historyItemKey("101")
                        .isDuplicate(true)));
    gatewayService.onCreateAgentInstanceRequest(response);

    // when
    final CreateAgentInstanceResponse result =
        client
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(ELEMENT_INSTANCE_KEY)
            .jobKey(JOB_KEY)
            .jobLease(JOB_LEASE)
            .history(
                Collections.singletonList(
                    new AgentInstanceHistoryItem()
                        .historyItemId("item-0")
                        .loopIteration(1)
                        .role(AgentInstanceHistoryRole.CONFIGURATION)
                        .content(
                            Collections.singletonList(
                                AgentInstanceHistoryContent.text("configuration")))
                        .producedAt(PRODUCED_AT)
                        .model(MODEL)
                        .provider(PROVIDER)
                        .systemPrompt(
                            Collections.singletonList(
                                AgentInstanceHistoryContent.text(SYSTEM_PROMPT)))))
            .execute();

    // then
    assertThat(result.getCreatedHistory())
        .extracting(
            AgentInstanceCreatedHistoryItem::getHistoryItemId,
            AgentInstanceCreatedHistoryItem::getHistoryItemKey,
            AgentInstanceCreatedHistoryItem::isDuplicate)
        .containsExactly(tuple("item-1", 100L, false), tuple("item-2", 101L, true));
  }

  @Test
  void shouldReturnEmptyCreatedHistoryWhenResponseHasNoHistory() {
    // given
    gatewayService.onCreateAgentInstanceRequest(
        new AgentInstanceCreationResult().agentInstanceKey("1"));

    // when
    final CreateAgentInstanceResponse result =
        client
            .newCreateAgentInstanceCommand()
            .elementInstanceKey(ELEMENT_INSTANCE_KEY)
            .jobKey(JOB_KEY)
            .jobLease(JOB_LEASE)
            .history(
                Collections.singletonList(
                    new AgentInstanceHistoryItem()
                        .historyItemId("item-0")
                        .loopIteration(1)
                        .role(AgentInstanceHistoryRole.CONFIGURATION)
                        .content(
                            Collections.singletonList(
                                AgentInstanceHistoryContent.text("configuration")))
                        .producedAt(PRODUCED_AT)
                        .model(MODEL)
                        .provider(PROVIDER)
                        .systemPrompt(
                            Collections.singletonList(
                                AgentInstanceHistoryContent.text(SYSTEM_PROMPT)))))
            .execute();

    // then
    assertThat(result.getCreatedHistory()).isNotNull().isEmpty();
  }
}
