/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.usertask;

import static io.camunda.gateway.mcp.tool.ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.FILTER_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PAGE_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.SORT_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.TRUNCATE_VARIABLES_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.USER_TASK_KEY_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.USER_TASK_KEY_POSITIVE_MESSAGE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.VARIABLE_FILTER_FORMAT_NOTE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.VARIABLE_VALUE_RETURN_FORMAT;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.gateway.mcp.model.McpSearchQueryPageRequest;
import io.camunda.gateway.mcp.model.McpUserTaskAssignmentRequest;
import io.camunda.gateway.mcp.model.McpUserTaskFilter;
import io.camunda.gateway.protocol.model.UserTaskAssignmentRequest;
import io.camunda.gateway.protocol.model.UserTaskSearchQuerySortRequest;
import io.camunda.gateway.protocol.model.UserTaskVariableSearchQuerySortRequest;
import io.camunda.gateway.protocol.model.simple.UserTaskVariableFilter;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.UserTaskServices;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class UserTaskTools {

  private final UserTaskServices userTaskServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public UserTaskTools(
      final UserTaskServices userTaskServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.userTaskServices = userTaskServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaMcpTool(
      description =
          "Search for user tasks. " + VARIABLE_FILTER_FORMAT_NOTE + " " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult searchUserTasks(
      @McpToolParam(description = FILTER_DESCRIPTION, required = false)
          final McpUserTaskFilter filter,
      @McpToolParam(description = SORT_DESCRIPTION, required = false)
          final List<UserTaskSearchQuerySortRequest> sort,
      @McpToolParam(description = PAGE_DESCRIPTION, required = false)
          final McpSearchQueryPageRequest page) {
    try {
      final var userTaskSearchQuery = SearchQueryRequestMapper.toUserTaskQuery(filter, page, sort);

      if (userTaskSearchQuery.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(userTaskSearchQuery.getLeft());
      }

      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toUserTaskSearchQueryResponse(
              userTaskServices
                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
                  .search(userTaskSearchQuery.get())));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @CamundaMcpTool(
      description = "Get user task by key. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult getUserTask(
      @McpToolParam(description = USER_TASK_KEY_DESCRIPTION)
          @Positive(message = USER_TASK_KEY_POSITIVE_MESSAGE)
          final Long userTaskKey) {
    try {
      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toUserTask(
              userTaskServices
                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
                  .getByKey(userTaskKey)));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @CamundaMcpTool(
      description =
          "Assign or unassign a user task. Provide an assignee to assign the task, or omit/provide null to unassign it.")
  public CallToolResult assignUserTask(
      @McpToolParam(description = USER_TASK_KEY_DESCRIPTION)
          @Positive(message = USER_TASK_KEY_POSITIVE_MESSAGE)
          final Long userTaskKey,
      @McpToolParam(
              description =
                  "The assignee for the user task. Provide a value to assign the task to that user, or omit/provide null to unassign the task.",
              required = false)
          final String assignee,
      @McpToolParam(description = "Assignment options.", required = false)
          final McpUserTaskAssignmentRequest assignmentOptions) {
    try {
      if (assignee == null || assignee.isBlank()) {
        return performUnassignment(userTaskKey);
      } else {
        // merge assignee root param with potential assignment options
        UserTaskAssignmentRequest request = new UserTaskAssignmentRequest().assignee(assignee);
        if (assignmentOptions != null) {
          request =
              request
                  .allowOverride(assignmentOptions.getAllowOverride())
                  .action(assignmentOptions.getAction());
        }

        return performAssignment(userTaskKey, request);
      }
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  private CallToolResult performAssignment(
      final Long userTaskKey, final UserTaskAssignmentRequest request) {
    final var mappedRequest = RequestMapper.toUserTaskAssignmentRequest(request, userTaskKey);
    if (mappedRequest.isLeft()) {
      return CallToolResultMapper.mapProblemToResult(mappedRequest.getLeft());
    }

    final var assignmentRequest = mappedRequest.get();
    return CallToolResultMapper.fromPrimitive(
        userTaskServices
            .withAuthentication(authenticationProvider.getCamundaAuthentication())
            .assignUserTask(
                assignmentRequest.userTaskKey(),
                assignmentRequest.assignee(),
                assignmentRequest.action(),
                assignmentRequest.allowOverride()),
        r ->
            "User task with key %s assigned to %s."
                .formatted(assignmentRequest.userTaskKey(), assignmentRequest.assignee()));
  }

  private CallToolResult performUnassignment(final long userTaskKey) {
    final var unassignRequest = RequestMapper.toUserTaskUnassignmentRequest(userTaskKey);

    return CallToolResultMapper.fromPrimitive(
        userTaskServices
            .withAuthentication(authenticationProvider.getCamundaAuthentication())
            .unassignUserTask(unassignRequest.userTaskKey(), unassignRequest.action()),
        r -> "User task with key %s unassigned.".formatted(unassignRequest.userTaskKey()));
  }

  @CamundaMcpTool(
      description =
          "Search user task variables based on given criteria. "
              + VARIABLE_VALUE_RETURN_FORMAT
              + " "
              + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult searchUserTaskVariables(
      @McpToolParam(description = USER_TASK_KEY_DESCRIPTION)
          @Positive(message = USER_TASK_KEY_POSITIVE_MESSAGE)
          final Long userTaskKey,
      @McpToolParam(description = FILTER_DESCRIPTION, required = false)
          final UserTaskVariableFilter filter,
      @McpToolParam(description = SORT_DESCRIPTION, required = false)
          final List<UserTaskVariableSearchQuerySortRequest> sort,
      @McpToolParam(description = PAGE_DESCRIPTION, required = false)
          final McpSearchQueryPageRequest page,
      @McpToolParam(description = TRUNCATE_VARIABLES_DESCRIPTION, required = false)
          final Boolean truncateValues) {
    try {
      final var variableSearchQuery =
          SearchQueryRequestMapper.toUserTaskVariableQuery(filter, page, sort);

      if (variableSearchQuery.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(variableSearchQuery.getLeft());
      }

      final boolean shouldTruncate = truncateValues == null || truncateValues;
      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toVariableSearchQueryResponse(
              userTaskServices
                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
                  .searchUserTaskVariables(userTaskKey, variableSearchQuery.get()),
              shouldTruncate));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }
}
