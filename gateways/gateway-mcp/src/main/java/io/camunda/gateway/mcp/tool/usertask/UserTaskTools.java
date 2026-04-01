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
import static io.camunda.gateway.mcp.tool.ToolDescriptions.USER_TASK_KEY_NOT_NULL_MESSAGE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.USER_TASK_KEY_POSITIVE_MESSAGE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.VARIABLE_FILTER_FORMAT_NOTE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.VARIABLE_VALUE_RETURN_FORMAT;

import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAdvancedDateTimeFilterStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIntegerFilterPropertyPlainValueStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedOffsetPaginationStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedStringFilterPropertyPlainValueStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedStringFilterPropertyStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskAssignmentRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskEffectiveVariableSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskFilterStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskStateFilterPropertyPlainValueStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskVariableFilterStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedUserTaskVariableSearchQuerySortRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedVariableValueFilterPropertyStrictContract;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.config.tool.McpToolParamsUnwrapped;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.gateway.mcp.model.McpDateRange;
import io.camunda.gateway.mcp.model.McpUserTaskAssignmentOptions;
import io.camunda.gateway.mcp.model.McpUserTaskFilter;
import io.camunda.gateway.mcp.model.McpUserTaskSearchQuery;
import io.camunda.gateway.mcp.model.McpUserTaskVariableFilterParam;
import io.camunda.gateway.mcp.model.McpVariableValue;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.UserTaskServices;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.http.HttpStatus;
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
      @McpToolParamsUnwrapped @Valid final McpUserTaskSearchQuery query) {
    try {
      final var strictRequest = toStrictContract(query);
      final var userTaskSearchQuery = SearchQueryRequestMapper.toUserTaskQueryStrict(strictRequest);

      if (userTaskSearchQuery.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(userTaskSearchQuery.getLeft());
      }

      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toUserTaskSearchQueryResponse(
              userTaskServices.search(
                  userTaskSearchQuery.get(), authenticationProvider.getCamundaAuthentication())));
    } catch (final IllegalArgumentException e) {
      return CallToolResultMapper.mapProblemToResult(
          GatewayErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_ARGUMENT"));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @CamundaMcpTool(
      description = "Get user task by key. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult getUserTask(
      @McpToolParam(description = USER_TASK_KEY_DESCRIPTION)
          @NotNull(message = USER_TASK_KEY_NOT_NULL_MESSAGE)
          @Positive(message = USER_TASK_KEY_POSITIVE_MESSAGE)
          final Long userTaskKey) {
    try {
      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toUserTask(
              userTaskServices.getByKey(
                  userTaskKey, authenticationProvider.getCamundaAuthentication())));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @CamundaMcpTool(
      description =
          "Assign or unassign a user task. Provide an assignee to assign the task, or omit/provide null to unassign it.")
  public CallToolResult assignUserTask(
      @McpToolParam(description = USER_TASK_KEY_DESCRIPTION)
          @NotNull(message = USER_TASK_KEY_NOT_NULL_MESSAGE)
          @Positive(message = USER_TASK_KEY_POSITIVE_MESSAGE)
          final Long userTaskKey,
      @McpToolParam(
              description =
                  "The assignee for the user task. Provide a value to assign the task to that user, or omit/provide null to unassign the task.",
              required = false)
          final String assignee,
      @McpToolParam(description = "Assignment options.", required = false)
          final McpUserTaskAssignmentOptions assignmentOptions) {
    try {
      if (assignee == null || assignee.isBlank()) {
        return performUnassignment(userTaskKey);
      } else {
        // merge assignee root param with potential assignment options
        final var request =
            new GeneratedUserTaskAssignmentRequestStrictContract(
                assignee,
                assignmentOptions != null ? assignmentOptions.allowOverride() : null,
                assignmentOptions != null ? assignmentOptions.action() : null);
        return performAssignment(userTaskKey, request);
      }
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  private CallToolResult performAssignment(
      final Long userTaskKey, final GeneratedUserTaskAssignmentRequestStrictContract request) {
    final var mappedRequest = RequestMapper.toUserTaskAssignmentRequest(request, userTaskKey);
    if (mappedRequest.isLeft()) {
      return CallToolResultMapper.mapProblemToResult(mappedRequest.getLeft());
    }

    final var assignmentRequest = mappedRequest.get();
    return CallToolResultMapper.fromPrimitive(
        userTaskServices.assignUserTask(
            assignmentRequest.userTaskKey(),
            assignmentRequest.assignee(),
            assignmentRequest.action(),
            assignmentRequest.allowOverride(),
            authenticationProvider.getCamundaAuthentication()),
        r ->
            "User task with key %s assigned to %s."
                .formatted(assignmentRequest.userTaskKey(), assignmentRequest.assignee()));
  }

  private CallToolResult performUnassignment(final long userTaskKey) {
    final var unassignRequest = RequestMapper.toUserTaskUnassignmentRequest(userTaskKey);

    return CallToolResultMapper.fromPrimitive(
        userTaskServices.unassignUserTask(
            unassignRequest.userTaskKey(),
            unassignRequest.action(),
            authenticationProvider.getCamundaAuthentication()),
        r -> "User task with key %s unassigned.".formatted(unassignRequest.userTaskKey()));
  }

  @CamundaMcpTool(
      description =
          "Search user task variables based on given criteria. Returns deduplicated variables where the innermost scope wins. "
              + VARIABLE_VALUE_RETURN_FORMAT
              + " "
              + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult searchUserTaskVariables(
      @McpToolParam(description = USER_TASK_KEY_DESCRIPTION)
          @NotNull(message = USER_TASK_KEY_NOT_NULL_MESSAGE)
          @Positive(message = USER_TASK_KEY_POSITIVE_MESSAGE)
          final Long userTaskKey,
      @McpToolParam(description = FILTER_DESCRIPTION, required = false)
          final McpUserTaskVariableFilterParam filter,
      @McpToolParam(description = SORT_DESCRIPTION, required = false)
          final List<GeneratedUserTaskVariableSearchQuerySortRequestStrictContract> sort,
      @McpToolParam(description = PAGE_DESCRIPTION, required = false)
          final GeneratedOffsetPaginationStrictContract page,
      @McpToolParam(description = TRUNCATE_VARIABLES_DESCRIPTION, required = false)
          final Boolean truncateValues) {
    try {
      final var strictRequest =
          new GeneratedUserTaskEffectiveVariableSearchQueryRequestStrictContract(
              page, sort, toStrictVariableFilter(filter));
      final var variableSearchQuery =
          SearchQueryRequestMapper.toUserTaskEffectiveVariableQueryStrict(strictRequest);

      if (variableSearchQuery.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(variableSearchQuery.getLeft());
      }

      final boolean shouldTruncate = truncateValues == null || truncateValues;
      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toVariableSearchQueryResponse(
              userTaskServices.searchUserTaskEffectiveVariables(
                  userTaskKey,
                  variableSearchQuery.get(),
                  authenticationProvider.getCamundaAuthentication()),
              shouldTruncate));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  // -- Facade → Strict contract conversion --

  private static GeneratedUserTaskSearchQueryRequestStrictContract toStrictContract(
      final McpUserTaskSearchQuery query) {
    return new GeneratedUserTaskSearchQueryRequestStrictContract(
        query.page(), query.sort(), toStrictFilter(query.filter()));
  }

  private static GeneratedUserTaskFilterStrictContract toStrictFilter(
      final McpUserTaskFilter filter) {
    if (filter == null) {
      return null;
    }
    return new GeneratedUserTaskFilterStrictContract(
        filter.state() != null
            ? new GeneratedUserTaskStateFilterPropertyPlainValueStrictContract(
                filter.state().getValue())
            : null,
        wrapString(filter.assignee()),
        filter.priority() != null
            ? new GeneratedIntegerFilterPropertyPlainValueStrictContract(filter.priority())
            : null,
        filter.elementId(),
        wrapString(filter.name()),
        null, // candidateGroup — not exposed in MCP
        null, // candidateUser — not exposed in MCP
        null, // tenantId — not exposed in MCP
        filter.processDefinitionId(),
        toStrictDateRange(filter.creationDate()),
        toStrictDateRange(filter.completionDate()),
        toStrictDateRange(filter.followUpDate()),
        toStrictDateRange(filter.dueDate()),
        toStrictVariableValueFilters(filter.processInstanceVariables()),
        toStrictVariableValueFilters(filter.localVariables()),
        filter.userTaskKey(),
        filter.processDefinitionKey(),
        filter.processInstanceKey(),
        filter.elementInstanceKey(),
        filter.tags());
  }

  private static List<GeneratedVariableValueFilterPropertyStrictContract>
      toStrictVariableValueFilters(final List<McpVariableValue> variables) {
    if (variables == null || variables.isEmpty()) {
      return null;
    }
    return variables.stream()
        .map(
            v ->
                new GeneratedVariableValueFilterPropertyStrictContract(
                    v.name(), wrapString(v.value())))
        .toList();
  }

  private static GeneratedStringFilterPropertyStrictContract wrapString(final String value) {
    return value != null ? new GeneratedStringFilterPropertyPlainValueStrictContract(value) : null;
  }

  private static GeneratedAdvancedDateTimeFilterStrictContract toStrictDateRange(
      final McpDateRange dateRange) {
    if (dateRange == null) {
      return null;
    }
    return new GeneratedAdvancedDateTimeFilterStrictContract(
        null, // $eq
        null, // $neq
        null, // $exists
        null, // $gt
        dateRange.from(), // $gte (from is inclusive)
        dateRange.to(), // $lt (to is exclusive)
        null, // $lte
        null // $in
        );
  }

  // -- Variable search: facade → strict contract conversion --

  private static GeneratedUserTaskVariableFilterStrictContract toStrictVariableFilter(
      final McpUserTaskVariableFilterParam filter) {
    if (filter == null) {
      return null;
    }
    return new GeneratedUserTaskVariableFilterStrictContract(wrapString(filter.name()));
  }
}
