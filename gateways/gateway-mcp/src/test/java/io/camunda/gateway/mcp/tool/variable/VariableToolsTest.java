/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mcp.tool.ToolsTest;
import io.camunda.gateway.protocol.model.VariableResult;
import io.camunda.gateway.protocol.model.VariableSearchQueryResult;
import io.camunda.gateway.protocol.model.VariableSearchResult;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import io.camunda.search.filter.UntypedOperation;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import io.camunda.service.VariableServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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

@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = {VariableTools.class})
class VariableToolsTest extends ToolsTest {

  static final String TRUNCATED_VALUE = "\\\"Lorem ipsum";
  static final String FULL_VALUE = "\\\"Lorem ipsum dolor sit amet\\\"";

  static final VariableEntity VARIABLE_ENTITY =
      new VariableEntity(
          123L,
          "demoVar",
          TRUNCATED_VALUE,
          FULL_VALUE,
          true,
          333L,
          789L,
          444L,
          "processId",
          "tenantId");

  static final SearchQueryResult<VariableEntity> SEARCH_QUERY_RESULT =
      new Builder<VariableEntity>()
          .total(1L)
          .items(List.of(VARIABLE_ENTITY))
          .startCursor("f")
          .endCursor("v")
          .build();

  @MockitoBean private VariableServices variableServices;

  @Autowired private ObjectMapper objectMapper;

  @Captor private ArgumentCaptor<VariableQuery> queryCaptor;

  @BeforeEach
  void mockApiServices() {
    mockApiServiceAuthentication(variableServices);
  }

  private void assertExampleVariable(final VariableResult variable) {
    assertThat(variable.getVariableKey()).isEqualTo("123");
    assertThat(variable.getName()).isEqualTo("demoVar");
    assertThat(variable.getValue()).isEqualTo(FULL_VALUE);
    assertThat(variable.getProcessInstanceKey()).isEqualTo("789");
    assertThat(variable.getTenantId()).isEqualTo("tenantId");
    assertThat(variable.getScopeKey()).isEqualTo("333");
  }

  private void assertExampleVariable(final VariableSearchResult variable) {
    assertThat(variable.getVariableKey()).isEqualTo("123");
    assertThat(variable.getName()).isEqualTo("demoVar");
    assertThat(variable.getProcessInstanceKey()).isEqualTo("789");
    assertThat(variable.getTenantId()).isEqualTo("tenantId");
    assertThat(variable.getScopeKey()).isEqualTo("333");
  }

  @Nested
  class GetVariable {

    @Test
    void shouldGetVariableByKey() {
      // given
      when(variableServices.getByKey(any())).thenReturn(VARIABLE_ENTITY);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getVariable")
                  .arguments(Map.of("variableKey", 123L))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      final var variable =
          objectMapper.convertValue(result.structuredContent(), VariableResult.class);
      assertExampleVariable(variable);

      verify(variableServices).getByKey(123L);
    }

    @Test
    void shouldFailGetVariableByKeyOnException() {
      // given
      when(variableServices.getByKey(any()))
          .thenThrow(new ServiceException("Expected failure", Status.NOT_FOUND));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getVariable")
                  .arguments(Map.of("variableKey", 123L))
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
    void shouldFailGetVariableByKeyOnInvalidKey() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getVariable")
                  .arguments(Map.of("variableKey", -3L))
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
                      .contains("Variable key must be a positive number."));
    }
  }

  @Nested
  class SearchVariables {

    @Test
    void shouldSearchVariablesWithNonTruncatedValue() {
      // given
      when(variableServices.search(any(VariableQuery.class))).thenReturn(SEARCH_QUERY_RESULT);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchVariables")
                  .arguments(
                      Map.of(
                          "filter",
                          Map.of(
                              "name", "demoVar", "value", FULL_VALUE, "processInstanceKey", "789"),
                          "sort",
                          List.of(Map.of("field", "variableKey", "order", "DESC")),
                          "page",
                          Map.of("limit", 25, "after", "WzEwMjRd"),
                          "truncateValues",
                          false))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      final var variables =
          objectMapper.convertValue(result.structuredContent(), VariableSearchQueryResult.class);
      assertThat(variables.getPage().getTotalItems()).isEqualTo(1L);
      assertThat(variables.getPage().getHasMoreTotalItems()).isFalse();
      assertThat(variables.getPage().getStartCursor()).isEqualTo("f");
      assertThat(variables.getPage().getEndCursor()).isEqualTo("v");
      assertThat(variables.getItems())
          .hasSize(1)
          .first()
          .satisfies(
              variable -> {
                assertExampleVariable(variable);
                assertThat(variable.getIsTruncated()).isFalse();
                assertThat(variable.getValue()).isEqualTo(FULL_VALUE);
              });
    }

    @Test
    void shouldSearchVariablesWithFilterSortAndPaging() {
      // given
      when(variableServices.search(any(VariableQuery.class))).thenReturn(SEARCH_QUERY_RESULT);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchVariables")
                  .arguments(
                      Map.of(
                          "filter",
                          Map.of(
                              "name", "demoVar", "value", FULL_VALUE, "processInstanceKey", "789"),
                          "sort",
                          List.of(Map.of("field", "variableKey", "order", "DESC")),
                          "page",
                          Map.of("limit", 25, "after", "WzEwMjRd")))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      final var variables =
          objectMapper.convertValue(result.structuredContent(), VariableSearchQueryResult.class);
      assertThat(variables.getPage().getTotalItems()).isEqualTo(1L);
      assertThat(variables.getPage().getHasMoreTotalItems()).isFalse();
      assertThat(variables.getPage().getStartCursor()).isEqualTo("f");
      assertThat(variables.getPage().getEndCursor()).isEqualTo("v");
      assertThat(variables.getItems())
          .hasSize(1)
          .first()
          .satisfies(
              variable -> {
                assertExampleVariable(variable);
                assertThat(variable.getIsTruncated()).isTrue();
                assertThat(variable.getValue()).isEqualTo(TRUNCATED_VALUE);
              });

      verify(variableServices).search(queryCaptor.capture());
      final VariableQuery capturedQuery = queryCaptor.getValue();

      final VariableFilter filter = capturedQuery.filter();
      assertThat(filter.nameOperations())
          .extracting(Operation::operator, Operation::value)
          .containsExactly(tuple(Operator.EQUALS, "demoVar"));

      assertThat(filter.valueOperations())
          .extracting(UntypedOperation::operator, UntypedOperation::value)
          .containsExactly(tuple(Operator.EQUALS, FULL_VALUE));

      assertThat(filter.processInstanceKeyOperations())
          .extracting(Operation::operator, Operation::value)
          .containsExactly(tuple(Operator.EQUALS, 789L));

      assertThat(capturedQuery.sort().orderings())
          .extracting(FieldSorting::field, FieldSorting::order)
          .containsExactly(tuple("variableKey", SortOrder.DESC));

      assertThat(capturedQuery.page().size()).isEqualTo(25);
      assertThat(capturedQuery.page().after()).isEqualTo("WzEwMjRd");
    }

    @Test
    void shouldFailSearchVariablesOnException() {
      // given
      when(variableServices.search(any(VariableQuery.class)))
          .thenThrow(new ServiceException("Expected failure", Status.NOT_FOUND));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder().name("searchVariables").arguments(Map.of()).build());

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
}
