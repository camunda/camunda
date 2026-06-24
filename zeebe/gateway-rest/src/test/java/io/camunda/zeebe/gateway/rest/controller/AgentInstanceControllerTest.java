/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.AgentHistoryServices;
import io.camunda.service.AgentInstanceServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(AgentInstanceController.class)
class AgentInstanceControllerTest extends RestControllerTest {

  private static final String AGENT_INSTANCES_URL = "/v2/agent-instances";
  private static final long ELEMENT_INSTANCE_KEY = 2251799813685248L;
  private static final long AGENT_INSTANCE_KEY = 9007199254741017L;
  private static final long JOB_KEY = 2251799813685249L;
  private static final long HISTORY_ITEM_KEY = 9007199254741018L;

  @MockitoBean private AgentInstanceServices agentInstanceServices;
  @MockitoBean private AgentHistoryServices agentHistoryServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean private ServiceRegistry serviceRegistry;

  @BeforeEach
  void setUp() {
    when(serviceRegistry.agentInstanceServices(any())).thenReturn(agentInstanceServices);
    when(serviceRegistry.agentHistoryServices(any())).thenReturn(agentHistoryServices);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void shouldCreateAgentInstance() {
    // given
    final var responseRecord = new AgentInstanceRecord();
    responseRecord.setAgentInstanceKey(AGENT_INSTANCE_KEY);
    when(agentInstanceServices.createAgentInstance(any(AgentInstanceRecord.class), any()))
        .thenReturn(CompletableFuture.completedFuture(responseRecord));

    final var requestBody =
        """
        {
          "elementInstanceKey": "%d",
          "definition": {
            "model": "gpt-4o",
            "provider": "openai",
            "systemPrompt": "You are a helpful assistant."
          }
        }
        """
            .formatted(ELEMENT_INSTANCE_KEY);

    // when / then
    webClient
        .post()
        .uri(AGENT_INSTANCES_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            { "agentInstanceKey": "%d" }
            """
                .formatted(AGENT_INSTANCE_KEY),
            JsonCompareMode.STRICT);

    verify(agentInstanceServices)
        .createAgentInstance(
            assertArg(
                record -> {
                  assertThat(record.getElementInstanceKey()).isEqualTo(ELEMENT_INSTANCE_KEY);
                  assertThat(record.getDefinition().getModel()).isEqualTo("gpt-4o");
                  assertThat(record.getDefinition().getProvider()).isEqualTo("openai");
                  assertThat(record.getDefinition().getSystemPrompt())
                      .isEqualTo("You are a helpful assistant.");
                  assertThat(record.getLimits().getMaxTokens()).isEqualTo(-1L);
                  assertThat(record.getLimits().getMaxModelCalls()).isEqualTo(-1);
                  assertThat(record.getLimits().getMaxToolCalls()).isEqualTo(-1);
                }),
            any());
  }

  @Test
  void shouldCreateAgentInstanceWithExplicitLimits() {
    // given
    final var responseRecord = new AgentInstanceRecord();
    responseRecord.setAgentInstanceKey(AGENT_INSTANCE_KEY);
    when(agentInstanceServices.createAgentInstance(any(AgentInstanceRecord.class), any()))
        .thenReturn(CompletableFuture.completedFuture(responseRecord));

    final var requestBody =
        """
        {
          "elementInstanceKey": "%d",
          "definition": {
            "model": "claude-sonnet-4-6",
            "provider": "anthropic",
            "systemPrompt": "You are an expert."
          },
          "limits": {
            "maxTokens": 100000,
            "maxModelCalls": 10,
            "maxToolCalls": 50
          }
        }
        """
            .formatted(ELEMENT_INSTANCE_KEY);

    // when / then
    webClient
        .post()
        .uri(AGENT_INSTANCES_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isOk();

    verify(agentInstanceServices)
        .createAgentInstance(
            assertArg(
                record -> {
                  assertThat(record.getLimits().getMaxTokens()).isEqualTo(100_000L);
                  assertThat(record.getLimits().getMaxModelCalls()).isEqualTo(10);
                  assertThat(record.getLimits().getMaxToolCalls()).isEqualTo(50);
                }),
            any());
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("invalidCreateRequests")
  void shouldRejectInvalidCreateRequest(final String requestBody, final String expectedDetail) {
    // when / then
    webClient
        .post()
        .uri(AGENT_INSTANCES_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "%s",
              "instance": "/v2/agent-instances"
            }
            """
                .formatted(expectedDetail),
            JsonCompareMode.STRICT);

    verifyNoInteractions(agentInstanceServices);
  }

  static Stream<Arguments> invalidCreateRequests() {
    return Stream.of(
        Arguments.of(
            named(
                "missing elementInstanceKey",
                """
                {
                  "definition": {
                    "model": "gpt-4o",
                    "provider": "openai",
                    "systemPrompt": "prompt"
                  }
                }
                """),
            "No elementInstanceKey provided."),
        Arguments.of(
            named(
                "null elementInstanceKey",
                """
                {
                  "elementInstanceKey": null,
                  "definition": {
                    "model": "gpt-4o",
                    "provider": "openai",
                    "systemPrompt": "prompt"
                  }
                }
                """),
            "No elementInstanceKey provided."),
        Arguments.of(
            named(
                "non-numeric elementInstanceKey",
                """
                {
                  "elementInstanceKey": "not-a-number",
                  "definition": {
                    "model": "gpt-4o",
                    "provider": "openai",
                    "systemPrompt": "prompt"
                  }
                }
                """),
            "The provided elementInstanceKey 'not-a-number' is not a valid key."
                + " Expected a numeric value."
                + " Did you pass an entity id instead of an entity key?."),
        Arguments.of(
            named(
                "zero elementInstanceKey",
                """
                {
                  "elementInstanceKey": "0",
                  "definition": {
                    "model": "gpt-4o",
                    "provider": "openai",
                    "systemPrompt": "prompt"
                  }
                }
                """),
            "The value for elementInstanceKey is '0' but must be > 0."),
        Arguments.of(
            named(
                "negative elementInstanceKey",
                """
                {
                  "elementInstanceKey": "-1",
                  "definition": {
                    "model": "gpt-4o",
                    "provider": "openai",
                    "systemPrompt": "prompt"
                  }
                }
                """),
            "The value for elementInstanceKey is '-1' but must be > 0."),
        Arguments.of(
            named(
                "missing definition",
                """
                {
                  "elementInstanceKey": "%d"
                }
                """
                    .formatted(ELEMENT_INSTANCE_KEY)),
            "No definition provided."),
        Arguments.of(
            named(
                "missing definition.model",
                """
                {
                  "elementInstanceKey": "%d",
                  "definition": {
                    "provider": "openai",
                    "systemPrompt": "prompt"
                  }
                }
                """
                    .formatted(ELEMENT_INSTANCE_KEY)),
            "No definition.model provided."),
        Arguments.of(
            named(
                "missing definition.provider",
                """
                {
                  "elementInstanceKey": "%d",
                  "definition": {
                    "model": "gpt-4o",
                    "systemPrompt": "prompt"
                  }
                }
                """
                    .formatted(ELEMENT_INSTANCE_KEY)),
            "No definition.provider provided."),
        Arguments.of(
            named(
                "missing definition.systemPrompt",
                """
                {
                  "elementInstanceKey": "%d",
                  "definition": {
                    "model": "gpt-4o",
                    "provider": "openai"
                  }
                }
                """
                    .formatted(ELEMENT_INSTANCE_KEY)),
            "No definition.systemPrompt provided."));
  }

  @Test
  void shouldReturn5xxOnServiceError() {
    // given
    when(agentInstanceServices.createAgentInstance(any(AgentInstanceRecord.class), any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));

    final var requestBody =
        """
        {
          "elementInstanceKey": "%d",
          "definition": {
            "model": "gpt-4o",
            "provider": "openai",
            "systemPrompt": "prompt"
          }
        }
        """
            .formatted(ELEMENT_INSTANCE_KEY);

    // when / then
    webClient
        .post()
        .uri(AGENT_INSTANCES_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .is5xxServerError();
  }

  @Test
  void shouldUpdateAgentInstanceWithStatus() {
    // given
    when(agentInstanceServices.updateAgentInstance(any(AgentInstanceRecord.class), any()))
        .thenReturn(CompletableFuture.completedFuture(new AgentInstanceRecord()));

    final var requestBody =
        """
        {
          "elementInstanceKey": "%d",
          "status": "THINKING"
        }
        """
            .formatted(ELEMENT_INSTANCE_KEY);

    // when / then
    webClient
        .patch()
        .uri(AGENT_INSTANCES_URL + "/%d".formatted(AGENT_INSTANCE_KEY))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(agentInstanceServices)
        .updateAgentInstance(
            assertArg(
                record -> {
                  assertThat(record.getAgentInstanceKey()).isEqualTo(AGENT_INSTANCE_KEY);
                  assertThat(record.getElementInstanceKey()).isEqualTo(ELEMENT_INSTANCE_KEY);
                  assertThat(record.getStatus().name()).isEqualTo("THINKING");
                  assertThat(record.getChangedAttributes()).containsExactly("status");
                }),
            any());
  }

  @Test
  void shouldUpdateAgentInstanceWithMetrics() {
    // given
    when(agentInstanceServices.updateAgentInstance(any(AgentInstanceRecord.class), any()))
        .thenReturn(CompletableFuture.completedFuture(new AgentInstanceRecord()));

    final var requestBody =
        """
        {
          "elementInstanceKey": "%d",
          "metrics": {
            "inputTokens": 1000,
            "outputTokens": 500,
            "modelCalls": 3,
            "toolCalls": 7
          }
        }
        """
            .formatted(ELEMENT_INSTANCE_KEY);

    // when / then
    webClient
        .patch()
        .uri(AGENT_INSTANCES_URL + "/%d".formatted(AGENT_INSTANCE_KEY))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(agentInstanceServices)
        .updateAgentInstance(
            assertArg(
                record -> {
                  assertThat(record.getMetrics().getInputTokens()).isEqualTo(1000L);
                  assertThat(record.getMetrics().getOutputTokens()).isEqualTo(500L);
                  assertThat(record.getMetrics().getModelCalls()).isEqualTo(3);
                  assertThat(record.getMetrics().getToolCalls()).isEqualTo(7);
                  assertThat(record.getChangedAttributes()).containsExactly("metrics");
                }),
            any());
  }

  @Test
  void shouldUpdateAgentInstanceWithTools() {
    // given
    when(agentInstanceServices.updateAgentInstance(any(AgentInstanceRecord.class), any()))
        .thenReturn(CompletableFuture.completedFuture(new AgentInstanceRecord()));

    final var requestBody =
        """
        {
          "elementInstanceKey": "%d",
          "tools": [
            {
              "name": "searchDatabase",
              "description": "Searches the database",
              "elementId": "searchTask"
            }
          ]
        }
        """
            .formatted(ELEMENT_INSTANCE_KEY);

    // when / then
    webClient
        .patch()
        .uri(AGENT_INSTANCES_URL + "/%d".formatted(AGENT_INSTANCE_KEY))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(agentInstanceServices)
        .updateAgentInstance(
            assertArg(
                record -> {
                  assertThat(record.getTools()).hasSize(1);
                  assertThat(record.getTools().get(0).getName()).isEqualTo("searchDatabase");
                  assertThat(record.getTools().get(0).getDescription())
                      .isEqualTo("Searches the database");
                  assertThat(record.getTools().get(0).getElementId()).isEqualTo("searchTask");
                  assertThat(record.getChangedAttributes()).containsExactly("tools");
                }),
            any());
  }

  @Test
  void shouldUpdateAgentInstanceWithEmptyToolsList() {
    // given
    when(agentInstanceServices.updateAgentInstance(any(AgentInstanceRecord.class), any()))
        .thenReturn(CompletableFuture.completedFuture(new AgentInstanceRecord()));

    final var requestBody =
        """
        {
          "elementInstanceKey": "%d",
          "tools": []
        }
        """
            .formatted(ELEMENT_INSTANCE_KEY);

    // when / then
    webClient
        .patch()
        .uri(AGENT_INSTANCES_URL + "/%d".formatted(AGENT_INSTANCE_KEY))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(agentInstanceServices)
        .updateAgentInstance(
            assertArg(
                record -> {
                  assertThat(record.getTools()).isEmpty();
                  assertThat(record.getChangedAttributes()).containsExactly("tools");
                }),
            any());
  }

  @Test
  void shouldUpdateAgentInstanceWithOnlyElementInstanceKey() {
    // given
    when(agentInstanceServices.updateAgentInstance(any(AgentInstanceRecord.class), any()))
        .thenReturn(CompletableFuture.completedFuture(new AgentInstanceRecord()));

    final var requestBody =
        """
        {
          "elementInstanceKey": "%d"
        }
        """
            .formatted(ELEMENT_INSTANCE_KEY);

    // when / then
    webClient
        .patch()
        .uri(AGENT_INSTANCES_URL + "/%d".formatted(AGENT_INSTANCE_KEY))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isNoContent();

    verify(agentInstanceServices)
        .updateAgentInstance(
            assertArg(
                record -> {
                  assertThat(record.getChangedAttributes()).isEmpty();
                }),
            any());
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("invalidUpdateRequests")
  void shouldRejectInvalidUpdateRequest(final UpdateRequest update, final String expectedDetail) {
    // when / then
    webClient
        .patch()
        .uri(AGENT_INSTANCES_URL + "/%d".formatted(update.agentInstanceKey()))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(update.requestBody())
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "%s",
              "instance": "/v2/agent-instances/%d"
            }
            """
                .formatted(expectedDetail, update.agentInstanceKey()),
            JsonCompareMode.STRICT);

    verifyNoInteractions(agentInstanceServices);
  }

  static Stream<Arguments> invalidUpdateRequests() {
    return Stream.of(
        Arguments.of(
            named(
                "missing elementInstanceKey",
                new UpdateRequest(
                    AGENT_INSTANCE_KEY,
                    """
                    { "status": "THINKING" }
                    """)),
            "No elementInstanceKey provided."),
        Arguments.of(
            named(
                "null elementInstanceKey",
                new UpdateRequest(
                    AGENT_INSTANCE_KEY,
                    """
                    { "elementInstanceKey": null, "status": "THINKING" }
                    """)),
            "No elementInstanceKey provided."),
        Arguments.of(
            named(
                "non-numeric elementInstanceKey",
                new UpdateRequest(
                    AGENT_INSTANCE_KEY,
                    """
                    { "elementInstanceKey": "not-a-number", "status": "THINKING" }
                    """)),
            "The provided elementInstanceKey 'not-a-number' is not a valid key."
                + " Expected a numeric value."
                + " Did you pass an entity id instead of an entity key?."),
        Arguments.of(
            named(
                "negative inputTokens delta",
                new UpdateRequest(
                    AGENT_INSTANCE_KEY,
                    """
                    { "elementInstanceKey": "%d", "metrics": { "inputTokens": -1 } }
                    """
                        .formatted(ELEMENT_INSTANCE_KEY))),
            "The value for metrics.inputTokens is '-1' but must be >= 0."),
        Arguments.of(
            named(
                "negative outputTokens delta",
                new UpdateRequest(
                    AGENT_INSTANCE_KEY,
                    """
                    { "elementInstanceKey": "%d", "metrics": { "outputTokens": -5 } }
                    """
                        .formatted(ELEMENT_INSTANCE_KEY))),
            "The value for metrics.outputTokens is '-5' but must be >= 0."),
        Arguments.of(
            named(
                "negative modelCalls delta",
                new UpdateRequest(
                    AGENT_INSTANCE_KEY,
                    """
                    { "elementInstanceKey": "%d", "metrics": { "modelCalls": -1 } }
                    """
                        .formatted(ELEMENT_INSTANCE_KEY))),
            "The value for metrics.modelCalls is '-1' but must be >= 0."),
        Arguments.of(
            named(
                "negative toolCalls delta",
                new UpdateRequest(
                    AGENT_INSTANCE_KEY,
                    """
                    { "elementInstanceKey": "%d", "metrics": { "toolCalls": -2 } }
                    """
                        .formatted(ELEMENT_INSTANCE_KEY))),
            "The value for metrics.toolCalls is '-2' but must be >= 0."),
        Arguments.of(
            named(
                "tool without name",
                new UpdateRequest(
                    AGENT_INSTANCE_KEY,
                    """
                    {
                      "elementInstanceKey": "%d",
                      "tools": [{ "description": "Search database" }]
                    }
                    """
                        .formatted(ELEMENT_INSTANCE_KEY))),
            "No tools[0].name provided."),
        Arguments.of(
            named(
                "tool with blank name",
                new UpdateRequest(
                    AGENT_INSTANCE_KEY,
                    """
                    {
                      "elementInstanceKey": "%d",
                      "tools": [{ "name": "" }]
                    }
                    """
                        .formatted(ELEMENT_INSTANCE_KEY))),
            "No tools[0].name provided."),
        Arguments.of(
            named(
                "tool with null name",
                new UpdateRequest(
                    AGENT_INSTANCE_KEY,
                    """
                    {
                      "elementInstanceKey": "%d",
                      "tools": [{ "name": null }]
                    }
                    """
                        .formatted(ELEMENT_INSTANCE_KEY))),
            "No tools[0].name provided."),
        Arguments.of(
            named(
                "zero agentInstanceKey",
                new UpdateRequest(
                    0,
                    """
                    { "elementInstanceKey": "%d", "status": "IDLE" }
                    """
                        .formatted(ELEMENT_INSTANCE_KEY))),
            "The value for agentInstanceKey is '0' but must be > 0."),
        Arguments.of(
            named(
                "negative agentInstanceKey",
                new UpdateRequest(
                    -1,
                    """
                    { "elementInstanceKey": "%d", "status": "IDLE" }
                    """
                        .formatted(ELEMENT_INSTANCE_KEY))),
            "The value for agentInstanceKey is '-1' but must be > 0."));
  }

  @Test
  void shouldReturn5xxOnUpdateServiceError() {
    // given
    when(agentInstanceServices.updateAgentInstance(any(AgentInstanceRecord.class), any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));

    // when / then
    webClient
        .patch()
        .uri(AGENT_INSTANCES_URL + "/%d".formatted(AGENT_INSTANCE_KEY))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            { "elementInstanceKey": "%d", "status": "IDLE" }
            """
                .formatted(ELEMENT_INSTANCE_KEY))
        .exchange()
        .expectStatus()
        .is5xxServerError();
  }

  // --------------------------------- history item tests -----------------------------------------

  @Test
  void shouldCreateAgentHistoryItemWithTextContent() {
    // given
    final var responseRecord = new AgentHistoryRecord();
    responseRecord.setAgentHistoryKey(HISTORY_ITEM_KEY);
    when(agentHistoryServices.createAgentHistoryItem(any(AgentHistoryRecord.class), any()))
        .thenReturn(CompletableFuture.completedFuture(responseRecord));

    final var requestBody =
        """
        {
          "elementInstanceKey": "%d",
          "jobKey": "%d",
          "jobLease": "lease-abc",
          "role": "ASSISTANT",
          "content": [
            { "contentType": "TEXT", "text": "I will process the invoice." }
          ],
          "producedAt": "2025-06-01T12:00:00Z"
        }
        """
            .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY);

    // when / then
    webClient
        .post()
        .uri(AGENT_INSTANCES_URL + "/%d/history".formatted(AGENT_INSTANCE_KEY))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody()
        .json(
            """
            { "historyItemKey": "%d" }
            """
                .formatted(HISTORY_ITEM_KEY),
            JsonCompareMode.STRICT);

    verify(agentHistoryServices)
        .createAgentHistoryItem(
            assertArg(
                record -> {
                  assertThat(record.getAgentInstanceKey()).isEqualTo(AGENT_INSTANCE_KEY);
                  assertThat(record.getElementInstanceKey()).isEqualTo(ELEMENT_INSTANCE_KEY);
                  assertThat(record.getJobKey()).isEqualTo(JOB_KEY);
                  assertThat(record.getJobLease()).isEqualTo("lease-abc");
                  assertThat(record.getRole().name()).isEqualTo("ASSISTANT");
                  assertThat(record.getContent()).hasSize(1);
                  assertThat(record.getContent().get(0).getText())
                      .isEqualTo("I will process the invoice.");
                }),
            any());
  }

