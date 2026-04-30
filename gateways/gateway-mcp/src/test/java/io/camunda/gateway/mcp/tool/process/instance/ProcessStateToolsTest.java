/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.process.instance;

import static io.camunda.gateway.mcp.tool.CallToolResultAssertions.assertTextContentFallback;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.gateway.mcp.OperationalToolsTest;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.ContextConfiguration;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = {ProcessStateTools.class})
class ProcessStateToolsTest extends OperationalToolsTest {

  @Autowired private JsonMapper objectMapper;

  @Test
  void shouldGetProcessState() {
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

    // when
    final CallToolResult result =
        mcpClient.callTool(
            CallToolRequest.builder()
                .name(ProcessStateTools.TOOL_NAME)
                .arguments(Map.of("processInstanceKey", 12345L))
                .build());

    // then
    assertThat(result.isError()).isFalse();
    assertThat(result.structuredContent()).isNotNull();

    @SuppressWarnings("unchecked")
    final var content =
        objectMapper.convertValue(
            result.structuredContent(), new TypeReference<Map<String, Object>>() {});
    assertThat(content).containsEntry("processInstanceKey", 12345);
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
              assertThat(i).containsEntry("incidentKey", 777);
              assertThat(i).containsEntry("errorType", "JOB_NO_RETRIES");
              assertThat(i).containsEntry("errorMessage", "No retries left");
              assertThat(i).containsEntry("flowNodeId", "ServiceTask_1");
            });

    assertTextContentFallback(result);
  }

  @Test
  void shouldFailGetProcessStateOnException() {
    // given
    when(processInstanceServices.getByKey(any(), any()))
        .thenThrow(new ServiceException("Expected failure", Status.NOT_FOUND));

    // when
    final CallToolResult result =
        mcpClient.callTool(
            CallToolRequest.builder()
                .name(ProcessStateTools.TOOL_NAME)
                .arguments(Map.of("processInstanceKey", 12345L))
                .build());

    // then
    assertThat(result.isError()).isTrue();
    assertThat(result.structuredContent()).isNotNull();

    final var problemDetail =
        objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
    assertThat(problemDetail.getDetail()).isEqualTo("Expected failure");
    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(problemDetail.getTitle()).isEqualTo("NOT_FOUND");

    assertTextContentFallback(result);
  }

  @Test
  void shouldFailGetProcessStateOnMissingKey() {
    // when
    final CallToolResult result =
        mcpClient.callTool(
            CallToolRequest.builder()
                .name(ProcessStateTools.TOOL_NAME)
                .arguments(Map.of())
                .build());

    // then
    assertThat(result.isError()).isTrue();
    assertThat(result.structuredContent()).isNull();
    assertThat(result.content())
        .hasSize(1)
        .first()
        .isInstanceOfSatisfying(
            TextContent.class,
            textContent ->
                assertThat(textContent.text())
                    .isEqualTo("processInstanceKey: Process instance key must not be null."));
  }

  @Test
  void shouldFailGetProcessStateOnNullKey() {
    // when
    final var arguments = new HashMap<String, Object>();
    arguments.put("processInstanceKey", null);
    final CallToolResult result =
        mcpClient.callTool(
            CallToolRequest.builder()
                .name(ProcessStateTools.TOOL_NAME)
                .arguments(arguments)
                .build());

    // then
    assertThat(result.isError()).isTrue();
    assertThat(result.structuredContent()).isNull();
    assertThat(result.content())
        .hasSize(1)
        .first()
        .isInstanceOfSatisfying(
            TextContent.class,
            textContent ->
                assertThat(textContent.text())
                    .isEqualTo("processInstanceKey: Process instance key must not be null."));
  }

  @Test
  void shouldFailGetProcessStateOnInvalidKey() {
    // when
    final CallToolResult result =
        mcpClient.callTool(
            CallToolRequest.builder()
                .name(ProcessStateTools.TOOL_NAME)
                .arguments(Map.of("processInstanceKey", -3L))
                .build());

    // then
    assertThat(result.isError()).isTrue();
    assertThat(result.structuredContent()).isNull();
    assertThat(result.content())
        .hasSize(1)
        .first()
        .isInstanceOfSatisfying(
            TextContent.class,
            textContent ->
                assertThat(textContent.text())
                    .isEqualTo(
                        "processInstanceKey: Process instance key must be a positive number."));
  }
}
