/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.process.state;

import static io.camunda.gateway.mcp.tool.CallToolResultAssertions.assertTextContentFallback;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.gateway.mcp.OperationalToolsTest;
import io.camunda.gateway.protocol.model.ElementInstanceResult;
import io.camunda.gateway.protocol.model.ElementInstanceStateEnum;
import io.camunda.gateway.protocol.model.ProcessInstanceResult;
import io.camunda.gateway.protocol.model.ProcessInstanceStateEnum;
import io.camunda.gateway.protocol.model.VariableSearchResult;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.query.VariableQuery;
import io.camunda.service.ElementInstanceServices;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.VariableServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = {ProcessStateTools.class})
class ProcessStateToolsTest extends OperationalToolsTest {

  static final ProcessInstanceEntity PROCESS_INSTANCE_ENTITY =
      new ProcessInstanceEntity(
          123L,
          null,
          "demoProcess",
          "Demo Process",
          5,
          "v5",
          789L,
          333L,
          777L,
          OffsetDateTime.parse("2024-01-01T00:00:00Z"),
          null,
          ProcessInstanceState.ACTIVE,
          true,
          "tenant",
          "PI_123",
          Set.of("tag1"),
          null);

  static final VariableEntity VARIABLE_ENTITY =
      new VariableEntity(
          456L,
          "orderId",
          "\"order-99\"",
          "\"order-99\"",
          false,
          123L,
          123L,
          null,
          "demoProcess",
          "tenant");

  static final FlowNodeInstanceEntity ELEMENT_INSTANCE_ENTITY =
      new FlowNodeInstanceEntity(
          999L,
          123L,
          null,
          789L,
          OffsetDateTime.parse("2024-01-01T01:00:00Z"),
          null,
          "serviceTask_1",
          "Process Order",
          null,
          FlowNodeType.SERVICE_TASK,
          FlowNodeState.ACTIVE,
          false,
          null,
          "demoProcess",
          "tenant",
          null);

  static final SearchQueryResult<VariableEntity> VARIABLE_SEARCH_RESULT =
      new Builder<VariableEntity>().total(1L).items(List.of(VARIABLE_ENTITY)).build();

  static final SearchQueryResult<FlowNodeInstanceEntity> ELEMENT_INSTANCE_SEARCH_RESULT =
      new Builder<FlowNodeInstanceEntity>()
          .total(1L)
          .items(List.of(ELEMENT_INSTANCE_ENTITY))
          .build();

  @MockitoBean private ProcessInstanceServices processInstanceServices;
  @MockitoBean private VariableServices variableServices;
  @MockitoBean private ElementInstanceServices elementInstanceServices;

  @Autowired private JsonMapper objectMapper;

  @Captor private ArgumentCaptor<VariableQuery> variableQueryCaptor;
  @Captor private ArgumentCaptor<FlowNodeInstanceQuery> elementQueryCaptor;

  private void assertExampleResult(final ProcessStateResult result) {
    assertExampleProcessInstance(result.processInstance());
    assertExampleVariable(result.variables().getFirst());
    assertExampleElementInstance(result.activeElementInstances().getFirst());
  }

  private void assertExampleProcessInstance(final ProcessInstanceResult processInstance) {
    assertThat(processInstance.getProcessInstanceKey()).isEqualTo("123");
    assertThat(processInstance.getProcessDefinitionId()).isEqualTo("demoProcess");
    assertThat(processInstance.getState()).isEqualTo(ProcessInstanceStateEnum.ACTIVE);
    assertThat(processInstance.getHasIncident()).isTrue();
  }

  private void assertExampleVariable(final VariableSearchResult variable) {
    assertThat(variable.getVariableKey()).isEqualTo("456");
    assertThat(variable.getName()).isEqualTo("orderId");
    assertThat(variable.getValue()).isEqualTo("\"order-99\"");
    assertThat(variable.getProcessInstanceKey()).isEqualTo("123");
    assertThat(variable.getTenantId()).isEqualTo("tenant");
  }

  private void assertExampleElementInstance(final ElementInstanceResult elementInstance) {
    assertThat(elementInstance.getElementInstanceKey()).isEqualTo("999");
    assertThat(elementInstance.getProcessInstanceKey()).isEqualTo("123");
    assertThat(elementInstance.getElementId()).isEqualTo("serviceTask_1");
    assertThat(elementInstance.getElementName()).isEqualTo("Process Order");
    assertThat(elementInstance.getState()).isEqualTo(ElementInstanceStateEnum.ACTIVE);
  }

