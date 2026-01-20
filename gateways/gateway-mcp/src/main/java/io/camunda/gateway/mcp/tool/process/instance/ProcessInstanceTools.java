/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.process.instance;

import static io.camunda.gateway.mcp.tool.ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.FILTER_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PAGE_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PROCESS_INSTANCE_KEY_POSITIVE_MESSAGE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.SORT_DESCRIPTION;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.SimpleRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.gateway.mcp.model.McpProcessInstanceFilter;
import io.camunda.gateway.mcp.model.McpSearchQueryPageRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceSearchQuerySortRequest;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ProcessInstanceServices;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class ProcessInstanceTools {

  private final ProcessInstanceServices processInstanceServices;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ProcessInstanceTools(
      final ProcessInstanceServices processInstanceServices,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.processInstanceServices = processInstanceServices;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
  }

  @McpTool(
      description = "Search for process instances. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult searchProcessInstances(
      @McpToolParam(description = FILTER_DESCRIPTION, required = false)
          final McpProcessInstanceFilter filter,
      @McpToolParam(description = SORT_DESCRIPTION, required = false)
          final List<ProcessInstanceSearchQuerySortRequest> sort,
      @McpToolParam(description = PAGE_DESCRIPTION, required = false)
          final McpSearchQueryPageRequest page) {
    try {
      final var query = SearchQueryRequestMapper.toProcessInstanceQuery(filter, page, sort);
      if (query.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(query.getLeft());
      }

      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toProcessInstanceSearchQueryResponse(
              processInstanceServices
                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
                  .search(query.get())));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @McpTool(
      description = "Get process instance by key. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult getProcessInstance(
      @McpToolParam(
              description =
                  "The assigned key of the process instance, which acts as a unique identifier for this process instance.")
          @Positive(message = PROCESS_INSTANCE_KEY_POSITIVE_MESSAGE)
          final Long processInstanceKey) {
    try {
      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toProcessInstance(
              processInstanceServices
                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
                  .getByKey(processInstanceKey)));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @McpTool(description = "Create process instance.")
  public CallToolResult createProcessInstance(
      @McpToolParam(
              description =
                  "The unique key identifying the process definition, for example, returned for a process in the deploy resources endpoint. ",
              required = false)
          final String processDefinitionKey,
      @McpToolParam(
              description =
                  "The BPMN process id of the process definition to start an instance of. ",
              required = false)
          final String processDefinitionId,
      @McpToolParam(
              description =
                  "The version of the process. By default, the latest version of the process is used. ",
              required = false)
          final Integer processDefinitionVersion,
      @McpToolParam(
              description =
                  "JSON object that will instantiate the variables for the root variable scope of the process instance. ",
              required = false)
          final Map<String, Object> variables,
      @McpToolParam(
              description =
                  "Wait for the process instance to complete. If the process instance completion does not occur within the requestTimeout, the request will be closed. This can lead to a 504 response status. Disabled by default. ",
              required = false)
          final Boolean awaitCompletion,
      @McpToolParam(
              description =
                  "Timeout (in ms) the request waits for the process to complete. By default or when set to 0, the generic request timeout configured in the cluster is applied. ",
              required = false)
          final Long requestTimeout,
      @McpToolParam(
              description =
                  "List of variables by name to be included in the response when awaitCompletion is set to true. If empty, all visible variables in the root scope will be returned. ",
              required = false)
          final List<String> fetchVariables,
      @McpToolParam(
              description =
                  "List of tags. Tags need to start with a letter; then alphanumerics, `_`, `-`, `:`, or `.`; length ≤ 100.",
              required = false)
          final Set<String> tags) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      final var tenantId =
          authentication.authenticatedTenantIds() == null
                  || authentication.authenticatedTenantIds().isEmpty()
              ? null
              : authentication.authenticatedTenantIds().getFirst();

      final var instruction =
          new io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationInstruction()
              .processDefinitionKey(processDefinitionKey)
              .processDefinitionId(processDefinitionId)
              .tenantId(tenantId);

      if (processDefinitionVersion != null) {
        instruction.processDefinitionVersion(processDefinitionVersion);
      }
      if (variables != null) {
        instruction.variables(variables);
      }

      if (awaitCompletion != null) {
        instruction.awaitCompletion(awaitCompletion);
      }

      if (requestTimeout != null) {
        instruction.requestTimeout(requestTimeout);
      }

      if (fetchVariables != null) {
        instruction.fetchVariables(fetchVariables);
      }

      if (tags != null) {
        instruction.tags(tags);
      }

      final var request =
          SimpleRequestMapper.toCreateProcessInstance(
              instruction, multiTenancyCfg.isChecksEnabled());
      if (request.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(request.getLeft());
      }

      final var svc = processInstanceServices.withAuthentication(authentication);

      if (Boolean.TRUE.equals(awaitCompletion)) {
        return CallToolResultMapper.from(
            svc.createProcessInstanceWithResult(request.get()),
            ResponseMapper::toCreateProcessInstanceWithResultResponse);
      }

      return CallToolResultMapper.from(
          svc.createProcessInstance(request.get()),
          ResponseMapper::toCreateProcessInstanceResponse);
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }
}
