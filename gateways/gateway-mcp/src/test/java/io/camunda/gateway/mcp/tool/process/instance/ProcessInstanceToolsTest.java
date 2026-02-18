/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.process.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mcp.tool.ToolsTest;
import io.camunda.gateway.protocol.model.CreateProcessInstanceResult;
import io.camunda.gateway.protocol.model.ProcessInstanceResult;
import io.camunda.gateway.protocol.model.ProcessInstanceSearchQueryResult;
import io.camunda.gateway.protocol.model.ProcessInstanceStateEnum;
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
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceCreateRequest;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ExtendWith(MockitoExtension.class)
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
  @MockitoBean private MultiTenancyConfiguration multiTenancyConfiguration;

  @Autowired private ObjectMapper objectMapper;

  @Captor private ArgumentCaptor<ProcessInstanceQuery> queryCaptor;
  @Captor private ArgumentCaptor<ProcessInstanceCreateRequest> createRequestCaptor;

  @BeforeEach
  void mockApiServices() {
    mockApiServiceAuthentication(processInstanceServices);
  }

  private void assertExampleProcessInstance(final ProcessInstanceResult processInstance) {
    assertThat(processInstance.getProcessInstanceKey()).isEqualTo("123");
    assertThat(processInstance.getProcessDefinitionId()).isEqualTo("demoProcess");
    assertThat(processInstance.getProcessDefinitionName()).isEqualTo("Demo Process");
    assertThat(processInstance.getProcessDefinitionVersion()).isEqualTo(5);
    assertThat(processInstance.getProcessDefinitionVersionTag()).isEqualTo("v5");
    assertThat(processInstance.getProcessDefinitionKey()).isEqualTo("789");
    assertThat(processInstance.getParentProcessInstanceKey()).isEqualTo("333");
    assertThat(processInstance.getParentElementInstanceKey()).isEqualTo("777");
    assertThat(processInstance.getStartDate()).isEqualTo("2024-01-01T00:00:00.000Z");
    assertThat(processInstance.getEndDate()).isNull();
    assertThat(processInstance.getState()).isEqualTo(ProcessInstanceStateEnum.ACTIVE);
    assertThat(processInstance.getHasIncident()).isFalse();
    assertThat(processInstance.getTenantId()).isEqualTo("tenant");
    assertThat(processInstance.getTags()).containsExactlyInAnyOrder("tag1", "tag2");
  }

  @Nested
  class GetProcessInstance {

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
      assertExampleProcessInstance(processInstance);

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
                      .isEqualTo(
                          "processInstanceKey: Process instance key must be a positive number."));
    }
  }

  @Nested
  class SearchProcessInstances {

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
      assertThat(result.structuredContent()).isNotNull();

      final var searchResult =
          objectMapper.convertValue(
              result.structuredContent(), ProcessInstanceSearchQueryResult.class);
      assertThat(searchResult.getPage().getTotalItems()).isEqualTo(1L);
      assertThat(searchResult.getPage().getHasMoreTotalItems()).isFalse();
      assertThat(searchResult.getPage().getStartCursor()).isEqualTo("f");
      assertThat(searchResult.getPage().getEndCursor()).isEqualTo("v");
      assertThat(searchResult.getItems())
          .hasSize(1)
          .first()
          .satisfies(ProcessInstanceToolsTest.this::assertExampleProcessInstance);

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

  @Nested
  class CreateProcessInstance {

    @Test
    void shouldCreateProcessInstanceByKey() {
      // given
      when(multiTenancyConfiguration.isChecksEnabled()).thenReturn(false);

      final var createResponse =
          new ProcessInstanceCreationRecord()
              .setProcessDefinitionKey(123L)
              .setBpmnProcessId("testProcessId")
              .setVersion(-1)
              .setProcessInstanceKey(456L)
              .setTenantId("<default>")
              .setTags(Set.of());

      when(processInstanceServices.createProcessInstance(any(ProcessInstanceCreateRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(createResponse));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createProcessInstance")
                  .arguments(
                      Map.of(
                          "processDefinitionKey",
                          "123",
                          "variables",
                          Map.of("foo", "bar"),
                          "tags",
                          Set.of("mcp-tool:abc")))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      verify(processInstanceServices).createProcessInstance(createRequestCaptor.capture());
      final var capturedRequest = createRequestCaptor.getValue();
      assertThat(capturedRequest.processDefinitionKey()).isEqualTo(123L);
      assertThat(capturedRequest.tenantId()).isEqualTo("<default>");
      assertThat(capturedRequest.variables()).containsExactly(entry("foo", "bar"));
      assertThat(capturedRequest.tags()).containsExactly("mcp-tool:abc");

      final var actualResult =
          objectMapper.convertValue(result.structuredContent(), CreateProcessInstanceResult.class);
      assertThat(actualResult.getProcessDefinitionKey()).isEqualTo("123");
      assertThat(actualResult.getProcessDefinitionId()).isEqualTo("testProcessId");
      assertThat(actualResult.getProcessDefinitionVersion()).isEqualTo(-1);
      assertThat(actualResult.getProcessInstanceKey()).isEqualTo("456");
      assertThat(actualResult.getTenantId()).isEqualTo("<default>");
      assertThat(actualResult.getVariables()).isEmpty();
      assertThat(actualResult.getTags()).isEmpty();
    }

    @Test
    void shouldCreateProcessInstanceById() {
      // given
      when(multiTenancyConfiguration.isChecksEnabled()).thenReturn(false);

      final var createResponse =
          new ProcessInstanceCreationRecord()
              .setProcessDefinitionKey(123L)
              .setBpmnProcessId("testProcessId")
              .setVersion(7)
              .setProcessInstanceKey(456L)
              .setTenantId("<default>")
              .setTags(Set.of("mcp-tool:abc"));

      when(processInstanceServices.createProcessInstance(any(ProcessInstanceCreateRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(createResponse));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createProcessInstance")
                  .arguments(
                      Map.of(
                          "processDefinitionId",
                          "testProcessId",
                          "processDefinitionVersion",
                          7,
                          "variables",
                          Map.of("foo", "bar"),
                          "tags",
                          Set.of("mcp-tool:abc")))
                  .build());

      // then
      assertThat(result.isError()).isFalse();

      verify(processInstanceServices).createProcessInstance(createRequestCaptor.capture());
      final var capturedRequest = createRequestCaptor.getValue();
      assertThat(capturedRequest.bpmnProcessId()).contains("testProcessId");
      assertThat(capturedRequest.version()).isEqualTo(7);
      assertThat(capturedRequest.tenantId()).isEqualTo("<default>");
      assertThat(capturedRequest.variables()).containsExactly(entry("foo", "bar"));
      assertThat(capturedRequest.awaitCompletion()).isFalse();
      assertThat(capturedRequest.tags()).containsExactly("mcp-tool:abc");

      final var actualResult =
          objectMapper.convertValue(result.structuredContent(), CreateProcessInstanceResult.class);
      assertThat(actualResult.getProcessDefinitionKey()).isEqualTo("123");
      assertThat(actualResult.getProcessDefinitionId()).isEqualTo("testProcessId");
      assertThat(actualResult.getProcessDefinitionVersion()).isEqualTo(7);
      assertThat(actualResult.getProcessInstanceKey()).isEqualTo("456");
      assertThat(actualResult.getTenantId()).isEqualTo("<default>");
      assertThat(actualResult.getVariables()).isEmpty();
      assertThat(actualResult.getTags()).containsExactly("mcp-tool:abc");
    }

    @Test
    void shouldCreateProcessInstanceWithAwaitCompletionAndFetchVariables() {
      // given
      when(multiTenancyConfiguration.isChecksEnabled()).thenReturn(false);

      final var variables = Map.of("foo", "bar", "nested", Map.of("x", 1));
      final var createResponse =
          new ProcessInstanceResultRecord()
              .setProcessDefinitionKey(123L)
              .setBpmnProcessId("testProcessId")
              .setVersion(7)
              .setProcessInstanceKey(456L)
              .setTenantId("<default>")
              .setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variables)))
              .setTags(Set.of("mcp-tool:abc"));

      when(processInstanceServices.createProcessInstanceWithResult(
              any(ProcessInstanceCreateRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(createResponse));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createProcessInstance")
                  .arguments(
                      Map.of(
                          "processDefinitionId",
                          "testProcessId",
                          "processDefinitionVersion",
                          7,
                          "variables",
                          variables,
                          "awaitCompletion",
                          true,
                          "fetchVariables",
                          List.of("foo"),
                          "tags",
                          Set.of("mcp-tool:abc")))
                  .build());

      // then
      assertThat(result.isError()).isFalse();

      verify(processInstanceServices)
          .createProcessInstanceWithResult(createRequestCaptor.capture());
      final var capturedRequest = createRequestCaptor.getValue();
      assertThat(capturedRequest.awaitCompletion()).isTrue();
      assertThat(capturedRequest.fetchVariables()).containsExactly("foo");

      final var actualResult =
          objectMapper.convertValue(result.structuredContent(), CreateProcessInstanceResult.class);
      assertThat(actualResult.getProcessDefinitionKey()).isEqualTo("123");
      assertThat(actualResult.getProcessDefinitionId()).isEqualTo("testProcessId");
      assertThat(actualResult.getProcessDefinitionVersion()).isEqualTo(7);
      assertThat(actualResult.getProcessInstanceKey()).isEqualTo("456");
      assertThat(actualResult.getTenantId()).isEqualTo("<default>");
      assertThat(actualResult.getVariables())
          .containsExactly(entry("foo", "bar"), entry("nested", Map.of("x", 1)));
      assertThat(actualResult.getTags()).containsExactly("mcp-tool:abc");
    }

    @Test
    void shouldCreateProcessInstanceWithAwaitCompletionAndRequestTimeout() {
      // given
      when(multiTenancyConfiguration.isChecksEnabled()).thenReturn(false);

      final var variables = Map.of("foo", "bar", "nested", Map.of("x", 1));
      final var createResponse =
          new ProcessInstanceResultRecord()
              .setProcessDefinitionKey(123L)
              .setBpmnProcessId("testProcessId")
              .setVersion(7)
              .setProcessInstanceKey(456L)
              .setTenantId("<default>")
              .setVariables(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(variables)))
              .setTags(Set.of("mcp-tool:abc"));

      when(processInstanceServices.createProcessInstanceWithResult(
              any(ProcessInstanceCreateRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(createResponse));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createProcessInstance")
                  .arguments(
                      Map.of(
                          "processDefinitionId",
                          "testProcessId",
                          "processDefinitionVersion",
                          7,
                          "variables",
                          variables,
                          "awaitCompletion",
                          true,
                          "requestTimeout",
                          60000L,
                          "tags",
                          Set.of("mcp-tool:abc")))
                  .build());

      // then
      assertThat(result.isError()).isFalse();

      verify(processInstanceServices)
          .createProcessInstanceWithResult(createRequestCaptor.capture());
      final var capturedRequest = createRequestCaptor.getValue();
      assertThat(capturedRequest.awaitCompletion()).isTrue();
      assertThat(capturedRequest.requestTimeout()).isEqualTo(60000L);

      final var actualResult =
          objectMapper.convertValue(result.structuredContent(), CreateProcessInstanceResult.class);
      assertThat(actualResult.getProcessDefinitionKey()).isEqualTo("123");
      assertThat(actualResult.getProcessDefinitionId()).isEqualTo("testProcessId");
      assertThat(actualResult.getProcessDefinitionVersion()).isEqualTo(7);
      assertThat(actualResult.getProcessInstanceKey()).isEqualTo("456");
      assertThat(actualResult.getTenantId()).isEqualTo("<default>");
      assertThat(actualResult.getVariables())
          .containsExactly(entry("foo", "bar"), entry("nested", Map.of("x", 1)));
      assertThat(actualResult.getTags()).containsExactly("mcp-tool:abc");
    }

    @Test
    void shouldFailCreateProcessInstanceOnException() {
      // given
      when(multiTenancyConfiguration.isChecksEnabled()).thenReturn(false);

      when(processInstanceServices.createProcessInstance(any(ProcessInstanceCreateRequest.class)))
          .thenThrow(new ServiceException("Expected failure", Status.INVALID_ARGUMENT));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createProcessInstance")
                  .arguments(Map.of("processDefinitionId", "invalidProcessId"))
                  .build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.content()).isEmpty();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getDetail()).isEqualTo("Expected failure");
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(problemDetail.getTitle()).isEqualTo("INVALID_ARGUMENT");
    }

    @Test
    void shouldFailCreateProcessInstanceWhenNoDefinitionProvided() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder().name("createProcessInstance").arguments(Map.of()).build());

      // then
      assertThat(result.isError()).isTrue();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(problemDetail.getTitle()).isEqualTo("INVALID_ARGUMENT");
      assertThat(problemDetail.getDetail())
          .contains("At least one of [processDefinitionId, processDefinitionKey] is required");
    }

    @Test
    void shouldFailCreateProcessInstanceWhenBothDefinitionKeyAndIdProvided() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createProcessInstance")
                  .arguments(
                      Map.of("processDefinitionKey", "123", "processDefinitionId", "testProcessId"))
                  .build());

      // then
      assertThat(result.isError()).isTrue();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(problemDetail.getTitle()).isEqualTo("INVALID_ARGUMENT");
      assertThat(problemDetail.getDetail())
          .contains("Only one of [processDefinitionId, processDefinitionKey] is allowed");
    }

    @Test
    void shouldCreateProcessInstanceByKeyWithMultiTenancy() {
      // given
      when(multiTenancyConfiguration.isChecksEnabled()).thenReturn(true);

      final var createResponse =
          new ProcessInstanceCreationRecord()
              .setProcessDefinitionKey(123L)
              .setBpmnProcessId("testProcessId")
              .setVersion(-1)
              .setProcessInstanceKey(456L)
              .setTenantId("tenant-a")
              .setTags(Set.of());

      when(processInstanceServices.createProcessInstance(any(ProcessInstanceCreateRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(createResponse));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createProcessInstance")
                  .arguments(
                      Map.of(
                          "processDefinitionKey",
                          "123",
                          "variables",
                          Map.of("foo", "bar"),
                          "tags",
                          Set.of("mcp-tool:abc"),
                          "tenantId",
                          "tenant-a"))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      verify(processInstanceServices).createProcessInstance(createRequestCaptor.capture());
      final var capturedRequest = createRequestCaptor.getValue();
      assertThat(capturedRequest.processDefinitionKey()).isEqualTo(123L);
      assertThat(capturedRequest.tenantId()).isEqualTo("tenant-a");
      assertThat(capturedRequest.variables()).containsExactly(entry("foo", "bar"));
      assertThat(capturedRequest.tags()).containsExactly("mcp-tool:abc");

      final var actualResult =
          objectMapper.convertValue(result.structuredContent(), CreateProcessInstanceResult.class);
      assertThat(actualResult.getProcessDefinitionKey()).isEqualTo("123");
      assertThat(actualResult.getProcessDefinitionId()).isEqualTo("testProcessId");
      assertThat(actualResult.getProcessDefinitionVersion()).isEqualTo(-1);
      assertThat(actualResult.getProcessInstanceKey()).isEqualTo("456");
      assertThat(actualResult.getTenantId()).isEqualTo("tenant-a");
      assertThat(actualResult.getVariables()).isEmpty();
      assertThat(actualResult.getTags()).isEmpty();
    }

    @Test
    void shouldCreateProcessInstanceByIdWithMultiTenancy() {
      // given
      when(multiTenancyConfiguration.isChecksEnabled()).thenReturn(true);

      final var createResponse =
          new ProcessInstanceCreationRecord()
              .setProcessDefinitionKey(123L)
              .setBpmnProcessId("testProcessId")
              .setVersion(7)
              .setProcessInstanceKey(456L)
              .setTenantId("tenant-a")
              .setTags(Set.of("mcp-tool:abc"));

      when(processInstanceServices.createProcessInstance(any(ProcessInstanceCreateRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(createResponse));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createProcessInstance")
                  .arguments(
                      Map.of(
                          "processDefinitionId",
                          "testProcessId",
                          "processDefinitionVersion",
                          7,
                          "variables",
                          Map.of("foo", "bar"),
                          "tags",
                          Set.of("mcp-tool:abc"),
                          "tenantId",
                          "tenant-a"))
                  .build());

      // then
      assertThat(result.isError()).isFalse();

      verify(processInstanceServices).createProcessInstance(createRequestCaptor.capture());
      final var capturedRequest = createRequestCaptor.getValue();
      assertThat(capturedRequest.bpmnProcessId()).contains("testProcessId");
      assertThat(capturedRequest.version()).isEqualTo(7);
      assertThat(capturedRequest.tenantId()).isEqualTo("tenant-a");
      assertThat(capturedRequest.variables()).containsExactly(entry("foo", "bar"));
      assertThat(capturedRequest.awaitCompletion()).isFalse();
      assertThat(capturedRequest.tags()).containsExactly("mcp-tool:abc");

      final var actualResult =
          objectMapper.convertValue(result.structuredContent(), CreateProcessInstanceResult.class);
      assertThat(actualResult.getProcessDefinitionKey()).isEqualTo("123");
      assertThat(actualResult.getProcessDefinitionId()).isEqualTo("testProcessId");
      assertThat(actualResult.getProcessDefinitionVersion()).isEqualTo(7);
      assertThat(actualResult.getProcessInstanceKey()).isEqualTo("456");
      assertThat(actualResult.getTenantId()).isEqualTo("tenant-a");
      assertThat(actualResult.getVariables()).isEmpty();
      assertThat(actualResult.getTags()).containsExactly("mcp-tool:abc");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void shouldFailCreateProcessInstanceWhenMultiTenancyEnabledButEmptyTenantProvided(
        final String tenantId) {
      // given
      when(multiTenancyConfiguration.isChecksEnabled()).thenReturn(true);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createProcessInstance")
                  .arguments(Map.of("processDefinitionKey", "123", "tenantId", tenantId))
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
                      .contains("tenantId: must match \"^(<default>|[A-Za-z0-9_@.+-]+)$\""));
    }

    @Test
    void shouldFailCreateProcessInstanceWhenMultiTenancyEnabledButNoTenantProvided() {
      // given
      when(multiTenancyConfiguration.isChecksEnabled()).thenReturn(true);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createProcessInstance")
                  .arguments(Map.of("processDefinitionKey", "123"))
                  .build());

      // then
      assertThat(result.isError()).isTrue();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(problemDetail.getTitle()).isEqualTo("INVALID_ARGUMENT");
      assertThat(problemDetail.getDetail())
          .contains(
              "Expected to handle request Create Process Instance with multi-tenancy enabled, but no tenant identifier was provided.");
    }

    @Test
    void shouldCreateProcessInstanceWithBusinessId() {
      // given
      when(multiTenancyConfiguration.isChecksEnabled()).thenReturn(false);

      final var businessId = "order-12345";
      final var createResponse =
          new ProcessInstanceCreationRecord()
              .setProcessDefinitionKey(123L)
              .setBpmnProcessId("testProcessId")
              .setVersion(-1)
              .setProcessInstanceKey(456L)
              .setTenantId("<default>")
              .setTags(Set.of())
              .setBusinessId(businessId);

      when(processInstanceServices.createProcessInstance(any(ProcessInstanceCreateRequest.class)))
          .thenReturn(CompletableFuture.completedFuture(createResponse));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("createProcessInstance")
                  .arguments(Map.of("processDefinitionKey", "123", "businessId", businessId))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      verify(processInstanceServices).createProcessInstance(createRequestCaptor.capture());
      final var capturedRequest = createRequestCaptor.getValue();
      assertThat(capturedRequest.processDefinitionKey()).isEqualTo(123L);
      assertThat(capturedRequest.businessId()).isEqualTo(businessId);

      final var actualResult =
          objectMapper.convertValue(result.structuredContent(), CreateProcessInstanceResult.class);
      assertThat(actualResult.getProcessInstanceKey()).isEqualTo("456");
      assertThat(actualResult.getBusinessId()).isEqualTo(businessId);
    }
  }
}
