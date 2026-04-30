/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.processes;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.gateway.mcp.ProcessesToolsTest;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionState;
import io.camunda.search.entities.MessageSubscriptionEntity.MessageSubscriptionType;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.service.MessageServices.CorrelateMessageRequest;
import io.camunda.zeebe.protocol.impl.record.value.message.MessageCorrelationRecord;
import io.camunda.zeebe.util.Either;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = ProcessesToolRepository.class)
class ProcessesToolRepositoryTest extends ProcessesToolsTest {

  @MockitoBean private McpTransportContext transportContext;
  @Autowired private JsonMapper objectMapper;

  private ProcessesToolRepository repository;

  @BeforeEach
  void setUp() {
    repository =
        new ProcessesToolRepository(
            messageSubscriptionServices,
            messageServices,
            processInstanceServices,
            variableServices,
            elementInstanceServices,
            incidentServices,
            authenticationProvider);
  }

  @Test
  void shouldReturnToolNameAsToolTitlePlusKey() {
    // given
    final var entity =
        buildStartSubscriptionEntity(
            99L,
            "orderProcess",
            Map.of(),
            "orderMessage",
            "<default>",
            MessageSubscriptionState.CREATED);
    when(messageSubscriptionServices.search(any(), any())).thenReturn(SearchQueryResult.of(entity));

    // when
    final var tools = repository.getTools(transportContext);

    // then
    assertThat(tools)
        .filteredOn(t -> !ProcessesToolRepository.TOOL_GET_PROCESS_STATE.equals(t.name()))
        .singleElement()
        .satisfies(
            tool -> {
              assertThat(tool.name()).isEqualTo("orderProcess_99");
              assertThat(tool.title()).isEqualTo("orderProcess");
            });
  }

  @Test
  void shouldBuildDescriptionFromExtensionProperties() {
    // given
    final var props =
        Map.of(
            ProcessesToolRepository.PROPERTY_INPUTS, "Provide a name",
            ProcessesToolRepository.PROPERTY_PURPOSE, "Starts an order",
            ProcessesToolRepository.PROPERTY_WHEN_TO_USE, "When user wants to place an order",
            ProcessesToolRepository.PROPERTY_WHEN_NOT_TO_USE, "For cancellations",
            ProcessesToolRepository.PROPERTY_RESULTS, "Returns orderId");
    final var entity =
        buildStartSubscriptionEntity(
            1L, "placeOrder", props, "order.start", "<default>", MessageSubscriptionState.CREATED);
    when(messageSubscriptionServices.search(any(), any())).thenReturn(SearchQueryResult.of(entity));

    // when
    final var tools = repository.getTools(transportContext);

    // then: getProcessState is first; dynamic tool is at index 1
    final String description = tools.get(1).description();
    assertThat(description)
        .isEqualTo(
            """
                Starts an order

                ## Inputs
                Provide a name

                ## When to use
                When user wants to place an order

                ## When not to use
                For cancellations

                ## Results
                Returns orderId""");
  }

  @Test
  void shouldOmitBlankDescriptionSections() {
    // given
    final var props = Map.of(ProcessesToolRepository.PROPERTY_PURPOSE, "Core purpose only");
    final var entity =
        buildStartSubscriptionEntity(
            5L, "minimalTool", props, "minMsg", "<default>", MessageSubscriptionState.CREATED);
    when(messageSubscriptionServices.search(any(), any())).thenReturn(SearchQueryResult.of(entity));

    // when
    final var tools = repository.getTools(transportContext);

    // then: getProcessState is first; dynamic tool is at index 1
    final String description = tools.get(1).description();
    assertThat(description).isEqualTo("Core purpose only");
  }

