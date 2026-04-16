/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.process.definition;

import static io.camunda.gateway.mcp.tool.CallToolResultAssertions.assertTextContentFallback;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.gateway.mapping.http.search.contract.StrictSearchQueryResult;
import io.camunda.gateway.mcp.OperationalToolsTest;
import io.camunda.gateway.protocol.model.ProcessDefinitionResult;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
@ContextConfiguration(classes = {ProcessDefinitionTools.class})
class ProcessDefinitionToolsTest extends OperationalToolsTest {

  static final ProcessDefinitionEntity PROCESS_DEFINITION_ENTITY =
      new ProcessDefinitionEntity(
          5L,
          "Complex Process",
          "complexProcess",
          "<bpmn />",
          "complexProcess.bpmn",
          2,
          "v2",
          "tenantId",
          "formId");

  static final SearchQueryResult<ProcessDefinitionEntity> SEARCH_QUERY_RESULT =
      new Builder<ProcessDefinitionEntity>()
          .total(1L)
          .items(List.of(PROCESS_DEFINITION_ENTITY))
          .startCursor("f")
          .endCursor("v")
          .build();

  @MockitoBean private ProcessDefinitionServices processDefinitionServices;

  @Autowired private JsonMapper objectMapper;
  @Captor private ArgumentCaptor<ProcessDefinitionQuery> queryCaptor;

  private void assertExampleProcessDefinitionResult(
      final ProcessDefinitionResult processDefinition) {
    assertThat(processDefinition.getProcessDefinitionKey()).isEqualTo("5");
    assertThat(processDefinition.getName()).isEqualTo("Complex Process");
    assertThat(processDefinition.getProcessDefinitionId()).isEqualTo("complexProcess");
    assertThat(processDefinition.getResourceName()).isEqualTo("complexProcess.bpmn");
    assertThat(processDefinition.getVersion()).isEqualTo(2);
    assertThat(processDefinition.getVersionTag()).isEqualTo("v2");
    assertThat(processDefinition.getTenantId()).isEqualTo("tenantId");
    assertThat(processDefinition.getHasStartForm()).isTrue();
  }

  @Nested
  class GetProcessDefinition {

    @Test
    void shouldGetProcessDefinitionByKey() {
      // given
      when(processDefinitionServices.getByKey(eq(5L), any())).thenReturn(PROCESS_DEFINITION_ENTITY);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getProcessDefinition")
                  .arguments(Map.of("processDefinitionKey", 5L))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      final var processDefinition =
          objectMapper.convertValue(result.structuredContent(), ProcessDefinitionResult.class);
      assertExampleProcessDefinitionResult(processDefinition);

      assertTextContentFallback(result);
    }