  @Test
  void shouldCreateAgentHistoryItemWithAllFields() {
    // given
    final var responseRecord = new AgentHistoryRecord();
    responseRecord.setAgentHistoryKey(HISTORY_ITEM_KEY);
    when(agentHistoryServices.createAgentHistoryItem(any(AgentHistoryRecord.class), any()))
        .thenReturn(CompletableFuture.completedFuture(responseRecord));

    final var requestBody =
        """
        {
          "elementInstanceKey": "%d",
          "jobKey": "%d",
          "jobLease": "lease-abc",
          "role": "USER",
          "iteration": 2,
          "content": [
            { "contentType": "TEXT", "text": "What is in the invoice?" },
            { "contentType": "OBJECT", "object": { "key": "value" } }
          ],
          "toolCalls": [
            { "toolCallId": "tc-001", "toolName": "extract_data", "elementId": "extract-task" }
          ],
          "metrics": {
            "inputTokens": 512,
            "outputTokens": 128,
            "durationMs": 1500
          },
          "producedAt": "2025-06-01T12:00:00Z"
        }
        """
            .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY);

    // when / then
    webClient
        .post()
        .uri(AGENT_INSTANCES_URL + "/%d/history".formatted(AGENT_INSTANCE_KEY))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isCreated();

    verify(agentHistoryServices)
        .createAgentHistoryItem(
            assertArg(
                record -> {
                  assertThat(record.getIteration()).isEqualTo(2);
                  assertThat(record.getContent()).hasSize(2);
                  assertThat(record.getToolCalls()).hasSize(1);
                  assertThat(record.getToolCalls().get(0).getToolCallId()).isEqualTo("tc-001");
                  assertThat(record.getToolCalls().get(0).getToolName()).isEqualTo("extract_data");
                  assertThat(record.getMetrics().getInputTokens()).isEqualTo(512L);
                  assertThat(record.getMetrics().getOutputTokens()).isEqualTo(128L);
                  assertThat(record.getMetrics().getDurationMs()).isEqualTo(1500L);
                }),
            any());
  }

  @Test
  void shouldCreateAgentHistoryItemWithDocumentContent() {
    // given
    final var responseRecord = new AgentHistoryRecord();
    responseRecord.setAgentHistoryKey(HISTORY_ITEM_KEY);
    when(agentHistoryServices.createAgentHistoryItem(any(AgentHistoryRecord.class), any()))
        .thenReturn(CompletableFuture.completedFuture(responseRecord));

    final var requestBody =
        """
        {
          "elementInstanceKey": "%d",
          "jobKey": "%d",
          "jobLease": "lease-abc",
          "role": "USER",
          "content": [
            {
              "contentType": "DOCUMENT",
              "documentReference": {
                "camunda.document.type": "camunda",
                "storeId": "store-1",
                "documentId": "doc-abc",
                "contentHash": "sha256:deadbeef",
                "metadata": {
                  "contentType": "application/pdf",
                  "fileName": "invoice.pdf",
                  "expiresAt": "2025-12-31T23:59:59Z",
                  "size": 12345,
                  "processDefinitionId": "invoice-process",
                  "processInstanceKey": "%d",
                  "customProperties": {"source": "email"}
                }
              }
            }
          ],
          "producedAt": "2025-06-01T12:00:00Z"
        }
        """
            .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY, ELEMENT_INSTANCE_KEY);

    // when / then
    webClient
        .post()
        .uri(AGENT_INSTANCES_URL + "/%d/history".formatted(AGENT_INSTANCE_KEY))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isCreated();

    verify(agentHistoryServices)
        .createAgentHistoryItem(
            assertArg(
                record -> {
                  assertThat(record.getContent()).hasSize(1);
                  final var docContent = record.getContent().get(0);
                  assertThat(docContent.getContentType())
                      .isEqualTo(AgentHistoryContentType.DOCUMENT);
                  final var docRef = docContent.getDocumentReference();
                  assertThat(docRef.getDocumentId()).isEqualTo("doc-abc");
                  assertThat(docRef.getStoreId()).isEqualTo("store-1");
                  assertThat(docRef.getContentHash()).isEqualTo("sha256:deadbeef");
                  final var meta = docRef.getMetadata();
                  assertThat(meta.getContentType()).isEqualTo("application/pdf");
                  assertThat(meta.getFileName()).isEqualTo("invoice.pdf");
                  assertThat(meta.getExpiresAt())
                      .isEqualTo(
                          OffsetDateTime.parse("2025-12-31T23:59:59Z").toInstant().toEpochMilli());
                  assertThat(meta.getSize()).isEqualTo(12345L);
                  assertThat(meta.getProcessDefinitionId()).isEqualTo("invoice-process");
                  assertThat(meta.getProcessInstanceKey()).isEqualTo(ELEMENT_INSTANCE_KEY);
                  assertThat(meta.getCustomProperties()).containsEntry("source", "email");
                }),
            any());
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("invalidHistoryItemRequests")
  void shouldRejectInvalidHistoryItemRequest(
      final HistoryItemRequest request, final String expectedDetail) {
    // when / then
    webClient
        .post()
        .uri(AGENT_INSTANCES_URL + "/%s/history".formatted(request.agentInstanceKeyPath()))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request.requestBody())
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "title": "INVALID_ARGUMENT",
              "status": 400,
              "detail": "%s",
              "instance": "/v2/agent-instances/%s/history"
            }
            """
                .formatted(expectedDetail, request.agentInstanceKeyPath()),
            JsonCompareMode.STRICT);

    verifyNoInteractions(agentHistoryServices);
  }

  static Stream<Arguments> invalidHistoryItemRequests() {
    final String validKey = String.valueOf(AGENT_INSTANCE_KEY);
    final String validBody =
        """
        {
          "elementInstanceKey": "%d",
          "jobKey": "%d",
          "jobLease": "lease-abc",
          "role": "ASSISTANT",
          "content": [{ "contentType": "TEXT", "text": "hello" }],
          "producedAt": "2025-06-01T12:00:00Z"
        }
        """
            .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY);

    return Stream.of(
        Arguments.of(
            named("zero agentInstanceKey", new HistoryItemRequest("0", validBody)),
            "The value for agentInstanceKey is '0' but must be > 0."),
        Arguments.of(
            named("non-numeric agentInstanceKey", new HistoryItemRequest("not-a-key", validBody)),
            "The provided agentInstanceKey 'not-a-key' is not a valid key."
                + " Expected a numeric value."
                + " Did you pass an entity id instead of an entity key?."),
        Arguments.of(
            named(
                "missing elementInstanceKey",
                new HistoryItemRequest(
                    validKey,
                    """
                    {
                      "jobKey": "%d",
                      "jobLease": "lease-abc",
                      "role": "ASSISTANT",
                      "content": [{ "contentType": "TEXT", "text": "hello" }],
                      "producedAt": "2025-06-01T12:00:00Z"
                    }
                    """
                        .formatted(JOB_KEY))),
            "No elementInstanceKey provided."),
        Arguments.of(
            named(
                "zero elementInstanceKey",
                new HistoryItemRequest(
                    validKey,
                    """
                    {
                      "elementInstanceKey": "0",
                      "jobKey": "%d",
                      "jobLease": "lease-abc",
                      "role": "ASSISTANT",
                      "content": [{ "contentType": "TEXT", "text": "hello" }],
                      "producedAt": "2025-06-01T12:00:00Z"
                    }
                    """
                        .formatted(JOB_KEY))),
            "The value for elementInstanceKey is '0' but must be > 0."),
        Arguments.of(
            named(
                "missing jobKey",
                new HistoryItemRequest(
                    validKey,
                    """
                    {
                      "elementInstanceKey": "%d",
                      "jobLease": "lease-abc",
                      "role": "ASSISTANT",
                      "content": [{ "contentType": "TEXT", "text": "hello" }],
                      "producedAt": "2025-06-01T12:00:00Z"
                    }
                    """
                        .formatted(ELEMENT_INSTANCE_KEY))),
            "No jobKey provided."),
        Arguments.of(
            named(
                "missing role",
                new HistoryItemRequest(
                    validKey,
                    """
                    {
                      "elementInstanceKey": "%d",
                      "jobKey": "%d",
                      "jobLease": "lease-abc",
                      "content": [{ "contentType": "TEXT", "text": "hello" }],
                      "producedAt": "2025-06-01T12:00:00Z"
                    }
                    """
                        .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY))),
            "No role provided."),
        Arguments.of(
            named(
                "missing producedAt",
                new HistoryItemRequest(
                    validKey,
                    """
                    {
                      "elementInstanceKey": "%d",
                      "jobKey": "%d",
                      "jobLease": "lease-abc",
                      "role": "ASSISTANT",
                      "content": [{ "contentType": "TEXT", "text": "hello" }]
                    }
                    """
                        .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY))),
            "No producedAt provided."),
        Arguments.of(
            named(
                "invalid producedAt format",
                new HistoryItemRequest(
                    validKey,
                    """
                    {
                      "elementInstanceKey": "%d",
                      "jobKey": "%d",
                      "jobLease": "lease-abc",
                      "role": "ASSISTANT",
                      "content": [{ "contentType": "TEXT", "text": "hello" }],
                      "producedAt": "not-a-date"
                    }
                    """
                        .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY))),
            "The provided producedAt 'not-a-date' cannot be parsed as a date"
                + " according to RFC 3339, section 5.6."),
        Arguments.of(
            named(
                "invalid document metadata expiresAt format",
                new HistoryItemRequest(
                    validKey,
                    """
                    {
                      "elementInstanceKey": "%d",
                      "jobKey": "%d",
                      "jobLease": "lease-abc",
                      "role": "USER",
                      "content": [
                        {
                          "contentType": "DOCUMENT",
                          "documentReference": {
                            "camunda.document.type": "camunda",
                            "storeId": "store-1",
                            "documentId": "doc-abc",
                            "metadata": {
                              "contentType": "application/pdf",
                              "fileName": "invoice.pdf",
                              "size": 1024,
                              "expiresAt": "not-a-date",
                              "customProperties": {}
                            }
                          }
                        }
                      ],
                      "producedAt": "2025-06-01T12:00:00Z"
                    }
                    """
                        .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY))),
            "The provided content[0].documentReference.metadata.expiresAt 'not-a-date'"
                + " cannot be parsed as a date according to RFC 3339, section 5.6."),
        Arguments.of(
            named(
                "TEXT content missing text field",
                new HistoryItemRequest(
                    validKey,
                    """
                    {
                      "elementInstanceKey": "%d",
                      "jobKey": "%d",
                      "role": "ASSISTANT",
                      "content": [{ "contentType": "TEXT" }],
                      "producedAt": "2025-06-01T12:00:00Z"
                    }
                    """
                        .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY))),
            "No content[0].text provided."),
        Arguments.of(
            named(
                "DOCUMENT content missing documentReference",
                new HistoryItemRequest(
                    validKey,
                    """
                    {
                      "elementInstanceKey": "%d",
                      "jobKey": "%d",
                      "role": "USER",
                      "content": [{ "contentType": "DOCUMENT" }],
                      "producedAt": "2025-06-01T12:00:00Z"
                    }
                    """
                        .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY))),
            "No content[0].documentReference provided."),
        Arguments.of(
            named(
                "OBJECT content missing object field",
                new HistoryItemRequest(
                    validKey,
                    """
                    {
                      "elementInstanceKey": "%d",
                      "jobKey": "%d",
                      "role": "ASSISTANT",
                      "content": [{ "contentType": "OBJECT" }],
                      "producedAt": "2025-06-01T12:00:00Z"
                    }
                    """
                        .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY))),
            "No content[0].object provided."),
        Arguments.of(
            named(
                "DOCUMENT content with missing documentId",
                new HistoryItemRequest(
                    validKey,
                    """
                    {
                      "elementInstanceKey": "%d",
                      "jobKey": "%d",
                      "role": "USER",
                      "content": [{ "contentType": "DOCUMENT", "documentReference": {} }],
                      "producedAt": "2025-06-01T12:00:00Z"
                    }
                    """
                        .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY))),
            "No content[0].documentReference.documentId provided."));
  }

  @Test
  void shouldReturn5xxOnHistoryItemServiceError() {
    // given
    when(agentHistoryServices.createAgentHistoryItem(any(AgentHistoryRecord.class), any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker unavailable")));

    // when / then
    webClient
        .post()
        .uri(AGENT_INSTANCES_URL + "/%d/history".formatted(AGENT_INSTANCE_KEY))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "elementInstanceKey": "%d",
              "jobKey": "%d",
              "jobLease": "lease-abc",
              "role": "ASSISTANT",
              "content": [{ "contentType": "TEXT", "text": "hello" }],
              "producedAt": "2025-06-01T12:00:00Z"
            }
            """
                .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY))
        .exchange()
        .expectStatus()
        .is5xxServerError();
  }

  private record UpdateRequest(long agentInstanceKey, String requestBody) {}

  private record HistoryItemRequest(String agentInstanceKeyPath, String requestBody) {}
}
