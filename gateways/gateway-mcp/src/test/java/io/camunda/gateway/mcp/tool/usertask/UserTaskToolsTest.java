/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.usertask;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gateway.mcp.tool.ToolsTest;
import io.camunda.gateway.protocol.model.UserTaskResult;
import io.camunda.gateway.protocol.model.UserTaskSearchQueryResult;
import io.camunda.gateway.protocol.model.UserTaskStateEnum;
import io.camunda.gateway.protocol.model.VariableResultBase;
import io.camunda.gateway.protocol.model.VariableSearchQueryResult;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
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

@ExtendWith(MockitoExtension.class)
@ContextConfiguration(classes = {UserTaskTools.class})
class UserTaskToolsTest extends ToolsTest {

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

  @Autowired private ObjectMapper objectMapper;
  @Captor private ArgumentCaptor<UserTaskQuery> userTaskQueryCaptor;
  @Captor private ArgumentCaptor<VariableQuery> variableQueryCaptor;

  @BeforeEach
  void mockApiServices() {
    mockApiServiceAuthentication(userTaskServices);
  }

  private void assertExampleUserTask(final UserTaskResult userTask) {
    assertThat(userTask.getUserTaskKey()).isEqualTo("5");
    assertThat(userTask.getName()).isEqualTo("Task Name");
    assertThat(userTask.getState()).isEqualTo(UserTaskStateEnum.CREATED);
    assertThat(userTask.getAssignee()).isEqualTo("john.doe");
    assertThat(userTask.getElementId()).isEqualTo("elementId");
    assertThat(userTask.getCandidateGroups()).containsExactly("group1", "group2");
    assertThat(userTask.getCandidateUsers()).containsExactly("user1", "user2");
    assertThat(userTask.getProcessDefinitionId()).isEqualTo("complexProcess");
    assertThat(userTask.getCreationDate()).isEqualTo("2024-05-23T23:05:00.000Z");
    assertThat(userTask.getTenantId()).isEqualTo("tenantId");
    assertThat(userTask.getProcessDefinitionVersion()).isEqualTo(2);
    assertThat(userTask.getCustomHeaders()).containsEntry("header1", "value1");
    assertThat(userTask.getPriority()).isEqualTo(50);
    assertThat(userTask.getProcessDefinitionKey()).isEqualTo("23");
    assertThat(userTask.getProcessInstanceKey()).isEqualTo("42");
    assertThat(userTask.getElementInstanceKey()).isEqualTo("17");
    assertThat(userTask.getFormKey()).isEqualTo("101");
    assertThat(userTask.getTags()).containsExactlyInAnyOrder("tag1", "tag2");
    assertThat(userTask.getProcessName()).isEqualTo("Process Name");
  }

  private void assertExampleVariable(final VariableResultBase variable) {
    assertThat(variable.getVariableKey()).isEqualTo("10");
    assertThat(variable.getName()).isEqualTo("varName");
    assertThat(variable.getScopeKey()).isEqualTo("101");
    assertThat(variable.getProcessInstanceKey()).isEqualTo("42");
    assertThat(variable.getTenantId()).isEqualTo("tenantId");
  }

  @Nested
  class GetUserTask {

    @Test
    void shouldGetUserTaskByKey() {
      // given
      when(userTaskServices.getByKey(anyLong())).thenReturn(USER_TASK_ENTITY);

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
          objectMapper.convertValue(result.structuredContent(), UserTaskResult.class);
      assertExampleUserTask(userTask);

      verify(userTaskServices).getByKey(5L);
    }

    @Test
    void shouldFailGetUserTaskByKeyOnException() {
      // given
      when(userTaskServices.getByKey(anyLong()))
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
      assertThat(result.content()).isEmpty();
      assertThat(result.structuredContent()).isNotNull();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getDetail()).isEqualTo("Expected failure");
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
      assertThat(problemDetail.getTitle()).isEqualTo("NOT_FOUND");
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
      when(userTaskServices.search(any(UserTaskQuery.class)))
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

      final var searchResult =
          objectMapper.convertValue(result.structuredContent(), UserTaskSearchQueryResult.class);
      assertThat(searchResult.getPage().getTotalItems()).isEqualTo(1L);
      assertThat(searchResult.getPage().getHasMoreTotalItems()).isFalse();
      assertThat(searchResult.getPage().getStartCursor()).isEqualTo("f");
      assertThat(searchResult.getPage().getEndCursor()).isEqualTo("v");
      assertThat(searchResult.getItems())
          .hasSize(1)
          .first()
          .satisfies(UserTaskToolsTest.this::assertExampleUserTask);

      verify(userTaskServices).search(userTaskQueryCaptor.capture());
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
    }

    @Test
    void shouldSearchUserTasksWithCreationDateRangeFilter() {
      // given
      when(userTaskServices.search(any(UserTaskQuery.class)))
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

      verify(userTaskServices).search(userTaskQueryCaptor.capture());
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
      when(userTaskServices.search(any(UserTaskQuery.class)))
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

      verify(userTaskServices).search(userTaskQueryCaptor.capture());
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
      when(userTaskServices.search(any(UserTaskQuery.class)))
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

      verify(userTaskServices).search(userTaskQueryCaptor.capture());
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
      when(userTaskServices.search(any(UserTaskQuery.class)))
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

      verify(userTaskServices).search(userTaskQueryCaptor.capture());
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
      when(userTaskServices.search(any(UserTaskQuery.class)))
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
      verify(userTaskServices).search(userTaskQueryCaptor.capture());
      final UserTaskQuery capturedQuery = userTaskQueryCaptor.getValue();
      assertThat(capturedQuery.filter().tenantIdOperations()).isEmpty();
      assertThat(capturedQuery.filter().candidateGroupOperations()).isEmpty();
      assertThat(capturedQuery.filter().candidateUserOperations()).isEmpty();
    }

