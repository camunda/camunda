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
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.agenthistory.AgentHistoryRecord;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AgentInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AgentHistoryContentType;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(AgentInstanceController.class)
class AgentInstanceControllerTest extends RestControllerTest {

  private static final String AGENT_INSTANCES_URL = "/v2/agent-instances";
  private static final long ELEMENT_INSTANCE_KEY = 2251799813685248L;
  private static final long AGENT_INSTANCE_KEY = 9007199254741017L;
  private static final long JOB_KEY = 2251799813685249L;

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
          "jobKey": "%d",
          "history": [
            {
              "historyItemId": "item-0",
              "loopIteration": 1,
              "role": "CONFIGURATION",
              "content": [{ "contentType": "TEXT", "text": "configuration" }],
              "producedAt": "2025-06-01T12:00:00Z",
              "model": "gpt-4o",
              "provider": "openai",
              "systemPrompt": [{ "contentType": "TEXT", "text": "You are a helpful assistant." }]
            }
          ]
        }
        """
            .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY);

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
            { "agentInstanceKey": "%d", "createdHistory": [] }
            """
                .formatted(AGENT_INSTANCE_KEY),
            JsonCompareMode.STRICT);

    verify(agentInstanceServices)
        .createAgentInstance(
            assertArg(
                record -> {
                  assertThat(record.getElementInstanceKey()).isEqualTo(ELEMENT_INSTANCE_KEY);
                  assertThat(record.getJobKey()).isEqualTo(JOB_KEY);
                  assertThat(record.getHistory()).hasSize(1);
                  final var configurationItem = record.getHistory().get(0);
                  assertThat(configurationItem.getModel()).isEqualTo("gpt-4o");
                  assertThat(configurationItem.getProvider()).isEqualTo("openai");
                  assertThat(configurationItem.getSystemPrompt())
                      .hasSize(1)
                      .first()
                      .satisfies(
                          block -> {
                            assertThat(block.getContentType())
                                .isEqualTo(AgentHistoryContentType.TEXT);
                            assertThat(block.getText()).isEqualTo("You are a helpful assistant.");
                          });
                  assertThat(configurationItem.getLimits().getMaxTokens()).isEqualTo(-1L);
                  assertThat(configurationItem.getLimits().getMaxModelCalls()).isEqualTo(-1);
                  assertThat(configurationItem.getLimits().getMaxToolCalls()).isEqualTo(-1);
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
          "jobKey": "%d",
          "history": [
            {
              "historyItemId": "item-0",
              "loopIteration": 1,
              "role": "CONFIGURATION",
              "content": [{ "contentType": "TEXT", "text": "configuration" }],
              "producedAt": "2025-06-01T12:00:00Z",
              "model": "claude-sonnet-4-6",
              "provider": "anthropic",
              "systemPrompt": [{ "contentType": "TEXT", "text": "You are an expert." }],
              "limits": {
                "maxTokens": 100000,
                "maxModelCalls": 10,
                "maxToolCalls": 50
              }
            }
          ]
        }
        """
            .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY);

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
                  final var configurationItem = record.getHistory().get(0);
                  assertThat(configurationItem.getLimits().getMaxTokens()).isEqualTo(100_000L);
                  assertThat(configurationItem.getLimits().getMaxModelCalls()).isEqualTo(10);
                  assertThat(configurationItem.getLimits().getMaxToolCalls()).isEqualTo(50);
                }),
            any());
  }

  @Test
  void shouldReturn409WhenAgentInstanceAlreadyExistsForElementInstance() {
    // given -- service layer signals ALREADY_EXISTS (e.g. duplicate CREATE from the engine).
    final var rejectionReason =
        "Expected to associate element instance with key '%d' with an agent instance,"
            + " but it is already associated with agent instance with key '%d'."
                .formatted(ELEMENT_INSTANCE_KEY, AGENT_INSTANCE_KEY);
    final var expectedDetail =
        "Command 'CREATE' rejected with code 'ALREADY_EXISTS': " + rejectionReason;
    when(agentInstanceServices.createAgentInstance(any(AgentInstanceRecord.class), any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapBrokerRejection(
                    new BrokerRejection(
                        AgentInstanceIntent.CREATE,
                        ELEMENT_INSTANCE_KEY,
                        RejectionType.ALREADY_EXISTS,
                        rejectionReason))));

    final var requestBody =
        """
        {
          "elementInstanceKey": "%d",
          "jobKey": "%d",
          "history": [
            {
              "historyItemId": "item-0",
              "loopIteration": 1,
              "role": "CONFIGURATION",
              "content": [{ "contentType": "TEXT", "text": "configuration" }],
              "producedAt": "2025-06-01T12:00:00Z",
              "model": "gpt-4o",
              "provider": "openai",
              "systemPrompt": [{ "contentType": "TEXT", "text": "You are a helpful assistant." }]
            }
          ]
        }
        """
            .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY);

    // when / then
    webClient
        .post()
        .uri(AGENT_INSTANCES_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(requestBody)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.CONFLICT)
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "title": "ALREADY_EXISTS",
              "status": 409,
              "detail": "%s",
              "instance": "/v2/agent-instances"
            }
            """
                .formatted(expectedDetail),
            JsonCompareMode.STRICT);
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
                  "jobKey": "%d",
                  "history": [
                    {
                      "historyItemId": "item-0",
                      "loopIteration": 1,
                      "role": "CONFIGURATION",
                      "content": [{ "contentType": "TEXT", "text": "configuration" }],
                      "producedAt": "2025-06-01T12:00:00Z",
                      "model": "gpt-4o",
                      "provider": "openai",
                      "systemPrompt": [{ "contentType": "TEXT", "text": "prompt" }]
                    }
                  ]
                }
                """
                    .formatted(JOB_KEY)),
            "No elementInstanceKey provided."),
        Arguments.of(
            named(
                "null elementInstanceKey",
                """
                {
                  "elementInstanceKey": null,
                  "jobKey": "%d",
                  "history": [
                    {
                      "historyItemId": "item-0",
                      "loopIteration": 1,
                      "role": "CONFIGURATION",
                      "content": [{ "contentType": "TEXT", "text": "configuration" }],
                      "producedAt": "2025-06-01T12:00:00Z",
                      "model": "gpt-4o",
                      "provider": "openai",
                      "systemPrompt": [{ "contentType": "TEXT", "text": "prompt" }]
                    }
                  ]
                }
                """
                    .formatted(JOB_KEY)),
            "No elementInstanceKey provided."),
        Arguments.of(
            named(
                "non-numeric elementInstanceKey",
                """
                {
                  "elementInstanceKey": "not-a-number",
                  "jobKey": "%d",
                  "history": [
                    {
                      "historyItemId": "item-0",
                      "loopIteration": 1,
                      "role": "CONFIGURATION",
                      "content": [{ "contentType": "TEXT", "text": "configuration" }],
                      "producedAt": "2025-06-01T12:00:00Z",
                      "model": "gpt-4o",
                      "provider": "openai",
                      "systemPrompt": [{ "contentType": "TEXT", "text": "prompt" }]
                    }
                  ]
                }
                """
                    .formatted(JOB_KEY)),
            "The provided elementInstanceKey 'not-a-number' is not a valid key."
                + " Expected a numeric value."
                + " Did you pass an entity id instead of an entity key?."),
        Arguments.of(
            named(
                "zero elementInstanceKey",
                """
                {
                  "elementInstanceKey": "0",
                  "jobKey": "%d",
                  "history": [
                    {
                      "historyItemId": "item-0",
                      "loopIteration": 1,
                      "role": "CONFIGURATION",
                      "content": [{ "contentType": "TEXT", "text": "configuration" }],
                      "producedAt": "2025-06-01T12:00:00Z",
                      "model": "gpt-4o",
                      "provider": "openai",
                      "systemPrompt": [{ "contentType": "TEXT", "text": "prompt" }]
                    }
                  ]
                }
                """
                    .formatted(JOB_KEY)),
            "The value for elementInstanceKey is '0' but must be > 0."),
        Arguments.of(
            named(
                "negative elementInstanceKey",
                """
                {
                  "elementInstanceKey": "-1",
                  "jobKey": "%d",
                  "history": [
                    {
                      "historyItemId": "item-0",
                      "loopIteration": 1,
                      "role": "CONFIGURATION",
                      "content": [{ "contentType": "TEXT", "text": "configuration" }],
                      "producedAt": "2025-06-01T12:00:00Z",
                      "model": "gpt-4o",
                      "provider": "openai",
                      "systemPrompt": [{ "contentType": "TEXT", "text": "prompt" }]
                    }
                  ]
                }
                """
                    .formatted(JOB_KEY)),
            "The value for elementInstanceKey is '-1' but must be > 0."),
        Arguments.of(
            named(
                "missing history",
                """
                {
                  "elementInstanceKey": "%d"
                }
                """
                    .formatted(ELEMENT_INSTANCE_KEY)),
            "No jobKey provided. No history provided."),
        Arguments.of(
            named(
                "history without jobKey",
                """
                {
                  "elementInstanceKey": "%d",
                  "history": [
                    {
                      "historyItemId": "item-0",
                      "loopIteration": 1,
                      "role": "CONFIGURATION",
                      "content": [{ "contentType": "TEXT", "text": "configuration" }],
                      "producedAt": "2025-06-01T12:00:00Z",
                      "model": "gpt-4o",
                      "provider": "openai",
                      "systemPrompt": [{ "contentType": "TEXT", "text": "prompt" }]
                    },
                    {
                      "historyItemId": "item-1",
                      "loopIteration": 1,
                      "role": "USER",
                      "content": [{ "contentType": "TEXT", "text": "hello" }],
                      "producedAt": "2025-06-01T12:00:00Z"
                    }
                  ]
                }
                """
                    .formatted(ELEMENT_INSTANCE_KEY)),
            "No jobKey provided."),
        Arguments.of(
            named(
                "non-numeric jobKey",
                """
                {
                  "elementInstanceKey": "%d",
                  "jobKey": "not-a-number",
                  "history": [
                    {
                      "historyItemId": "item-0",
                      "loopIteration": 1,
                      "role": "CONFIGURATION",
                      "content": [{ "contentType": "TEXT", "text": "configuration" }],
                      "producedAt": "2025-06-01T12:00:00Z",
                      "model": "gpt-4o",
                      "provider": "openai",
                      "systemPrompt": [{ "contentType": "TEXT", "text": "prompt" }]
                    }
                  ]
                }
                """
                    .formatted(ELEMENT_INSTANCE_KEY)),
            "The provided jobKey 'not-a-number' is not a valid key. Expected a numeric value."
                + " Did you pass an entity id instead of an entity key?."),
        Arguments.of(
            named(
                "history without a CONFIGURATION item establishing the definition",
                """
                {
                  "elementInstanceKey": "%d",
                  "jobKey": "%d",
                  "history": [
                    {
                      "historyItemId": "item-1",
                      "loopIteration": 1,
                      "role": "USER",
                      "content": [{ "contentType": "TEXT", "text": "hello" }],
                      "producedAt": "2025-06-01T12:00:00Z"
                    }
                  ]
                }
                """
                    .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY)),
            "No CONFIGURATION history item sets 'model'; add a CONFIGURATION history item that"
                + " sets it. No CONFIGURATION history item sets 'provider'; add a CONFIGURATION"
                + " history item that sets it. No CONFIGURATION history item sets"
                + " 'systemPrompt'; add a CONFIGURATION history item that sets it."));
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
          "jobKey": "%d",
          "history": [
            {
              "historyItemId": "item-0",
              "loopIteration": 1,
              "role": "CONFIGURATION",
              "content": [{ "contentType": "TEXT", "text": "configuration" }],
              "producedAt": "2025-06-01T12:00:00Z",
              "model": "gpt-4o",
              "provider": "openai",
              "systemPrompt": [{ "contentType": "TEXT", "text": "prompt" }]
            }
          ]
        }
        """
            .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY);

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
        .isOk()
        .expectBody()
        .json(
            """
            { "createdHistory": [] }
            """,
            JsonCompareMode.STRICT);

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
        .isOk()
        .expectBody()
        .json(
            """
            { "createdHistory": [] }
            """,
            JsonCompareMode.STRICT);

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

  @Nested
  class UpdateWithHistoryBatchTest {

    private static final long HISTORY_ITEM_KEY_1 = 9007199254741019L;
    private static final long HISTORY_ITEM_KEY_2 = 9007199254741020L;
    private static final long HISTORY_ITEM_KEY_3 = 9007199254741021L;

    @Test
    void shouldReturn200WithCreatedHistoryInRequestOrderFlaggingDuplicates() {
      // given
      final var responseRecord = new AgentInstanceRecord();
      responseRecord.addHistoryItem(
          new AgentHistoryRecord()
              .setAgentHistoryKey(HISTORY_ITEM_KEY_1)
              .setHistoryItemId("item-1"));
      responseRecord.addHistoryItem(
          new AgentHistoryRecord()
              .setAgentHistoryKey(HISTORY_ITEM_KEY_2)
              .setHistoryItemId("item-2")
              .setDuplicate(true));
      responseRecord.addHistoryItem(
          new AgentHistoryRecord()
              .setAgentHistoryKey(HISTORY_ITEM_KEY_3)
              .setHistoryItemId("item-3"));
      when(agentInstanceServices.updateAgentInstance(any(AgentInstanceRecord.class), any()))
          .thenReturn(CompletableFuture.completedFuture(responseRecord));

      final var requestBody =
          """
          {
            "elementInstanceKey": "%d",
            "jobKey": "%d",
            "jobLease": "lease-abc",
            "history": [
              %s,
              %s,
              %s
            ]
          }
          """
              .formatted(
                  ELEMENT_INSTANCE_KEY,
                  JOB_KEY,
                  historyItemJson("item-1", "USER", "hello"),
                  historyItemJson("item-2", "ASSISTANT", "hi there"),
                  historyItemJson("item-3", "USER", "thanks"));

      // when / then
      webClient
          .patch()
          .uri(AGENT_INSTANCES_URL + "/%d".formatted(AGENT_INSTANCE_KEY))
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(requestBody)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .json(
              """
              {
                "createdHistory": [
                  { "historyItemId": "item-1", "historyItemKey": "%d", "isDuplicate": false },
                  { "historyItemId": "item-2", "historyItemKey": "%d", "isDuplicate": true },
                  { "historyItemId": "item-3", "historyItemKey": "%d", "isDuplicate": false }
                ]
              }
              """
                  .formatted(HISTORY_ITEM_KEY_1, HISTORY_ITEM_KEY_2, HISTORY_ITEM_KEY_3),
              JsonCompareMode.STRICT);

      verify(agentInstanceServices)
          .updateAgentInstance(
              assertArg(
                  record -> {
                    assertThat(record.getJobKey()).isEqualTo(JOB_KEY);
                    assertThat(record.getJobLease()).isEqualTo("lease-abc");
                    assertThat(record.getHistory()).hasSize(3);
                    assertThat(record.getHistory().get(0).getHistoryItemId()).isEqualTo("item-1");
                    assertThat(record.getHistory().get(0).getContent().get(0).getText())
                        .isEqualTo("hello");
                    assertThat(record.getHistory().get(1).getHistoryItemId()).isEqualTo("item-2");
                    assertThat(record.getHistory().get(2).getHistoryItemId()).isEqualTo("item-3");
                  }),
              any());
    }

    @Test
    void shouldAcceptHistoryBatchWithoutJobLease() {
      // given
      final var responseRecord = new AgentInstanceRecord();
      responseRecord.addHistoryItem(
          new AgentHistoryRecord()
              .setAgentHistoryKey(HISTORY_ITEM_KEY_1)
              .setHistoryItemId("item-1"));
      responseRecord.addHistoryItem(
          new AgentHistoryRecord()
              .setAgentHistoryKey(HISTORY_ITEM_KEY_2)
              .setHistoryItemId("item-2"));
      when(agentInstanceServices.updateAgentInstance(any(AgentInstanceRecord.class), any()))
          .thenReturn(CompletableFuture.completedFuture(responseRecord));

      final var requestBody =
          """
          {
            "elementInstanceKey": "%d",
            "jobKey": "%d",
            "history": [
              %s,
              %s
            ]
          }
          """
              .formatted(
                  ELEMENT_INSTANCE_KEY,
                  JOB_KEY,
                  historyItemJson("item-1", "USER", "hello"),
                  historyItemJson("item-2", "ASSISTANT", "hi there"));

      // when / then
      webClient
          .patch()
          .uri(AGENT_INSTANCES_URL + "/%d".formatted(AGENT_INSTANCE_KEY))
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(requestBody)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .json(
              """
              {
                "createdHistory": [
                  { "historyItemId": "item-1", "historyItemKey": "%d", "isDuplicate": false },
                  { "historyItemId": "item-2", "historyItemKey": "%d", "isDuplicate": false }
                ]
              }
              """
                  .formatted(HISTORY_ITEM_KEY_1, HISTORY_ITEM_KEY_2),
              JsonCompareMode.STRICT);

      verify(agentInstanceServices)
          .updateAgentInstance(
              assertArg(
                  record -> {
                    assertThat(record.getJobKey()).isEqualTo(JOB_KEY);
                    assertThat(record.getJobLease()).isEmpty();
                    assertThat(record.getHistory()).hasSize(2);
                  }),
              any());
    }

    @Test
    void shouldReturn200WhenHistoryBatchCombinedWithStatus() {
      // given
      final var responseRecord = new AgentInstanceRecord();
      responseRecord.addHistoryItem(
          new AgentHistoryRecord()
              .setAgentHistoryKey(HISTORY_ITEM_KEY_1)
              .setHistoryItemId("item-1"));
      responseRecord.addHistoryItem(
          new AgentHistoryRecord()
              .setAgentHistoryKey(HISTORY_ITEM_KEY_2)
              .setHistoryItemId("item-2"));
      when(agentInstanceServices.updateAgentInstance(any(AgentInstanceRecord.class), any()))
          .thenReturn(CompletableFuture.completedFuture(responseRecord));

      final var requestBody =
          """
          {
            "elementInstanceKey": "%d",
            "status": "THINKING",
            "jobKey": "%d",
            "jobLease": "lease-abc",
            "history": [
              %s,
              %s
            ]
          }
          """
              .formatted(
                  ELEMENT_INSTANCE_KEY,
                  JOB_KEY,
                  historyItemJson("item-1", "USER", "hello"),
                  historyItemJson("item-2", "ASSISTANT", "hi there"));

      // when / then
      webClient
          .patch()
          .uri(AGENT_INSTANCES_URL + "/%d".formatted(AGENT_INSTANCE_KEY))
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(requestBody)
          .exchange()
          .expectStatus()
          .isOk()
          .expectBody()
          .json(
              """
              {
                "createdHistory": [
                  { "historyItemId": "item-1", "historyItemKey": "%d", "isDuplicate": false },
                  { "historyItemId": "item-2", "historyItemKey": "%d", "isDuplicate": false }
                ]
              }
              """
                  .formatted(HISTORY_ITEM_KEY_1, HISTORY_ITEM_KEY_2),
              JsonCompareMode.STRICT);

      verify(agentInstanceServices)
          .updateAgentInstance(
              assertArg(
                  record -> {
                    assertThat(record.getStatus().name()).isEqualTo("THINKING");
                    assertThat(record.getJobKey()).isEqualTo(JOB_KEY);
                    assertThat(record.getJobLease()).isEqualTo("lease-abc");
                    assertThat(record.getHistory()).hasSize(2);
                    assertThat(record.getHistory().get(0).getHistoryItemId()).isEqualTo("item-1");
                    assertThat(record.getHistory().get(1).getHistoryItemId()).isEqualTo("item-2");
                    assertThat(record.getChangedAttributes()).containsExactlyInAnyOrder("status");
                  }),
              any());
    }

    @Test
    void shouldReturnEmptyCreatedHistoryWhenHistoryIsEmptyArray() {
      // given
      when(agentInstanceServices.updateAgentInstance(any(AgentInstanceRecord.class), any()))
          .thenReturn(CompletableFuture.completedFuture(new AgentInstanceRecord()));

      final var requestBody =
          """
          {
            "elementInstanceKey": "%d",
            "history": []
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
          .isOk()
          .expectBody()
          .json(
              """
              { "createdHistory": [] }
              """,
              JsonCompareMode.STRICT);
    }

    @Test
    void shouldReturn400WhenHistoryItemRejectedByBrokerWithMessageIntact() {
      // given -- a distinct, unrelated invalidity (job no longer active for the batch); an
      // intra-batch duplicate historyItemId is deliberately not this kind of rejection
      final var rejectionReason =
          ("Expected job with key '%d' referenced by history[1].historyItemId 'item-2' to be"
                  + " currently activated for agent instance with key '%d', but it is not.")
              .formatted(JOB_KEY, AGENT_INSTANCE_KEY);
      final var expectedDetail =
          "Command 'UPDATE' rejected with code 'INVALID_ARGUMENT': " + rejectionReason;
      when(agentInstanceServices.updateAgentInstance(any(AgentInstanceRecord.class), any()))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          AgentInstanceIntent.UPDATE,
                          AGENT_INSTANCE_KEY,
                          RejectionType.INVALID_ARGUMENT,
                          rejectionReason))));

      final var requestBody =
          """
          {
            "elementInstanceKey": "%d",
            "jobKey": "%d",
            "jobLease": "lease-abc",
            "history": [
              %s,
              %s
            ]
          }
          """
              .formatted(
                  ELEMENT_INSTANCE_KEY,
                  JOB_KEY,
                  historyItemJson("item-1", "USER", "hello"),
                  historyItemJson("item-2", "USER", "hello again"));

      // when / then
      webClient
          .patch()
          .uri(AGENT_INSTANCES_URL + "/%d".formatted(AGENT_INSTANCE_KEY))
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(requestBody)
          .exchange()
          .expectStatus()
          .isEqualTo(HttpStatus.BAD_REQUEST)
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
                  .formatted(expectedDetail, AGENT_INSTANCE_KEY),
              JsonCompareMode.STRICT);
    }

    private String historyItemJson(
        final String historyItemId, final String role, final String text) {
      return """
          {
            "historyItemId": "%s",
            "loopIteration": 1,
            "role": "%s",
            "content": [{ "contentType": "TEXT", "text": "%s" }],
            "producedAt": "2025-06-01T12:00:00Z"
          }
          """
          .formatted(historyItemId, role, text);
    }
  }

  @Nested
  class CreateWithHistoryBatchTest {

    private static final long HISTORY_ITEM_KEY_0 = 9007199254741021L;
    private static final long HISTORY_ITEM_KEY_1 = 9007199254741019L;
    private static final long HISTORY_ITEM_KEY_2 = 9007199254741020L;

    @Test
    void shouldReturn200WithAgentInstanceKeyAndCreatedHistoryInRequestOrderFlaggingDuplicates() {
      // given
      final var responseRecord = new AgentInstanceRecord();
      responseRecord.setAgentInstanceKey(AGENT_INSTANCE_KEY);
      responseRecord.addHistoryItem(
          new AgentHistoryRecord()
              .setAgentHistoryKey(HISTORY_ITEM_KEY_0)
              .setHistoryItemId("item-0"));
      responseRecord.addHistoryItem(
          new AgentHistoryRecord()
              .setAgentHistoryKey(HISTORY_ITEM_KEY_1)
              .setHistoryItemId("item-1"));
      responseRecord.addHistoryItem(
          new AgentHistoryRecord()
              .setAgentHistoryKey(HISTORY_ITEM_KEY_2)
              .setHistoryItemId("item-2")
              .setDuplicate(true));
      when(agentInstanceServices.createAgentInstance(any(AgentInstanceRecord.class), any()))
          .thenReturn(CompletableFuture.completedFuture(responseRecord));

      final var requestBody =
          """
          {
            "elementInstanceKey": "%d",
            "jobKey": "%d",
            "jobLease": "lease-abc",
            "history": [
              {
                "historyItemId": "item-0",
                "loopIteration": 1,
                "role": "CONFIGURATION",
                "content": [{ "contentType": "TEXT", "text": "configuration" }],
                "producedAt": "2025-06-01T12:00:00Z",
                "model": "gpt-4o",
                "provider": "openai",
                "systemPrompt": [{ "contentType": "TEXT", "text": "You are a helpful assistant." }]
              },
              {
                "historyItemId": "item-1",
                "loopIteration": 1,
                "role": "USER",
                "content": [{ "contentType": "TEXT", "text": "hello" }],
                "producedAt": "2025-06-01T12:00:00Z"
              },
              {
                "historyItemId": "item-2",
                "loopIteration": 1,
                "role": "ASSISTANT",
                "content": [{ "contentType": "TEXT", "text": "hi there" }],
                "producedAt": "2025-06-01T12:00:00Z"
              }
            ]
          }
          """
              .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY);

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
              {
                "agentInstanceKey": "%d",
                "createdHistory": [
                  { "historyItemId": "item-0", "historyItemKey": "%d", "isDuplicate": false },
                  { "historyItemId": "item-1", "historyItemKey": "%d", "isDuplicate": false },
                  { "historyItemId": "item-2", "historyItemKey": "%d", "isDuplicate": true }
                ]
              }
              """
                  .formatted(
                      AGENT_INSTANCE_KEY,
                      HISTORY_ITEM_KEY_0,
                      HISTORY_ITEM_KEY_1,
                      HISTORY_ITEM_KEY_2),
              JsonCompareMode.STRICT);

      verify(agentInstanceServices)
          .createAgentInstance(
              assertArg(
                  record -> {
                    assertThat(record.getElementInstanceKey()).isEqualTo(ELEMENT_INSTANCE_KEY);
                    assertThat(record.getJobKey()).isEqualTo(JOB_KEY);
                    assertThat(record.getJobLease()).isEqualTo("lease-abc");
                    assertThat(record.getHistory()).hasSize(3);
                    assertThat(record.getHistory().get(0).getHistoryItemId()).isEqualTo("item-0");
                    assertThat(record.getHistory().get(1).getHistoryItemId()).isEqualTo("item-1");
                    assertThat(record.getHistory().get(1).getContent().get(0).getText())
                        .isEqualTo("hello");
                    assertThat(record.getHistory().get(2).getHistoryItemId()).isEqualTo("item-2");
                  }),
              any());
    }

    @Test
    void shouldAcceptHistoryBatchWithoutJobLease() {
      // given
      final var responseRecord = new AgentInstanceRecord();
      responseRecord.setAgentInstanceKey(AGENT_INSTANCE_KEY);
      responseRecord.addHistoryItem(
          new AgentHistoryRecord()
              .setAgentHistoryKey(HISTORY_ITEM_KEY_0)
              .setHistoryItemId("item-0"));
      responseRecord.addHistoryItem(
          new AgentHistoryRecord()
              .setAgentHistoryKey(HISTORY_ITEM_KEY_1)
              .setHistoryItemId("item-1"));
      when(agentInstanceServices.createAgentInstance(any(AgentInstanceRecord.class), any()))
          .thenReturn(CompletableFuture.completedFuture(responseRecord));

      final var requestBody =
          """
          {
            "elementInstanceKey": "%d",
            "jobKey": "%d",
            "history": [
              {
                "historyItemId": "item-0",
                "loopIteration": 1,
                "role": "CONFIGURATION",
                "content": [{ "contentType": "TEXT", "text": "configuration" }],
                "producedAt": "2025-06-01T12:00:00Z",
                "model": "gpt-4o",
                "provider": "openai",
                "systemPrompt": [{ "contentType": "TEXT", "text": "You are a helpful assistant." }]
              },
              {
                "historyItemId": "item-1",
                "loopIteration": 1,
                "role": "USER",
                "content": [{ "contentType": "TEXT", "text": "hello" }],
                "producedAt": "2025-06-01T12:00:00Z"
              }
            ]
          }
          """
              .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY);

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
              {
                "agentInstanceKey": "%d",
                "createdHistory": [
                  { "historyItemId": "item-0", "historyItemKey": "%d", "isDuplicate": false },
                  { "historyItemId": "item-1", "historyItemKey": "%d", "isDuplicate": false }
                ]
              }
              """
                  .formatted(AGENT_INSTANCE_KEY, HISTORY_ITEM_KEY_0, HISTORY_ITEM_KEY_1),
              JsonCompareMode.STRICT);

      verify(agentInstanceServices)
          .createAgentInstance(
              assertArg(
                  record -> {
                    assertThat(record.getJobKey()).isEqualTo(JOB_KEY);
                    assertThat(record.getJobLease()).isEmpty();
                    assertThat(record.getHistory()).hasSize(2);
                  }),
              any());
    }

    @Test
    void shouldReturn400WhenHistoryItemRejectedByBrokerWithMessageIntact() {
      // given
      final var rejectionReason =
          ("Expected job with key '%d' referenced by history[0].historyItemId 'item-1' to be"
                  + " currently activated for agent instance, but it is not.")
              .formatted(JOB_KEY);
      final var expectedDetail =
          "Command 'CREATE' rejected with code 'INVALID_ARGUMENT': " + rejectionReason;
      when(agentInstanceServices.createAgentInstance(any(AgentInstanceRecord.class), any()))
          .thenReturn(
              CompletableFuture.failedFuture(
                  ErrorMapper.mapBrokerRejection(
                      new BrokerRejection(
                          AgentInstanceIntent.CREATE,
                          ELEMENT_INSTANCE_KEY,
                          RejectionType.INVALID_ARGUMENT,
                          rejectionReason))));

      final var requestBody =
          """
          {
            "elementInstanceKey": "%d",
            "jobKey": "%d",
            "jobLease": "lease-abc",
            "history": [
              {
                "historyItemId": "item-0",
                "loopIteration": 1,
                "role": "CONFIGURATION",
                "content": [{ "contentType": "TEXT", "text": "configuration" }],
                "producedAt": "2025-06-01T12:00:00Z",
                "model": "gpt-4o",
                "provider": "openai",
                "systemPrompt": [{ "contentType": "TEXT", "text": "You are a helpful assistant." }]
              },
              {
                "historyItemId": "item-1",
                "loopIteration": 1,
                "role": "USER",
                "content": [{ "contentType": "TEXT", "text": "hello" }],
                "producedAt": "2025-06-01T12:00:00Z"
              }
            ]
          }
          """
              .formatted(ELEMENT_INSTANCE_KEY, JOB_KEY);

      // when / then
      webClient
          .post()
          .uri(AGENT_INSTANCES_URL)
          .accept(MediaType.APPLICATION_JSON)
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(requestBody)
          .exchange()
          .expectStatus()
          .isEqualTo(HttpStatus.BAD_REQUEST)
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
    }
  }

  private record UpdateRequest(long agentInstanceKey, String requestBody) {}
}
