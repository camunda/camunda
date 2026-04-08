/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.usertask;

import static io.camunda.gateway.mcp.tool.CallToolResultAssertions.assertTextContentFallback;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.gateway.mapping.http.search.contract.StrictSearchQueryResult;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskStateEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedVariableSearchStrictContract;
import io.camunda.gateway.mcp.OperationalToolsTest;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.UserTaskEntity.UserTaskState;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import io.camunda.search.filter.UntypedOperation;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.sort.SortOption.FieldSorting;
import io.camunda.search.sort.SortOrder;
import io.camunda.service.UserTaskServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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
@ContextConfiguration(classes = {UserTaskTools.class})
class UserTaskToolsTest extends OperationalToolsTest {

  static final UserTaskEntity USER_TASK_ENTITY =
      new UserTaskEntity(
          5L,
          "elementId",
          "Task Name",
          "complexProcess",
          "Process Name",
          OffsetDateTime.parse("2024-05-23T23:05:00.000Z"),
          null,
          "john.doe",
          UserTaskState.CREATED,
          101L,
          23L,
          42L,
          null,
          17L,
          "tenantId",
          null,
          null,
          List.of("group1", "group2"),
          List.of("user1", "user2"),
          null,
          2,
          Map.of("header1", "value1"),
          50,
          Set.of("tag1", "tag2"));

  static final SearchQueryResult<UserTaskEntity> USER_TASK_SEARCH_QUERY_RESULT =
      new Builder<UserTaskEntity>()
          .total(1L)
          .items(List.of(USER_TASK_ENTITY))
          .startCursor("f")
          .endCursor("v")
          .build();

  static final String TRUNCATED_VALUE = "\\\"Lorem ipsum";
  static final String FULL_VALUE = "\\\"Lorem ipsum dolor sit amet\\\"";

  static final VariableEntity VARIABLE_ENTITY =
      new VariableEntity(
          10L,
          "varName",
          TRUNCATED_VALUE,
          FULL_VALUE,
          true,
          101L,
          42L,
          null,
          "complexProcess",
          "tenantId");

  static final SearchQueryResult<VariableEntity> VARIABLE_SEARCH_QUERY_RESULT =
      new Builder<VariableEntity>()
          .total(1L)
          .items(List.of(VARIABLE_ENTITY))
          .startCursor("start")
          .endCursor("end")
          .build();

  @MockitoBean private UserTaskServices userTaskServices;

  @Autowired private JsonMapper objectMapper;
  @Captor private ArgumentCaptor<UserTaskQuery> userTaskQueryCaptor;
  @Captor private ArgumentCaptor<VariableQuery> variableQueryCaptor;

  private void assertExampleUserTask(final GeneratedUserTaskStrictContract userTask) {
    assertThat(userTask.userTaskKey()).isEqualTo("5");
    assertThat(userTask.name()).isEqualTo("Task Name");
    assertThat(userTask.state()).isEqualTo(GeneratedUserTaskStateEnum.CREATED);
    assertThat(userTask.assignee()).isEqualTo("john.doe");
    assertThat(userTask.elementId()).isEqualTo("elementId");
    assertThat(userTask.candidateGroups()).containsExactly("group1", "group2");
    assertThat(userTask.candidateUsers()).containsExactly("user1", "user2");
    assertThat(userTask.processDefinitionId()).isEqualTo("complexProcess");
    assertThat(userTask.creationDate()).isEqualTo("2024-05-23T23:05:00.000Z");
    assertThat(userTask.tenantId()).isEqualTo("tenantId");
    assertThat(userTask.processDefinitionVersion()).isEqualTo(2);
    assertThat(userTask.customHeaders()).containsEntry("header1", "value1");
    assertThat(userTask.priority()).isEqualTo(50);
    assertThat(userTask.processDefinitionKey()).isEqualTo("23");
    assertThat(userTask.processInstanceKey()).isEqualTo("42");
    assertThat(userTask.elementInstanceKey()).isEqualTo("17");
    assertThat(userTask.formKey()).isEqualTo("101");
    assertThat(userTask.tags()).containsExactlyInAnyOrder("tag1", "tag2");
    assertThat(userTask.processName()).isEqualTo("Process Name");
  }