    @Test
    void shouldFailGetProcessDefinitionByKeyOnMissingKey() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder().name("getProcessDefinition").arguments(Map.of()).build());

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
                      .isEqualTo("processDefinitionKey: Process definition key must not be null."));
    }

    @Test
    void shouldFailGetProcessDefinitionByKeyOnNullKey() {
      // when
      final var arguments = new HashMap<String, Object>();
      arguments.put("processDefinitionKey", null);
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder().name("getProcessDefinition").arguments(arguments).build());

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
                      .isEqualTo("processDefinitionKey: Process definition key must not be null."));
    }

    @Test
    void shouldFailGetProcessDefinitionByKeyOnInvalidKey() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getProcessDefinition")
                  .arguments(Map.of("processDefinitionKey", -3L))
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
                          "processDefinitionKey: Process definition key must be a positive number."));
    }
  }

  @Nested
  class SearchProcessDefinitions {

    @Test
    void shouldSearchProcessDefinitionsWithFilterSortAndPaging() {
      // given
      when(processDefinitionServices.search(any(ProcessDefinitionQuery.class), any()))
          .thenReturn(SEARCH_QUERY_RESULT);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchProcessDefinitions")
                  .arguments(
                      Map.of(
                          "filter",
                          Map.of("processDefinitionId", "complexProcess"),
                          "sort",
                          List.of(Map.of("field", "processDefinitionKey", "order", "DESC")),
                          "page",
                          Map.of("limit", 25, "after", "WzEwMjRd")))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      @SuppressWarnings("unchecked")
      final StrictSearchQueryResult<ProcessDefinitionResult> response =
          (StrictSearchQueryResult<ProcessDefinitionResult>)
              objectMapper.convertValue(
                  result.structuredContent(),
                  objectMapper
                      .getTypeFactory()
                      .constructParametricType(
                          StrictSearchQueryResult.class, ProcessDefinitionResult.class));
      assertThat(response.page().totalItems()).isEqualTo(1L);
      assertThat(response.page().hasMoreTotalItems()).isFalse();
      assertThat(response.page().startCursor()).isEqualTo("f");
      assertThat(response.page().endCursor()).isEqualTo("v");
      assertThat(response.items())
          .hasSize(1)
          .first()
          .satisfies(ProcessDefinitionToolsTest.this::assertExampleProcessDefinitionResult);

      verify(processDefinitionServices).search(queryCaptor.capture(), any());
      final ProcessDefinitionQuery capturedQuery = queryCaptor.getValue();

      final ProcessDefinitionFilter filter = capturedQuery.filter();
      assertThat(filter.processDefinitionIdOperations())
          .extracting(Operation::operator, Operation::value)
          .containsExactly(tuple(Operator.EQUALS, "complexProcess"));

      assertThat(capturedQuery.sort().orderings())
          .extracting(FieldSorting::field, FieldSorting::order)
          .containsExactly(tuple("processDefinitionKey", SortOrder.DESC));

      assertThat(capturedQuery.page().size()).isEqualTo(25);
      assertThat(capturedQuery.page().after()).isEqualTo("WzEwMjRd");

      assertTextContentFallback(result);
    }

    @Test
    void shouldFailSearchProcessDefinitionsOnException() {
      // given
      when(processDefinitionServices.search(any(ProcessDefinitionQuery.class), any()))
          .thenThrow(new ServiceException("Expected failure", Status.NOT_FOUND));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchProcessDefinitions")
                  .arguments(Map.of())
                  .build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNotNull();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getDetail()).isEqualTo("Expected failure");
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
      assertThat(problemDetail.getTitle()).isEqualTo("Not Found");

      assertTextContentFallback(result);
    }

    @Test
    void shouldIgnoreTenantIdInFilter() {
      // given
      when(processDefinitionServices.search(any(ProcessDefinitionQuery.class), any()))
          .thenReturn(SEARCH_QUERY_RESULT);

      // when (tenantId passed in arguments should be ignored by MCP filter schema)
      mcpClient.callTool(
          CallToolRequest.builder()
              .name("searchProcessDefinitions")
              .arguments(Map.of("filter", Map.of("tenantId", "tenantId")))
              .build());

      // then
      verify(processDefinitionServices).search(queryCaptor.capture(), any());
      final ProcessDefinitionQuery capturedQuery = queryCaptor.getValue();
      assertThat(capturedQuery.filter().tenantIds()).isEmpty();
    }
  }

  @Nested
  class GetProcessDefinitionXml {

    @Test
    void shouldFailGetProcessDefinitionXmlByKeyOnMissingKey() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getProcessDefinitionXml")
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
                      .isEqualTo("processDefinitionKey: Process definition key must not be null."));
    }

    @Test
    void shouldFailGetProcessDefinitionXmlByKeyOnNullKey() {
      // when
      final var arguments = new HashMap<String, Object>();
      arguments.put("processDefinitionKey", null);
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getProcessDefinitionXml")
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
                      .isEqualTo("processDefinitionKey: Process definition key must not be null."));
    }

    @Test
    void shouldFailGetProcessDefinitionXmlByKeyOnInvalidKey() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getProcessDefinitionXml")
                  .arguments(Map.of("processDefinitionKey", -3L))
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
                          "processDefinitionKey: Process definition key must be a positive number."));
    }

    @Test
    void shouldGetProcessDefinitionXmlByKey() {
      // given
      when(processDefinitionServices.getProcessDefinitionXml(any(), any()))
          .thenReturn(Optional.of("<bpmn />"));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getProcessDefinitionXml")
                  .arguments(Map.of("processDefinitionKey", 5L))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNull();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent -> assertThat(textContent.text()).isEqualTo("<bpmn />"));
    }

    @Test
    void shouldReturnErrorWhenNoProcessDefinitionXmlAvailable() {
      // given
      when(processDefinitionServices.getProcessDefinitionXml(any(), any()))
          .thenReturn(Optional.empty());

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getProcessDefinitionXml")
                  .arguments(Map.of("processDefinitionKey", 5L))
                  .build());

      // then
      assertThat(result.isError()).isTrue();
      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getDetail())
          .isEqualTo("The BPMN XML for this process definition is not available.");
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
      assertThat(problemDetail.getTitle()).isEqualTo("Not Found");

      assertTextContentFallback(result);
    }
  }
}