  @Test
  void shouldReturnExpectedToolSpecification() {
    // given
    final var expectedTool =
        objectMapper.readTree(
            """
      {
        "name": "orderProcess_99",
        "title": "orderProcess",
        "description": "Core purpose",
        "inputSchema": {
          "type": "object",
          "properties": {},
          "required": [],
          "additionalProperties":false,
          "$defs":{},
          "definitions":{}
        },
        "outputSchema": {
          "type": "object",
          "properties": {
            "processInstanceKey": {
              "type": "integer",
              "format": "int64",
              "description": "The key of the started process instance. Use this to investigate the state of the started instance."
            }
          },
          "required": ["processInstanceKey"]
        }
      }""");

    final var entity =
        buildStartSubscriptionEntity(
            99L,
            "orderProcess",
            Map.of(ProcessesToolRepository.PROPERTY_PURPOSE, "Core purpose"),
            "orderMessage",
            "<default>",
            MessageSubscriptionState.CREATED);
    when(messageSubscriptionServices.search(any(), any())).thenReturn(SearchQueryResult.of(entity));

    // when
    final var tools = repository.getTools(transportContext);

    // then
    assertThat(tools)
        .filteredOn(t -> !ProcessesToolRepository.TOOL_GET_PROCESS_STATE.equals(t.name()))
        .singleElement()
        .satisfies(
            tool -> {
              final JsonNode actualTool = objectMapper.valueToTree(tool);
              assertThat(actualTool).isEqualTo(expectedTool);
            });
  }

  @Test
  void shouldFilterByStartEventTypeAndNonDeletedStateAndToolNameExists() {
    // given
    when(messageSubscriptionServices.search(any(), any())).thenReturn(SearchQueryResult.empty());

    // when
    repository.getTools(transportContext);

    // then
    final ArgumentCaptor<MessageSubscriptionQuery> queryCaptor =
        ArgumentCaptor.forClass(MessageSubscriptionQuery.class);
    verify(messageSubscriptionServices).search(queryCaptor.capture(), any());

    final var filter = queryCaptor.getValue().filter();
    assertThat(filter.messageSubscriptionTypeOperations())
        .singleElement()
        .satisfies(
            op ->
                assertThat(op).isEqualTo(Operation.eq(MessageSubscriptionType.START_EVENT.name())));
    assertThat(filter.messageSubscriptionStateOperations())
        .singleElement()
        .satisfies(
            op -> assertThat(op).isEqualTo(Operation.neq(MessageSubscriptionState.DELETED.name())));
    assertThat(filter.toolNameOperations())
        .singleElement()
        .satisfies(op -> assertThat(op).isEqualTo(Operation.exists(true)));
  }

