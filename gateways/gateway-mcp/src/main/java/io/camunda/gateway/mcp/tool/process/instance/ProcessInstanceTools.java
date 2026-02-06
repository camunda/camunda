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
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PROCESS_DEFINITION_KEY_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PROCESS_INSTANCE_KEY_POSITIVE_MESSAGE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.SORT_DESCRIPTION;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.SimpleRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.gateway.mcp.model.McpProcessInstanceFilter;
import io.camunda.gateway.mcp.model.McpSearchQueryPageRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceSearchQuerySortRequest;
import io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationInstruction;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ProcessInstanceServices;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

  @CamundaMcpTool(
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

  @CamundaMcpTool(
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

  @CamundaMcpTool(
      description =
          """
          Create a new process instance of the given process definition. Either a processDefinitionKey or
          a processDefinitionId (with an optional processDefinitionVersion) need to be passed.

          When using the awaitCompletion flag, the tool will wait for the process instance to complete
          and return its result variables. When using awaitCompletion, always include a unique tag
          `mcp-tool:<uniqueId>` which can be used to search for the started process instance in case
          of timeouts.""")
  public CallToolResult createProcessInstance(
      @McpToolParam(description = PROCESS_DEFINITION_KEY_DESCRIPTION, required = false)
          final String processDefinitionKey,
      @McpToolParam(
              description =
                  "The BPMN process id of the process definition to start an instance of.",
              required = false)
          @Pattern(regexp = "^[a-zA-Z_][a-zA-Z0-9_\\-.]*$")
          @Size(min = 1)
          final String processDefinitionId,
      @McpToolParam(
              description =
                  "The version of the process. By default, the latest version of the process is used. Can only be used in combination with processDefinitionId.",
              required = false)
          final Integer processDefinitionVersion,
      @McpToolParam(
              description =
                  "Set of variables to instantiate in the root variable scope of the process instance. Can include nested/complex objects. Which variables to set depends on the process definition.",
              required = false)
          final Map<@NotBlank String, Object> variables,
      @McpToolParam(
              description =
                  "Wait for the process instance to complete. If the process instance does not complete within request timeout limits, the waiting will time out and the tool will return a 504 response status. Use the unique tag you added to query process instance status. Disabled by default.",
              required = false)
          final Boolean awaitCompletion,
      @McpToolParam(
              description =
                  "List of variables by name to be included in the response when awaitCompletion is set to true. If empty, all visible variables in the root scope will be returned.",
              required = false)
          final List<@NotBlank String> fetchVariables,
      @McpToolParam(
              description =
                  "List of tags to apply to the process instance. Tags must start with a letter, followed by letters, digits, or the special characters `_`, `-`, `:`, or `.`; length ≤ 100.",
              required = false)
          final Set<
                  @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_\\-:.]{0,99}$") @Size(min = 1, max = 100)
                  String>
              tags) {
    try {
      final var instruction =
          new ProcessInstanceCreationInstruction()
              .processDefinitionKey(processDefinitionKey)
              .processDefinitionId(processDefinitionId)
              .processDefinitionVersion(processDefinitionVersion)
              .awaitCompletion(awaitCompletion != null && awaitCompletion);

      Optional.ofNullable(variables).ifPresent(instruction::variables);
      Optional.ofNullable(fetchVariables).ifPresent(instruction::fetchVariables);
      Optional.ofNullable(tags).ifPresent(instruction::tags);

      final var request =
          SimpleRequestMapper.toCreateProcessInstance(
              instruction, multiTenancyCfg.isChecksEnabled());
      if (request.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(request.getLeft());
      }

      final var authenticatedServices =
          processInstanceServices.withAuthentication(
              authenticationProvider.getCamundaAuthentication());

      if (Boolean.TRUE.equals(awaitCompletion)) {
        return CallToolResultMapper.from(
            authenticatedServices.createProcessInstanceWithResult(request.get()),
            ResponseMapper::toCreateProcessInstanceWithResultResponse);
      }

      return CallToolResultMapper.from(
          authenticatedServices.createProcessInstance(request.get()),
          ResponseMapper::toCreateProcessInstanceResponse);
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }
}
