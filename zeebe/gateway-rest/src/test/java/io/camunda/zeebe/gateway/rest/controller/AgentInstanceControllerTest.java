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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.AgentInstanceEntity;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceDefinition;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceLimits;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceMetrics;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceStatus;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceTool;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.query.AgentInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.AgentInstanceServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.protocol.impl.record.value.agentinstance.AgentInstanceRecord;
import java.time.OffsetDateTime;
import java.util.List;
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
  private static final long PROCESS_INSTANCE_KEY = 9007199254741001L;
  private static final long ROOT_PROCESS_INSTANCE_KEY = 9007199254741000L;
  private static final long PROCESS_DEFINITION_KEY = 9007199254740992L;
  private static final OffsetDateTime CREATION_DATE =
      OffsetDateTime.parse("2024-01-01T10:00:00+00:00");
  private static final OffsetDateTime LAST_UPDATED_DATE =
      OffsetDateTime.parse("2024-01-01T10:05:00+00:00");
  private static final OffsetDateTime COMPLETION_DATE =
      OffsetDateTime.parse("2024-01-01T10:05:00+00:00");

  private static final AgentInstanceEntity AGENT_INSTANCE_ENTITY =
      new AgentInstanceEntity(
          AGENT_INSTANCE_KEY,
          List.of(ELEMENT_INSTANCE_KEY),
          AgentInstanceStatus.COMPLETED,
          new AgentInstanceDefinition("gpt-4o", "openai", "You are a helpful assistant."),
          new AgentInstanceMetrics(100L, 200L, 3, 5),
          new AgentInstanceLimits(10000L, 10, 50),
          List.of(new AgentInstanceTool("search", "Search the web", "searchElement")),
          "AgentTask",
          PROCESS_INSTANCE_KEY,
          ROOT_PROCESS_INSTANCE_KEY,
          PROCESS_DEFINITION_KEY,
          "myProcessId",
          1,
          "v1",
          "<default>",
          CREATION_DATE,
          LAST_UPDATED_DATE,
          COMPLETION_DATE);

  @MockitoBean private AgentInstanceServices agentInstanceServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setUp() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void shouldGetAgentInstance() {
    // given
    when(agentInstanceServices.getByKey(eq(AGENT_INSTANCE_KEY), any()))
        .thenReturn(AGENT_INSTANCE_ENTITY);

    // when / then
    webClient
        .get()
        .uri("%s/%d".formatted(AGENT_INSTANCES_URL, AGENT_INSTANCE_KEY))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "agentInstanceKey": "%d",
              "status": "COMPLETED",
              "definition": {
                "model": "gpt-4o",
                "provider": "openai",
                "systemPrompt": "You are a helpful assistant."
              },
              "metrics": {
                "inputTokens": 100,
                "outputTokens": 200,
                "modelCalls": 3,
                "toolCalls": 5
              },
              "limits": {
                "maxModelCalls": 10,
                "maxTokens": 10000,
                "maxToolCalls": 50
              },
              "tools": [
                {
                  "name": "search",
                  "description": "Search the web",
                  "elementId": "searchElement"
                }
              ],
              "elementId": "AgentTask",
              "processInstanceKey": "%d",
              "rootProcessInstanceKey": "%d",
              "processDefinitionKey": "%d",
              "processDefinitionId": "myProcessId",
              "processDefinitionVersion": 1,
              "processDefinitionVersionTag": "v1",
              "tenantId": "<default>",
              "creationDate": "2024-01-01T10:00:00.000Z",
              "lastUpdatedDate": "2024-01-01T10:05:00.000Z",
              "completionDate": "2024-01-01T10:05:00.000Z",
              "elementInstanceKeys": ["%d"]
            }
            """
                .formatted(
                    AGENT_INSTANCE_KEY,
                    PROCESS_INSTANCE_KEY,
                    ROOT_PROCESS_INSTANCE_KEY,
                    PROCESS_DEFINITION_KEY,
                    ELEMENT_INSTANCE_KEY),
            JsonCompareMode.LENIENT);
  }

  @Test
  void shouldReturnNotFoundForUnknownAgentInstanceKey() {
    // given
    final var path = "%s/%d".formatted(AGENT_INSTANCES_URL, AGENT_INSTANCE_KEY);
    when(agentInstanceServices.getByKey(eq(AGENT_INSTANCE_KEY), any()))
        .thenThrow(
            ErrorMapper.mapSearchError(
                new CamundaSearchException(
                    "agent instance not found", CamundaSearchException.Reason.NOT_FOUND)));

    // when / then
    webClient
        .get()
        .uri(path)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "title": "NOT_FOUND",
              "status": 404,
              "detail": "agent instance not found",
              "instance": "%s"
            }"""
                .formatted(path),
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldSearchAgentInstancesWithEmptyBody() {
    // given
    when(agentInstanceServices.search(any(AgentInstanceQuery.class), any()))
        .thenReturn(
            new SearchQueryResult.Builder<AgentInstanceEntity>().total(0).items(List.of()).build());

    // when / then
    webClient
        .post()
        .uri("%s/search".formatted(AGENT_INSTANCES_URL))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "items": [],
              "page": { "totalItems": 0 }
            }
            """,
            JsonCompareMode.LENIENT);
  }

  @Test
  void shouldSearchAgentInstances() {
    // given
    when(agentInstanceServices.search(any(AgentInstanceQuery.class), any()))
        .thenReturn(
            new SearchQueryResult.Builder<AgentInstanceEntity>()
                .total(1)
                .startCursor("f")
                .endCursor("v")
                .items(List.of(AGENT_INSTANCE_ENTITY))
                .build());

    // when / then
    webClient
        .post()
        .uri("%s/search".formatted(AGENT_INSTANCES_URL))
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "filter": {
                "status": { "$eq": "COMPLETED" }
              }
            }
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(
            """
            {
              "items": [
                {
                  "agentInstanceKey": "%d",
                  "status": "COMPLETED",
                  "processDefinitionId": "myProcessId",
                  "processDefinitionVersion": 1,
                  "processDefinitionVersionTag": "v1"
                }
              ],
              "page": {
                "totalItems": 1,
                "startCursor": "f",
                "endCursor": "v"
              }
            }
            """
                .formatted(AGENT_INSTANCE_KEY),
            JsonCompareMode.LENIENT);
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

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("invalidUpdateRequests")
  void shouldRejectInvalidUpdateRequest(final String requestBody, final String expectedDetail) {
    // when / then
    webClient
        .patch()
        .uri(AGENT_INSTANCES_URL + "/%d".formatted(AGENT_INSTANCE_KEY))
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
              "instance": "/v2/agent-instances/%d"
            }
            """
                .formatted(expectedDetail, AGENT_INSTANCE_KEY),
            JsonCompareMode.STRICT);

    verifyNoInteractions(agentInstanceServices);
  }

  static Stream<Arguments> invalidUpdateRequests() {
    return Stream.of(
        Arguments.of(
            named(
                "missing elementInstanceKey",
                """
                { "status": "THINKING" }
                """),
            "No elementInstanceKey provided."),
        Arguments.of(
            named(
                "null elementInstanceKey",
                """
                { "elementInstanceKey": null, "status": "THINKING" }
                """),
            "No elementInstanceKey provided."),
        Arguments.of(
            named(
                "non-numeric elementInstanceKey",
                """
                { "elementInstanceKey": "not-a-number", "status": "THINKING" }
                """),
            "The provided elementInstanceKey 'not-a-number' is not a valid key."
                + " Expected a numeric value."
                + " Did you pass an entity id instead of an entity key?."),
        Arguments.of(
            named(
                "no mutable fields provided",
                """
                { "elementInstanceKey": "%d" }
                """
                    .formatted(ELEMENT_INSTANCE_KEY)),
            "At least one of status, metrics, tools is required."),
        Arguments.of(
            named(
                "negative inputTokens delta",
                """
                { "elementInstanceKey": "%d", "metrics": { "inputTokens": -1 } }
                """
                    .formatted(ELEMENT_INSTANCE_KEY)),
            "The value for metrics.inputTokens is '-1' but must be >= 0."),
        Arguments.of(
            named(
                "negative outputTokens delta",
                """
                { "elementInstanceKey": "%d", "metrics": { "outputTokens": -5 } }
                """
                    .formatted(ELEMENT_INSTANCE_KEY)),
            "The value for metrics.outputTokens is '-5' but must be >= 0."),
        Arguments.of(
            named(
                "negative modelCalls delta",
                """
                { "elementInstanceKey": "%d", "metrics": { "modelCalls": -1 } }
                """
                    .formatted(ELEMENT_INSTANCE_KEY)),
            "The value for metrics.modelCalls is '-1' but must be >= 0."),
        Arguments.of(
            named(
                "negative toolCalls delta",
                """
                { "elementInstanceKey": "%d", "metrics": { "toolCalls": -2 } }
                """
                    .formatted(ELEMENT_INSTANCE_KEY)),
            "The value for metrics.toolCalls is '-2' but must be >= 0."),
        Arguments.of(
            named(
                "only empty metrics object",
                """
                { "elementInstanceKey": "%d", "metrics": {} }
                """
                    .formatted(ELEMENT_INSTANCE_KEY)),
            "At least one of status, metrics, tools is required."),
        Arguments.of(
            named(
                "tool without name",
                """
                {
                  "elementInstanceKey": "%d",
                  "tools": [{ "description": "Search database" }]
                }
                """
                    .formatted(ELEMENT_INSTANCE_KEY)),
            "No tools[0].name provided."),
        Arguments.of(
            named(
                "tool with blank name",
                """
                {
                  "elementInstanceKey": "%d",
                  "tools": [{ "name": "" }]
                }
                """
                    .formatted(ELEMENT_INSTANCE_KEY)),
            "No tools[0].name provided."),
        Arguments.of(
            named(
                "tool with null name",
                """
                {
                  "elementInstanceKey": "%d",
                  "tools": [{ "name": null }]
                }
                """
                    .formatted(ELEMENT_INSTANCE_KEY)),
            "No tools[0].name provided."));
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
}