  @Test
  void shouldFindTool() {
    // given
    final var entity =
        buildStartSubscriptionEntity(
            77L, "deploy", Map.of(), "deploy.start", "tenant-a", MessageSubscriptionState.CREATED);
    when(messageSubscriptionServices.getByKey(eq(77L), any())).thenReturn(entity);
    when(messageSubscriptionServices.search(any(), any())).thenReturn(SearchQueryResult.of(entity));

    final var correlationRecord = new MessageCorrelationRecord();
    when(messageServices.correlateMessage(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(correlationRecord));

    final var toolSpec = repository.getTools(transportContext).get(1); // index 0 is getProcessState

    // when
    final Either<String, SyncToolSpecification> result =
        repository.findTool(transportContext, "deploy_77");

    // then
    assertThat(result.isRight()).isTrue();
    assertThat(result.get().tool()).isEqualTo(toolSpec);
    assertThat(result.get().callHandler()).isNotNull();
  }

  @Test
  void shouldCorrelateMessageWhenToolIsCalled() {
    // given
    final var entity =
        buildStartSubscriptionEntity(
            77L, "deploy", Map.of(), "deploy.start", "tenant-a", MessageSubscriptionState.CREATED);
    when(messageSubscriptionServices.getByKey(eq(77L), any())).thenReturn(entity);

    final var correlationRecord = new MessageCorrelationRecord();
    when(messageServices.correlateMessage(any(), any()))
        .thenReturn(CompletableFuture.completedFuture(correlationRecord));

    final Either<String, SyncToolSpecification> result =
        repository.findTool(transportContext, "deploy_77");

    // when
    result
        .get()
        .callHandler()
        .apply(
            transportContext,
            CallToolRequest.builder().name("deploy_77").arguments(Map.of("foo", "bar")).build());

    final ArgumentCaptor<CorrelateMessageRequest> reqCaptor =
        ArgumentCaptor.forClass(CorrelateMessageRequest.class);
    verify(messageServices).correlateMessage(reqCaptor.capture(), any());

    assertThat(reqCaptor.getValue().name()).isEqualTo("deploy.start");
    assertThat(reqCaptor.getValue().tenantId()).isEqualTo("tenant-a");
    assertThat(reqCaptor.getValue().correlationKey()).isEmpty();
    assertThat(reqCaptor.getValue().variables()).containsExactly(entry("foo", "bar"));
  }

  @Test
  void shouldReturnNotFoundWhenSubscriptionDoesNotExist() {
    // given
    when(messageSubscriptionServices.getByKey(anyLong(), any())).thenReturn(null);

    // when
    final var result = repository.findTool(transportContext, "missingTool_123");

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Tool not found: missingTool_123");
  }

  @Test
  void shouldReturnRemovedMessageForDeletedSubscription() {
    // given
    final var entity =
        buildStartSubscriptionEntity(
            55L, "oldTool", Map.of(), "old.message", "<default>", MessageSubscriptionState.DELETED);
    when(messageSubscriptionServices.getByKey(eq(55L), any())).thenReturn(entity);

    // when
    final var result = repository.findTool(transportContext, "oldTool_55");

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).contains("oldTool_55").contains("removed").contains("refresh");
  }

  @Test
  void shouldReturnNotFoundWhenToolNameHasNoNumericKeySuffix() {
    // when
    final var result = repository.findTool(transportContext, "notoolkey");

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Tool not found: notoolkey");
  }

