/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.process.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mcp.tool.ToolsTest;
import io.camunda.gateway.protocol.model.ProcessInstanceResult;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.filter.UntypedOperation;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ContextConfiguration(classes = {ProcessInstanceTools.class})
class ProcessInstanceToolsTest extends ToolsTest {

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
          false,
          "tenant",
          "PI_123",
          Set.of("tag1", "tag2"));

  static final SearchQueryResult<ProcessInstanceEntity> SEARCH_QUERY_RESULT =
      new Builder<ProcessInstanceEntity>()
          .total(1L)
          .items(List.of(PROCESS_INSTANCE_ENTITY))
          .startCursor("f")
          .endCursor("v")
          .build();

  @MockitoBean private ProcessInstanceServices processInstanceServices;

  @Autowired private ObjectMapper objectMapper;

  @Captor private ArgumentCaptor<ProcessInstanceQuery> queryCaptor;

  @BeforeEach
  void mockApiServices() {
    mockApiServiceAuthentication(processInstanceServices);
  }

  @Test
  void shouldGetProcessInstanceByKey() {
    // given
    when(processInstanceServices.getByKey(any())).thenReturn(PROCESS_INSTANCE_ENTITY);

    // when
    final CallToolResult result =
        mcpClient.callTool(
            CallToolRequest.builder()
                .name("getProcessInstance")
                .arguments(Map.of("processInstanceKey", 123L))
                .build());

    // then
    assertThat(result.isError()).isFalse();
    assertThat(result.structuredContent()).isNotNull();

    final var processInstance =
        objectMapper.convertValue(result.structuredContent(), ProcessInstanceResult.class);
    assertThat(processInstance)
        .usingRecursiveComparison()
        .isEqualTo(SearchQueryResponseMapper.toProcessInstance(PROCESS_INSTANCE_ENTITY));

    verify(processInstanceServices).getByKey(123L);
  }

  @Test
  void shouldFailGetProcessInstanceByKeyOnException() {
    // given
    when(processInstanceServices.getByKey(any()))
        .thenThrow(new ServiceException("Expected failure", Status.NOT_FOUND));

    // when
    final CallToolResult result =
        mcpClient.callTool(
            CallToolRequest.builder()
                .name("getProcessInstance")
                .arguments(Map.of("processInstanceKey", 123L))
                .build());

    // then
    assertThat(result.isError()).isTrue();
    assertThat(result.content()).isEmpty();
    assertThat(result.structuredContent()).isNotNull();

    final var problemDetail =
        objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
    assertThat(problemDetail.getDetail()).isEqualTo("Expected failure");
    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(problemDetail.getTitle()).isEqualTo("NOT_FOUND");
  }

  @Test
  void shouldFailGetProcessInstanceByKeyOnInvalidKey() {
    // when
    final CallToolResult result =
        mcpClient.callTool(
            CallToolRequest.builder()
                .name("getProcessInstance")
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
                    .contains("Process instance key must be a positive number."));
  }

  @Test
  void shouldSearchProcessInstancesWithFilterSortAndPaging() {
    // given
    when(processInstanceServices.search(any(ProcessInstanceQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);

    // when
    final CallToolResult result =
        mcpClient.callTool(
            CallToolRequest.builder()
                .name("searchProcessInstances")
                .arguments(
                    Map.of(
                        "filter",
                        Map.of(
                            "state",
                            "ACTIVE",
                            "startDate",
                            Map.of("from", "2024-01-01T00:00:00Z", "to", "2024-02-01T00:00:00Z"),
                            "processDefinitionId",
                            "demoProcess",
                            "processDefinitionVersion",
                            5,
                            "tags",
                            List.of("tag1"),
                            "variables",
                            List.of(Map.of("name", "orderId", "value", "123"))),
                        "sort",
                        List.of(Map.of("field", "processInstanceKey", "order", "DESC")),
                        "page",
                        Map.of("limit", 25, "after", "WzEwMjRd")))
                .build());

    // then
    assertThat(result.isError()).isFalse();

    verify(processInstanceServices).search(queryCaptor.capture());
    final ProcessInstanceQuery capturedQuery = queryCaptor.getValue();

    final ProcessInstanceFilter filter = capturedQuery.filter();
    assertThat(filter.stateOperations())
        .extracting(Operation::operator, Operation::value)
        .containsExactly(tuple(Operator.EQUALS, "ACTIVE"));

    assertThat(filter.processDefinitionIdOperations())
        .extracting(Operation::operator, Operation::value)
        .containsExactly(tuple(Operator.EQUALS, "demoProcess"));

    assertThat(filter.processDefinitionVersionOperations())
        .extracting(Operation::operator, Operation::value)
        .containsExactly(tuple(Operator.EQUALS, 5));

    assertThat(filter.startDateOperations())
        .extracting(Operation::operator, Operation::value)
        .containsExactly(
            tuple(Operator.GREATER_THAN_EQUALS, OffsetDateTime.parse("2024-01-01T00:00:00Z")),
            tuple(Operator.LOWER_THAN, OffsetDateTime.parse("2024-02-01T00:00:00Z")));

    assertThat(filter.tags()).containsExactly("tag1");

    assertThat(filter.variableFilters()).hasSize(1);
    final VariableValueFilter variableFilter = filter.variableFilters().getFirst();
    assertThat(variableFilter.name()).isEqualTo("orderId");
    assertThat(variableFilter.valueOperations())
        .extracting(UntypedOperation::operator, UntypedOperation::value)
        .containsExactly(tuple(Operator.EQUALS, 123L));

    assertThat(capturedQuery.sort().orderings())
        .extracting(FieldSorting::field, FieldSorting::order)
        .containsExactly(tuple("processInstanceKey", SortOrder.DESC));

    assertThat(capturedQuery.page().size()).isEqualTo(25);
    assertThat(capturedQuery.page().after()).isEqualTo("WzEwMjRd");
  }

  @Test
  void shouldFailSearchProcessInstancesOnException() {
    // given
    when(processInstanceServices.search(any(ProcessInstanceQuery.class)))
        .thenThrow(new ServiceException("Expected failure", Status.NOT_FOUND));

    // when
    final CallToolResult result =
        mcpClient.callTool(
            CallToolRequest.builder().name("searchProcessInstances").arguments(Map.of()).build());

    // then
    assertThat(result.isError()).isTrue();
    assertThat(result.content()).isEmpty();

    final var problemDetail =
        objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
    assertThat(problemDetail.getDetail()).isEqualTo("Expected failure");
    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(problemDetail.getTitle()).isEqualTo("NOT_FOUND");
  }
}
