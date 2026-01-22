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
import static io.camunda.gateway.mcp.tool.ToolDescriptions.USER_TASK_KEY_POSITIVE_MESSAGE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.VARIABLE_FORMAT_DESCRIPTION;

import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.gateway.mcp.model.McpSearchQueryPageRequest;
import io.camunda.gateway.mcp.model.McpUserTaskAssignmentRequest;
import io.camunda.gateway.mcp.model.McpUserTaskFilter;
import io.camunda.gateway.mcp.model.McpVariableFilter;
import io.camunda.gateway.protocol.model.UserTaskAssignmentRequest;
import io.camunda.gateway.protocol.model.UserTaskSearchQuerySortRequest;
import io.camunda.gateway.protocol.model.VariableSearchQuerySortRequest;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.UserTaskServices;
import io.camunda.service.VariableServices;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class UserTaskTools {

  private final UserTaskServices userTaskServices;
  private final VariableServices variableServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public UserTaskTools(
      final UserTaskServices userTaskServices,
      final VariableServices variableServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.userTaskServices = userTaskServices;
    this.variableServices = variableServices;
    this.authenticationProvider = authenticationProvider;
  }

  @McpTool(
      description = "Search for user tasks. " + EVENTUAL_CONSISTENCY_NOTE,
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

  @McpTool(
      description = "Get user task by key. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult getUserTask(
      @McpToolParam(description = "The user task key.")
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

  @McpTool(description = "Assign user task to an assignee. " + EVENTUAL_CONSISTENCY_NOTE)
  public CallToolResult assignUserTask(
      @McpToolParam(description = "The key of the user task to assign.")
          @Positive(message = USER_TASK_KEY_POSITIVE_MESSAGE)
          final Long userTaskKey,
      @McpToolParam(
              description =
                  "The assignee for the user task. The assignee must not be empty or `null`.")
          @NotBlank(message = "Assignee must not be blank.")
          final String assignee,
      @McpToolParam(description = "Assignment options.", required = false)
          final McpUserTaskAssignmentRequest options) {
    try {
      // Enrich the options request with the assignee from the root param
      final UserTaskAssignmentRequest request =
          options != null ? options : new UserTaskAssignmentRequest();
      request.setAssignee(assignee);

      final boolean allowOverride =
          request.getAllowOverride() == null || request.getAllowOverride();

      return CallToolResultMapper.fromPrimitive(
          userTaskServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .assignUserTask(userTaskKey, assignee, request.getAction(), allowOverride),
          r -> "User task with key %s assigned to %s.".formatted(userTaskKey, assignee));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @McpTool(
      description =
          "Search user task variables based on given criteria. "
              + VARIABLE_FORMAT_DESCRIPTION
              + " The value may be truncated depending on the truncateValues parameter. "
              + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult searchUserTaskVariables(
      @McpToolParam(description = "The key of the user task.")
          @Positive(message = USER_TASK_KEY_POSITIVE_MESSAGE)
          final Long userTaskKey,
      @McpToolParam(description = FILTER_DESCRIPTION, required = false)
          final McpVariableFilter filter,
      @McpToolParam(description = SORT_DESCRIPTION, required = false)
          final List<VariableSearchQuerySortRequest> sort,
      @McpToolParam(description = PAGE_DESCRIPTION, required = false)
          final McpSearchQueryPageRequest page,
      @McpToolParam(
              description =
                  "When true (default), long variable values in the response are truncated. When false, full variable values are returned.",
              required = false)
          final Boolean truncateValues) {
    try {
      final var variableSearchQuery = SearchQueryRequestMapper.toVariableQuery(filter, page, sort);

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