  @Test
  void shouldReturnNotFoundWhenSuffixIsNotANumber() {
    // when
    final var result = repository.findTool(transportContext, "myTool_notanumber");

    // then
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).isEqualTo("Tool not found: myTool_notanumber");
  }

  @Test
  void shouldIncludeCorrelatedStateSubscriptions() {
    // given
    final var entity =
        buildStartSubscriptionEntity(
            88L,
            "correlatedTool",
            Map.of(ProcessesToolRepository.PROPERTY_PURPOSE, "correlated"),
            "corr.msg",
            "<default>",
            MessageSubscriptionState.CORRELATED);
    when(messageSubscriptionServices.search(any(), any())).thenReturn(SearchQueryResult.of(entity));

    // when
    final var tools = repository.getTools(transportContext);

    // then: getProcessState is always present plus the correlated dynamic tool
    assertThat(tools).hasSize(2);
    assertThat(tools.get(1).name()).isEqualTo("correlatedTool_88");
  }

  @Test
  void shouldReturnExpectedToolsToClient() {
    // given
    final var entity =
        buildStartSubscriptionEntity(
            88L,
            "myTool",
            Map.of(ProcessesToolRepository.PROPERTY_PURPOSE, "does things"),
            "message",
            "<default>",
            MessageSubscriptionState.CREATED);
    when(messageSubscriptionServices.search(any(), any())).thenReturn(SearchQueryResult.of(entity));

    // when
    final var toolsResult = mcpClient.listTools();

    // then: getProcessState is always present alongside the dynamic tool
    assertThat(toolsResult.tools())
        .extracting(Tool::name)
        .containsExactly(ProcessesToolRepository.TOOL_GET_PROCESS_STATE, "myTool_88");
  }

  @Test
  void shouldLetClientCallExistingTool() {
    // given
    final var entity =
        buildStartSubscriptionEntity(
            88L,
            "myTool",
            Map.of(ProcessesToolRepository.PROPERTY_PURPOSE, "does things"),
            "message",
            "<default>",
            MessageSubscriptionState.CREATED);
    when(messageSubscriptionServices.getByKey(eq(88L), any())).thenReturn(entity);
    when(messageServices.correlateMessage(any(), any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                new MessageCorrelationRecord().setProcessInstanceKey(12345678910L)));

    // when
    final var result = mcpClient.callTool(CallToolRequest.builder().name("myTool_88").build());

    // then
    assertThat(result.isError()).isFalse();
    assertThat(result.structuredContent()).isNotNull();

    final var incident =
        objectMapper.convertValue(
            result.structuredContent(), new TypeReference<Map<String, Object>>() {});

    assertThat(incident).containsExactly(entry("processInstanceKey", 12345678910L));

    verify(messageServices)
        .correlateMessage(
            eq(new CorrelateMessageRequest("message", "", Map.of(), "<default>")), any());
  }

  @Test
  void shouldAlwaysIncludeStaticGetProcessStateTool() {
    // given
    when(messageSubscriptionServices.search(any(), any())).thenReturn(SearchQueryResult.empty());

    // when
    final var tools = repository.getTools(transportContext);

    // then
    assertThat(tools)
        .anySatisfy(
            tool -> {
              assertThat(tool.name()).isEqualTo(ProcessesToolRepository.TOOL_GET_PROCESS_STATE);
              assertThat(tool.title()).isEqualTo("Get process state");
            });
  }

  @Test
  void shouldFindStaticGetProcessStateTool() {
    // when
    final var result = repository.findTool(transportContext, ProcessesToolRepository.TOOL_GET_PROCESS_STATE);

    // then
    assertThat(result.isRight()).isTrue();
    assertThat(result.get().tool().name()).isEqualTo(ProcessesToolRepository.TOOL_GET_PROCESS_STATE);
    assertThat(result.get().callHandler()).isNotNull();
  }

  @Test
  void shouldGetProcessStateAssemblesFullState() {
    // given
    final var processInstance =
        new ProcessInstanceEntity(
            12345L,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            ProcessInstanceState.ACTIVE,
            true,
            "<default>",
            null,
            null,
            null);

    final var variable =
        new VariableEntity(
            1L, "orderId", "\"abc\"", null, false, 12345L, 12345L, null, "myProcess", "<default>");

    final var flowNode =
        new FlowNodeInstanceEntity(
            500L,
            12345L,
            null,
            99L,
            null,
            null,
            "ServiceTask_1",
            "My Task",
            null,
            FlowNodeType.SERVICE_TASK,
            FlowNodeState.ACTIVE,
            null,
            null,
            "myProcess",
            "<default>",
            null);

    final var incident =
        new IncidentEntity(
            777L,
            99L,
            "myProcess",
            12345L,
            null,
            ErrorType.JOB_NO_RETRIES,
            "No retries left",
            "ServiceTask_1",
            500L,
            OffsetDateTime.now(),
            null,
            null,
            "<default>");

    when(processInstanceServices.getByKey(eq(12345L), any())).thenReturn(processInstance);
    when(variableServices.search(any(), any())).thenReturn(SearchQueryResult.of(variable));
    when(elementInstanceServices.search(any(), any())).thenReturn(SearchQueryResult.of(flowNode));
    when(incidentServices.search(any(IncidentQuery.class), any()))
        .thenReturn(SearchQueryResult.of(incident));

    final var spec = repository.findTool(transportContext, ProcessesToolRepository.TOOL_GET_PROCESS_STATE).get();

    // when
    final var result =
        spec.callHandler()
            .apply(
                transportContext,
                CallToolRequest.builder()
                    .name(ProcessesToolRepository.TOOL_GET_PROCESS_STATE)
                    .arguments(Map.of("processInstanceKey", 12345L))
                    .build());

    // then
    assertThat(result.isError()).isFalse();

    @SuppressWarnings("unchecked")
    final var content =
        objectMapper.convertValue(
            result.structuredContent(), new TypeReference<Map<String, Object>>() {});

    assertThat(content).containsEntry("processInstanceKey", 12345L);
    assertThat(content).containsEntry("state", "ACTIVE");
    assertThat(content).containsEntry("hasIncident", true);

    @SuppressWarnings("unchecked")
    final var variables = (List<Map<String, Object>>) content.get("variables");
    assertThat(variables)
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v).containsEntry("name", "orderId");
              assertThat(v).containsEntry("value", "\"abc\"");
            });

    @SuppressWarnings("unchecked")
    final var activeElements = (List<Map<String, Object>>) content.get("activeElementInstances");
    assertThat(activeElements)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e).containsEntry("flowNodeId", "ServiceTask_1");
              assertThat(e).containsEntry("flowNodeName", "My Task");
              assertThat(e).containsEntry("type", "SERVICE_TASK");
            });

    @SuppressWarnings("unchecked")
    final var incidents = (List<Map<String, Object>>) content.get("incidents");
    assertThat(incidents)
        .singleElement()
        .satisfies(
            i -> {
              assertThat(i).containsEntry("incidentKey", 777L);
              assertThat(i).containsEntry("errorType", "JOB_NO_RETRIES");
              assertThat(i).containsEntry("errorMessage", "No retries left");
              assertThat(i).containsEntry("flowNodeId", "ServiceTask_1");
            });
  }

  @Test
  void shouldGetProcessStateReturnsErrorWhenProcessInstanceKeyMissing() {
    // given
    final var spec = repository.findTool(transportContext, ProcessesToolRepository.TOOL_GET_PROCESS_STATE).get();

    // when
    final var result =
        spec.callHandler()
            .apply(
                transportContext, CallToolRequest.builder().name(ProcessesToolRepository.TOOL_GET_PROCESS_STATE).build());

    // then
    assertThat(result.isError()).isTrue();
  }

  @Test
  void shouldClientCallGetProcessStateTool() {
    // given
    when(messageSubscriptionServices.search(any(), any())).thenReturn(SearchQueryResult.empty());

    final var processInstance =
        new ProcessInstanceEntity(
            12345L,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            ProcessInstanceState.COMPLETED,
            false,
            "<default>",
            null,
            null,
            null);

    when(processInstanceServices.getByKey(eq(12345L), any())).thenReturn(processInstance);
    when(variableServices.search(any(), any())).thenReturn(SearchQueryResult.empty());
    when(elementInstanceServices.search(any(), any())).thenReturn(SearchQueryResult.empty());
    when(incidentServices.search(any(IncidentQuery.class), any()))
        .thenReturn(SearchQueryResult.empty());

    // when
    final var result =
        mcpClient.callTool(
            CallToolRequest.builder()
                .name(ProcessesToolRepository.TOOL_GET_PROCESS_STATE)
                .arguments(Map.of("processInstanceKey", 12345L))
                .build());

    // then
    assertThat(result.isError()).isFalse();
    @SuppressWarnings("unchecked")
    final var content =
        objectMapper.convertValue(
            result.structuredContent(), new TypeReference<Map<String, Object>>() {});
    assertThat(content).containsEntry("state", "COMPLETED");
    assertThat(content).containsEntry("hasIncident", false);
  }

  private static MessageSubscriptionEntity buildStartSubscriptionEntity(
      final Long key,
      final String toolName,
      final Map<String, String> extensionProperties,
      final String messageName,
      final String tenantId,
      final MessageSubscriptionState state) {
    return MessageSubscriptionEntity.builder()
        .messageSubscriptionKey(key)
        .toolName(toolName)
        .extensionProperties(extensionProperties)
        .messageName(messageName)
        .tenantId(tenantId)
        .messageSubscriptionState(state)
        .messageSubscriptionType(MessageSubscriptionType.START_EVENT)
        .processDefinitionId("myProcess_" + toolName)
        .flowNodeId("agentStart")
        .build();
  }
}