  @Nested
  class GetProcessState {

    @Test
    void shouldGetProcessStateByKey() {
      // given
      when(processInstanceServices.getByKey(any(), any())).thenReturn(PROCESS_INSTANCE_ENTITY);
      when(variableServices.search(any(VariableQuery.class), any()))
          .thenReturn(VARIABLE_SEARCH_RESULT);
      when(elementInstanceServices.search(any(FlowNodeInstanceQuery.class), any()))
          .thenReturn(ELEMENT_INSTANCE_SEARCH_RESULT);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getProcessState")
                  .arguments(Map.of("processInstanceKey", 123L))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      final var processStateResult =
          objectMapper.convertValue(result.structuredContent(), ProcessStateResult.class);
      assertExampleResult(processStateResult);

      verify(processInstanceServices).getByKey(eq(123L), any());

      assertTextContentFallback(result);
    }

    @Test
    void shouldQueryVariablesByProcessInstanceKey() {
      // given
      when(processInstanceServices.getByKey(any(), any())).thenReturn(PROCESS_INSTANCE_ENTITY);
      when(variableServices.search(any(VariableQuery.class), any()))
          .thenReturn(VARIABLE_SEARCH_RESULT);
      when(elementInstanceServices.search(any(FlowNodeInstanceQuery.class), any()))
          .thenReturn(ELEMENT_INSTANCE_SEARCH_RESULT);

      // when
      mcpClient.callTool(
          CallToolRequest.builder()
              .name("getProcessState")
              .arguments(Map.of("processInstanceKey", 123L))
              .build());

      // then
      verify(variableServices).search(variableQueryCaptor.capture(), any());
      final VariableFilter capturedFilter = variableQueryCaptor.getValue().filter();
      assertThat(capturedFilter.processInstanceKeyOperations()).hasSize(1);
    }

    @Test
    void shouldQueryActiveElementInstancesByProcessInstanceKey() {
      // given
      when(processInstanceServices.getByKey(any(), any())).thenReturn(PROCESS_INSTANCE_ENTITY);
      when(variableServices.search(any(VariableQuery.class), any()))
          .thenReturn(VARIABLE_SEARCH_RESULT);
      when(elementInstanceServices.search(any(FlowNodeInstanceQuery.class), any()))
          .thenReturn(ELEMENT_INSTANCE_SEARCH_RESULT);

      // when
      mcpClient.callTool(
          CallToolRequest.builder()
              .name("getProcessState")
              .arguments(Map.of("processInstanceKey", 123L))
              .build());

      // then
      verify(elementInstanceServices).search(elementQueryCaptor.capture(), any());
      final FlowNodeInstanceFilter capturedFilter = elementQueryCaptor.getValue().filter();
      assertThat(capturedFilter.processInstanceKeys()).containsExactly(123L);
      assertThat(capturedFilter.stateOperations()).hasSize(1);
    }

    @Test
    void shouldReturnEmptyListsWhenNoVariablesOrElementInstances() {
      // given
      when(processInstanceServices.getByKey(any(), any())).thenReturn(PROCESS_INSTANCE_ENTITY);
      when(variableServices.search(any(VariableQuery.class), any()))
          .thenReturn(new Builder<VariableEntity>().total(0L).items(List.of()).build());
      when(elementInstanceServices.search(any(FlowNodeInstanceQuery.class), any()))
          .thenReturn(new Builder<FlowNodeInstanceEntity>().total(0L).items(List.of()).build());

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getProcessState")
                  .arguments(Map.of("processInstanceKey", 123L))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      final var processStateResult =
          objectMapper.convertValue(result.structuredContent(), ProcessStateResult.class);
      assertThat(processStateResult.variables()).isEmpty();
      assertThat(processStateResult.activeElementInstances()).isEmpty();
    }

    @Test
    void shouldFailGetProcessStateOnProcessInstanceNotFound() {
      // given
      when(processInstanceServices.getByKey(any(), any()))
          .thenThrow(new ServiceException("Expected failure", Status.NOT_FOUND));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getProcessState")
                  .arguments(Map.of("processInstanceKey", 123L))
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
              CallToolRequest.builder().name("getProcessState").arguments(Map.of()).build());

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
              CallToolRequest.builder().name("getProcessState").arguments(arguments).build());

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
                  .name("getProcessState")
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
}