    @Test
    void shouldFailSearchUserTasksOnException() {
      // given
      when(userTaskServices.search(any(UserTaskQuery.class)))
          .thenThrow(new ServiceException("Expected failure", Status.NOT_FOUND));

      // when
      final CallToolResult result =
          mcpClient.callTool(
              CallToolRequest.builder().name("searchUserTasks").arguments(Map.of()).build());

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
  }

  @Nested
  class AssignUserTask {

    @Test
    void shouldAssignUserTaskByKey() {
      // given
      when(userTaskServices.assignUserTask(anyLong(), anyString(), anyString(), anyBoolean()))
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

      verify(userTaskServices).assignUserTask(5L, "jane.doe", "assign", true);
    }

    @Test
    void shouldAssignUserTaskWithOptions() {
      // given
      when(userTaskServices.assignUserTask(anyLong(), anyString(), anyString(), anyBoolean()))
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

      verify(userTaskServices).assignUserTask(5L, "jane.doe", "claim", false);
    }

    @Test
    void shouldUnassignUserTaskWhenAssigneeIsMissing() {
      // given
      when(userTaskServices.unassignUserTask(anyLong(), anyString()))
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

      verify(userTaskServices).unassignUserTask(5L, "unassign");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void shouldUnassignUserTaskWhenAssigneeIsNullOrEmpty(final String assignee) {
      // given
      when(userTaskServices.unassignUserTask(anyLong(), anyString()))
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

      verify(userTaskServices).unassignUserTask(5L, "unassign");
    }

    @Test
    void shouldFailAssignUserTaskOnException() {
      // given
      when(userTaskServices.assignUserTask(anyLong(), anyString(), anyString(), anyBoolean()))
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
      assertThat(result.content()).isEmpty();
      assertThat(result.structuredContent()).isNotNull();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getDetail()).isEqualTo("Expected failure");
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
      assertThat(problemDetail.getTitle()).isEqualTo("NOT_FOUND");
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
      when(userTaskServices.searchUserTaskVariables(anyLong(), any(VariableQuery.class)))
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

      final var searchResult =
          objectMapper.convertValue(result.structuredContent(), VariableSearchQueryResult.class);
      assertThat(searchResult.getPage().getTotalItems()).isEqualTo(1L);
      assertThat(searchResult.getPage().getHasMoreTotalItems()).isFalse();
      assertThat(searchResult.getPage().getStartCursor()).isEqualTo("start");
      assertThat(searchResult.getPage().getEndCursor()).isEqualTo("end");
      assertThat(searchResult.getItems())
          .hasSize(1)
          .first()
          .satisfies(
              variable -> {
                assertExampleVariable(variable);
                assertThat(variable.getIsTruncated()).isTrue();
                assertThat(variable.getValue()).isEqualTo(TRUNCATED_VALUE);
              });

      verify(userTaskServices).searchUserTaskVariables(eq(5L), variableQueryCaptor.capture());
    }

    @Test
    void shouldSearchUserTaskVariablesWithoutTruncation() {
      // given
      when(userTaskServices.searchUserTaskVariables(anyLong(), any(VariableQuery.class)))
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

      final var searchResult =
          objectMapper.convertValue(result.structuredContent(), VariableSearchQueryResult.class);
      assertThat(searchResult.getItems())
          .hasSize(1)
          .first()
          .satisfies(
              variable -> {
                assertExampleVariable(variable);
                assertThat(variable.getIsTruncated()).isFalse();
                assertThat(variable.getValue()).isEqualTo(FULL_VALUE);
              });

      verify(userTaskServices).searchUserTaskVariables(eq(5L), variableQueryCaptor.capture());
    }

    @Test
    void shouldSearchUserTaskVariablesWithFilterSortAndPaging() {
      // given
      when(userTaskServices.searchUserTaskVariables(anyLong(), any(VariableQuery.class)))
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
                          Map.of("limit", 10, "after", "abc")))
                  .build());

      // then
      assertThat(result.isError()).isFalse();

      verify(userTaskServices).searchUserTaskVariables(eq(5L), variableQueryCaptor.capture());
      final VariableQuery capturedQuery = variableQueryCaptor.getValue();

      assertThat(capturedQuery.filter().nameOperations())
          .extracting(Operation::operator, Operation::value)
          .containsExactly(tuple(Operator.EQUALS, "varName"));

      assertThat(capturedQuery.sort().orderings())
          .extracting(FieldSorting::field, FieldSorting::order)
          .containsExactly(tuple("name", SortOrder.ASC));

      assertThat(capturedQuery.page().size()).isEqualTo(10);
      assertThat(capturedQuery.page().after()).isEqualTo("abc");
    }

    @Test
    void shouldFailSearchUserTaskVariablesOnException() {
      // given
      when(userTaskServices.searchUserTaskVariables(anyLong(), any(VariableQuery.class)))
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
      assertThat(result.content()).isEmpty();
      assertThat(result.structuredContent()).isNotNull();

      final var problemDetail =
          objectMapper.convertValue(result.structuredContent(), ProblemDetail.class);
      assertThat(problemDetail.getDetail()).isEqualTo("Expected failure");
      assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
      assertThat(problemDetail.getTitle()).isEqualTo("NOT_FOUND");
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