  private void assertExampleVariable(final GeneratedVariableSearchStrictContract variable) {
    assertThat(variable.variableKey()).isEqualTo("10");
    assertThat(variable.name()).isEqualTo("varName");
    assertThat(variable.scopeKey()).isEqualTo("101");
    assertThat(variable.processInstanceKey()).isEqualTo("42");
    assertThat(variable.tenantId()).isEqualTo("tenantId");
  }

  @Nested
  class GetUserTask {

    @Test
    void shouldGetUserTaskByKey() {
      // given
      when(userTaskServices.getByKey(anyLong(), any())).thenReturn(USER_TASK_ENTITY);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getUserTask")
                  .arguments(Map.of("userTaskKey", 5L))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      final var userTask =
          objectMapper.convertValue(
              result.structuredContent(), GeneratedUserTaskStrictContract.class);
      assertExampleUserTask(userTask);

      verify(userTaskServices).getByKey(eq(5L), any());

      assertTextContentFallback(result);
    }

    @Test
    void shouldFailGetUserTaskByKeyOnException() {
      // given
      when(userTaskServices.getByKey(anyLong(), any()))
          .thenThrow(new ServiceException("Expected failure", Status.NOT_FOUND));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getUserTask")
                  .arguments(Map.of("userTaskKey", 5L))
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
    void shouldFailGetUserTaskByKeyOnMissingKey() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder().name("getUserTask").arguments(Map.of()).build());

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
                      .isEqualTo("userTaskKey: User task key must not be null."));
    }

    @Test
    void shouldFailGetUserTaskByKeyOnNullKey() {
      // when
      final var arguments = new HashMap<String, Object>();
      arguments.put("userTaskKey", null);
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder().name("getUserTask").arguments(arguments).build());

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
                      .isEqualTo("userTaskKey: User task key must not be null."));
    }

    @Test
    void shouldFailGetUserTaskByKeyOnInvalidKey() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("getUserTask")
                  .arguments(Map.of("userTaskKey", -3L))
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
                      .isEqualTo("userTaskKey: User task key must be a positive number."));
    }
  }

  @Nested
  class SearchUserTasks {

    @Test
    void shouldSearchUserTasksWithFilterSortAndPaging() {
      // given
      when(userTaskServices.search(any(UserTaskQuery.class), any()))
          .thenReturn(USER_TASK_SEARCH_QUERY_RESULT);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchUserTasks")
                  .arguments(
                      Map.of(
                          "filter",
                          Map.of("state", "CREATED", "assignee", "john.doe", "priority", 50),
                          "sort",
                          List.of(Map.of("field", "creationDate", "order", "DESC")),
                          "page",
                          Map.of("limit", 25, "after", "WzEwMjRd")))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      @SuppressWarnings("unchecked")
      final StrictSearchQueryResult<GeneratedUserTaskStrictContract> searchResult =
          (StrictSearchQueryResult<GeneratedUserTaskStrictContract>)
              objectMapper.convertValue(
                  result.structuredContent(),
                  objectMapper
                      .getTypeFactory()
                      .constructParametricType(
                          StrictSearchQueryResult.class, GeneratedUserTaskStrictContract.class));
      assertThat(searchResult.page().totalItems()).isEqualTo(1L);
      assertThat(searchResult.page().hasMoreTotalItems()).isFalse();
      assertThat(searchResult.page().startCursor()).isEqualTo("f");
      assertThat(searchResult.page().endCursor()).isEqualTo("v");
      assertThat(searchResult.items())
          .hasSize(1)
          .first()
          .satisfies(UserTaskToolsTest.this::assertExampleUserTask);

      verify(userTaskServices).search(userTaskQueryCaptor.capture(), any());
      final UserTaskQuery capturedQuery = userTaskQueryCaptor.getValue();

      final UserTaskFilter filter = capturedQuery.filter();
      assertThat(filter.stateOperations())
          .extracting(Operation::operator, Operation::value)
          .containsExactly(tuple(Operator.EQUALS, "CREATED"));

      assertThat(filter.assigneeOperations())
          .extracting(Operation::operator, Operation::value)
          .containsExactly(tuple(Operator.EQUALS, "john.doe"));

      assertThat(filter.priorityOperations())
          .extracting(Operation::operator, Operation::value)
          .containsExactly(tuple(Operator.EQUALS, 50));

      assertThat(capturedQuery.sort().orderings())
          .extracting(FieldSorting::field, FieldSorting::order)
          .containsExactly(tuple("creationDate", SortOrder.DESC));

      assertThat(capturedQuery.page().size()).isEqualTo(25);
      assertThat(capturedQuery.page().after()).isEqualTo("WzEwMjRd");

      assertTextContentFallback(result);
    }

    @Test
    void shouldSearchUserTasksWithCreationDateRangeFilter() {
      // given
      when(userTaskServices.search(any(UserTaskQuery.class), any()))
          .thenReturn(USER_TASK_SEARCH_QUERY_RESULT);

      final var creationDateFrom = OffsetDateTime.of(2025, 5, 23, 9, 35, 12, 0, ZoneOffset.UTC);
      final var creationDateTo = OffsetDateTime.of(2025, 12, 18, 17, 22, 33, 0, ZoneOffset.UTC);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchUserTasks")
                  .arguments(
                      Map.of(
                          "filter",
                          Map.of(
                              "creationDate",
                              Map.of(
                                  "from", "2025-05-23T09:35:12Z", "to", "2025-12-18T17:22:33Z"))))
                  .build());

      // then
      assertThat(result.isError()).isFalse();

      verify(userTaskServices).search(userTaskQueryCaptor.capture(), any());
      final UserTaskQuery capturedQuery = userTaskQueryCaptor.getValue();

      assertThat(capturedQuery.filter().creationDateOperations())
          .extracting(Operation::operator, Operation::value)
          .containsExactly(
              tuple(Operator.GREATER_THAN_EQUALS, creationDateFrom),
              tuple(Operator.LOWER_THAN, creationDateTo));
    }

    @Test
    void shouldSearchUserTasksWithCompletionDateRangeFilter() {
      // given
      when(userTaskServices.search(any(UserTaskQuery.class), any()))
          .thenReturn(USER_TASK_SEARCH_QUERY_RESULT);

      final var completionDateFrom = OffsetDateTime.of(2026, 1, 15, 10, 20, 30, 0, ZoneOffset.UTC);
      final var completionDateTo = OffsetDateTime.of(2026, 2, 28, 18, 45, 0, 0, ZoneOffset.UTC);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchUserTasks")
                  .arguments(
                      Map.of(
                          "filter",
                          Map.of(
                              "completionDate",
                              Map.of(
                                  "from", "2026-01-15T10:20:30Z", "to", "2026-02-28T18:45:00Z"))))
                  .build());

      // then
      assertThat(result.isError()).isFalse();

      verify(userTaskServices).search(userTaskQueryCaptor.capture(), any());
      final UserTaskQuery capturedQuery = userTaskQueryCaptor.getValue();

      assertThat(capturedQuery.filter().completionDateOperations())
          .extracting(Operation::operator, Operation::value)
          .containsExactly(
              tuple(Operator.GREATER_THAN_EQUALS, completionDateFrom),
              tuple(Operator.LOWER_THAN, completionDateTo));
    }

    @Test
    void shouldSearchUserTasksWithProcessInstanceVariablesFilter() {
      // given
      when(userTaskServices.search(any(UserTaskQuery.class), any()))
          .thenReturn(USER_TASK_SEARCH_QUERY_RESULT);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchUserTasks")
                  .arguments(
                      Map.of(
                          "filter",
                          Map.of(
                              "processInstanceVariables",
                              List.of(
                                  Map.of("name", "status", "value", "active"),
                                  Map.of("name", "priority", "value", "high")))))
                  .build());

      // then
      assertThat(result.isError()).isFalse();

      verify(userTaskServices).search(userTaskQueryCaptor.capture(), any());
      final UserTaskQuery capturedQuery = userTaskQueryCaptor.getValue();

      assertThat(capturedQuery.filter().processInstanceVariableFilter())
          .hasSize(2)
          .satisfiesExactlyInAnyOrder(
              filter -> {
                assertThat(filter.name()).isEqualTo("status");
                assertThat(filter.valueOperations())
                    .extracting(UntypedOperation::operator, UntypedOperation::value)
                    .containsExactly(tuple(Operator.EQUALS, "active"));
              },
              filter -> {
                assertThat(filter.name()).isEqualTo("priority");
                assertThat(filter.valueOperations())
                    .extracting(UntypedOperation::operator, UntypedOperation::value)
                    .containsExactly(tuple(Operator.EQUALS, "high"));
              });
    }

    @Test
    void shouldSearchUserTasksWithLocalVariablesFilter() {
      // given
      when(userTaskServices.search(any(UserTaskQuery.class), any()))
          .thenReturn(USER_TASK_SEARCH_QUERY_RESULT);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchUserTasks")
                  .arguments(
                      Map.of(
                          "filter",
                          Map.of(
                              "localVariables",
                              List.of(
                                  Map.of("name", "status", "value", "active"),
                                  Map.of("name", "priority", "value", "high")))))
                  .build());

      // then
      assertThat(result.isError()).isFalse();

      verify(userTaskServices).search(userTaskQueryCaptor.capture(), any());
      final UserTaskQuery capturedQuery = userTaskQueryCaptor.getValue();

      assertThat(capturedQuery.filter().localVariableFilters())
          .hasSize(2)
          .satisfiesExactlyInAnyOrder(
              filter -> {
                assertThat(filter.name()).isEqualTo("status");
                assertThat(filter.valueOperations())
                    .extracting(UntypedOperation::operator, UntypedOperation::value)
                    .containsExactly(tuple(Operator.EQUALS, "active"));
              },
              filter -> {
                assertThat(filter.name()).isEqualTo("priority");
                assertThat(filter.valueOperations())
                    .extracting(UntypedOperation::operator, UntypedOperation::value)
                    .containsExactly(tuple(Operator.EQUALS, "high"));
              });
    }

    @Test
    void shouldIgnoreTenantIdCandidateGroupAndCandidateUserInFilter() {
      // given
      when(userTaskServices.search(any(UserTaskQuery.class), any()))
          .thenReturn(USER_TASK_SEARCH_QUERY_RESULT);

      // when (tenantId, candidateGroup, candidateUser passed in arguments should be ignored by MCP
      // filter schema)
      mcpClient.callTool(
          CallToolRequest.builder()
              .name("searchUserTasks")
              .arguments(
                  Map.of(
                      "filter",
                      Map.of(
                          "tenantId",
                          "tenantId",
                          "candidateGroup",
                          "group1",
                          "candidateUser",
                          "user1")))
              .build());

      // then
      verify(userTaskServices).search(userTaskQueryCaptor.capture(), any());
      final UserTaskQuery capturedQuery = userTaskQueryCaptor.getValue();
      assertThat(capturedQuery.filter().tenantIdOperations()).isEmpty();
      assertThat(capturedQuery.filter().candidateGroupOperations()).isEmpty();
      assertThat(capturedQuery.filter().candidateUserOperations()).isEmpty();
    }

    @Test
    void shouldFailSearchUserTasksOnException() {
      // given
      when(userTaskServices.search(any(UserTaskQuery.class), any()))
          .thenThrow(new ServiceException("Expected failure", Status.NOT_FOUND));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder().name("searchUserTasks").arguments(Map.of()).build());

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
    void shouldRejectNonNumericProcessInstanceKey() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchUserTasks")
                  .arguments(Map.of("filter", Map.of("processInstanceKey", "abc")))
                  .build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNotNull();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(problemDetail.getTitle()).isEqualTo("Bad Request");
      assertThat(problemDetail.getDetail())
          .contains("processInstanceKey")
          .contains("illegal characters");

      assertTextContentFallback(result);
    }

    @Test
    void shouldRejectInvalidCreationDate() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchUserTasks")
                  .arguments(Map.of("filter", Map.of("creationDate", Map.of("from", "not-a-date"))))
                  .build());

      // then
      assertThat(result.isError()).isTrue();
      assertThat(result.structuredContent()).isNotNull();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
      assertThat(problemDetail.getTitle()).isEqualTo("Bad Request");
      assertThat(problemDetail.getDetail())
          .startsWith("The provided creationDate 'not-a-date' cannot be parsed as a date");

      assertTextContentFallback(result);
    }
  }

  @Nested
  class AssignUserTask {

    @Test
    void shouldAssignUserTaskByKey() {
      // given
      when(userTaskServices.assignUserTask(
              anyLong(), anyString(), anyString(), anyBoolean(), any()))
          .thenReturn(CompletableFuture.completedFuture(new UserTaskRecord()));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("assignUserTask")
                  .arguments(Map.of("userTaskKey", 5L, "assignee", "jane.doe"))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text())
                      .isEqualTo("User task with key 5 assigned to jane.doe."));

      verify(userTaskServices)
          .assignUserTask(eq(5L), eq("jane.doe"), eq("assign"), eq(true), any());
    }

    @Test
    void shouldAssignUserTaskWithOptions() {
      // given
      when(userTaskServices.assignUserTask(
              anyLong(), anyString(), anyString(), anyBoolean(), any()))
          .thenReturn(CompletableFuture.completedFuture(new UserTaskRecord()));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("assignUserTask")
                  .arguments(
                      Map.of(
                          "userTaskKey",
                          5L,
                          "assignee",
                          "jane.doe",
                          "assignmentOptions",
                          Map.of("allowOverride", false, "action", "claim")))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text())
                      .isEqualTo("User task with key 5 assigned to jane.doe."));

      verify(userTaskServices)
          .assignUserTask(eq(5L), eq("jane.doe"), eq("claim"), eq(false), any());
    }

    @Test
    void shouldUnassignUserTaskWhenAssigneeIsMissing() {
      // given
      when(userTaskServices.unassignUserTask(anyLong(), anyString(), any()))
          .thenReturn(CompletableFuture.completedFuture(new UserTaskRecord()));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("assignUserTask")
                  .arguments(Map.of("userTaskKey", 5L))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text()).isEqualTo("User task with key 5 unassigned."));

      verify(userTaskServices).unassignUserTask(eq(5L), eq("unassign"), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void shouldUnassignUserTaskWhenAssigneeIsNullOrEmpty(final String assignee) {
      // given
      when(userTaskServices.unassignUserTask(anyLong(), anyString(), any()))
          .thenReturn(CompletableFuture.completedFuture(new UserTaskRecord()));

      // when
      final var arguments = new LinkedHashMap<String, Object>(Map.of("userTaskKey", 5L));
      arguments.put("assignee", assignee);

      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder().name("assignUserTask").arguments(arguments).build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.content())
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              TextContent.class,
              textContent ->
                  assertThat(textContent.text()).isEqualTo("User task with key 5 unassigned."));

      verify(userTaskServices).unassignUserTask(eq(5L), eq("unassign"), any());
    }

    @Test
    void shouldFailAssignUserTaskOnException() {
      // given
      when(userTaskServices.assignUserTask(
              anyLong(), anyString(), anyString(), anyBoolean(), any()))
          .thenReturn(
              CompletableFuture.failedFuture(
                  new ServiceException("Expected failure", Status.NOT_FOUND)));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("assignUserTask")
                  .arguments(Map.of("userTaskKey", 5L, "assignee", "jane.doe"))
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
    void shouldFailAssignUserTaskOnMissingKey() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("assignUserTask")
                  .arguments(Map.of("assignee", "jane.doe"))
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
                      .isEqualTo("userTaskKey: User task key must not be null."));
    }

    @Test
    void shouldFailAssignUserTaskOnNullKey() {
      // when
      final var arguments = new HashMap<String, Object>();
      arguments.put("userTaskKey", null);
      arguments.put("assignee", "jane.doe");
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder().name("assignUserTask").arguments(arguments).build());

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
                      .isEqualTo("userTaskKey: User task key must not be null."));
    }

    @Test
    void shouldFailAssignUserTaskOnInvalidKey() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("assignUserTask")
                  .arguments(Map.of("userTaskKey", -3L, "assignee", "jane.doe"))
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
                      .isEqualTo("userTaskKey: User task key must be a positive number."));
    }
  }

  @Nested
  class SearchUserTaskVariables {

    @Test
    void shouldSearchUserTaskVariablesWithTruncation() {
      // given
      when(userTaskServices.searchUserTaskEffectiveVariables(
              anyLong(), any(VariableQuery.class), any()))
          .thenReturn(VARIABLE_SEARCH_QUERY_RESULT);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchUserTaskVariables")
                  .arguments(
                      Map.of(
                          "userTaskKey",
                          5L,
                          "filter",
                          Map.of("name", "varName"),
                          "truncateValues",
                          true))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      @SuppressWarnings("unchecked")
      final StrictSearchQueryResult<GeneratedVariableSearchStrictContract> searchResult =
          (StrictSearchQueryResult<GeneratedVariableSearchStrictContract>)
              objectMapper.convertValue(
                  result.structuredContent(),
                  objectMapper
                      .getTypeFactory()
                      .constructParametricType(
                          StrictSearchQueryResult.class,
                          GeneratedVariableSearchStrictContract.class));
      assertThat(searchResult.page().totalItems()).isEqualTo(1L);
      assertThat(searchResult.page().hasMoreTotalItems()).isFalse();
      assertThat(searchResult.items())
          .hasSize(1)
          .first()
          .satisfies(
              variable -> {
                assertExampleVariable(variable);
                assertThat(variable.isTruncated()).isTrue();
                assertThat(variable.value()).isEqualTo(TRUNCATED_VALUE);
              });

      verify(userTaskServices)
          .searchUserTaskEffectiveVariables(eq(5L), variableQueryCaptor.capture(), any());

      assertTextContentFallback(result);
    }

    @Test
    void shouldSearchUserTaskVariablesWithoutTruncation() {
      // given
      when(userTaskServices.searchUserTaskEffectiveVariables(
              anyLong(), any(VariableQuery.class), any()))
          .thenReturn(VARIABLE_SEARCH_QUERY_RESULT);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchUserTaskVariables")
                  .arguments(Map.of("userTaskKey", 5L, "truncateValues", false))
                  .build());

      // then
      assertThat(result.isError()).isFalse();
      assertThat(result.structuredContent()).isNotNull();

      @SuppressWarnings("unchecked")
      final StrictSearchQueryResult<GeneratedVariableSearchStrictContract> searchResult =
          (StrictSearchQueryResult<GeneratedVariableSearchStrictContract>)
              objectMapper.convertValue(
                  result.structuredContent(),
                  objectMapper
                      .getTypeFactory()
                      .constructParametricType(
                          StrictSearchQueryResult.class,
                          GeneratedVariableSearchStrictContract.class));
      assertThat(searchResult.items())
          .hasSize(1)
          .first()
          .satisfies(
              variable -> {
                assertExampleVariable(variable);
                assertThat(variable.isTruncated()).isFalse();
                assertThat(variable.value()).isEqualTo(FULL_VALUE);
              });

      verify(userTaskServices)
          .searchUserTaskEffectiveVariables(eq(5L), variableQueryCaptor.capture(), any());

      assertTextContentFallback(result);
    }

    @Test
    void shouldSearchUserTaskVariablesWithFilterSortAndPaging() {
      // given
      when(userTaskServices.searchUserTaskEffectiveVariables(
              anyLong(), any(VariableQuery.class), any()))
          .thenReturn(VARIABLE_SEARCH_QUERY_RESULT);

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchUserTaskVariables")
                  .arguments(
                      Map.of(
                          "userTaskKey",
                          5L,
                          "filter",
                          Map.of("name", "varName"),
                          "sort",
                          List.of(Map.of("field", "name", "order", "ASC")),
                          "page",
                          Map.of("limit", 10, "from", 5)))
                  .build());

      // then
      assertThat(result.isError()).isFalse();

      verify(userTaskServices)
          .searchUserTaskEffectiveVariables(eq(5L), variableQueryCaptor.capture(), any());
      final VariableQuery capturedQuery = variableQueryCaptor.getValue();

      assertThat(capturedQuery.filter().nameOperations())
          .extracting(Operation::operator, Operation::value)
          .containsExactly(tuple(Operator.EQUALS, "varName"));

      assertThat(capturedQuery.sort().orderings())
          .extracting(FieldSorting::field, FieldSorting::order)
          .containsExactly(tuple("name", SortOrder.ASC));

      assertThat(capturedQuery.page().size()).isEqualTo(10);
      assertThat(capturedQuery.page().from()).isEqualTo(5);
    }

    @Test
    void shouldFailSearchUserTaskVariablesOnException() {
      // given
      when(userTaskServices.searchUserTaskEffectiveVariables(
              anyLong(), any(VariableQuery.class), any()))
          .thenThrow(new ServiceException("Expected failure", Status.NOT_FOUND));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchUserTaskVariables")
                  .arguments(Map.of("userTaskKey", 5L))
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
    void shouldFailSearchUserTaskVariablesOnMissingKey() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchUserTaskVariables")
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
                      .isEqualTo("userTaskKey: User task key must not be null."));
    }

    @Test
    void shouldFailSearchUserTaskVariablesOnNullKey() {
      // when
      final var arguments = new HashMap<String, Object>();
      arguments.put("userTaskKey", null);
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchUserTaskVariables")
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
                      .isEqualTo("userTaskKey: User task key must not be null."));
    }

    @Test
    void shouldFailSearchUserTaskVariablesOnInvalidKey() {
      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder()
                  .name("searchUserTaskVariables")
                  .arguments(Map.of("userTaskKey", -3L))
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
                      .isEqualTo("userTaskKey: User task key must be a positive number."));
    }
  }
}
