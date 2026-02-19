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
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.config.tool.McpToolParamsUnwrapped;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.gateway.mcp.model.McpProcessInstanceCreationInstruction;
import io.camunda.gateway.mcp.model.McpProcessInstanceFilter;
import io.camunda.gateway.mcp.model.McpSearchQueryPageRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceSearchQuerySortRequest;
import io.camunda.gateway.protocol.model.simple.ProcessInstanceCreationInstruction;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ProcessInstanceServices;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Optional;
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
          of timeouts. Processes with wait states, like service tasks, user tasks, or defined listeners,
          are more likely to time out. You can increase the timeout to wait for completion by defining
          a longer requestTimeout.""")
  public CallToolResult createProcessInstance(
      @McpToolParamsUnwrapped @Valid
          final McpProcessInstanceCreationInstruction creationInstruction) {
    try {
      final var instruction =
          new ProcessInstanceCreationInstruction()
              .processDefinitionKey(creationInstruction.processDefinitionKey())
              .processDefinitionId(creationInstruction.processDefinitionId())
              .processDefinitionVersion(creationInstruction.processDefinitionVersion())
              .awaitCompletion(
                  creationInstruction.awaitCompletion() != null
                      && creationInstruction.awaitCompletion());

      Optional.ofNullable(creationInstruction.variables()).ifPresent(instruction::variables);
      Optional.ofNullable(creationInstruction.fetchVariables())
          .ifPresent(instruction::fetchVariables);
      Optional.ofNullable(creationInstruction.tags()).ifPresent(instruction::tags);
      Optional.ofNullable(creationInstruction.requestTimeout())
          .ifPresent(instruction::requestTimeout);
      Optional.ofNullable(creationInstruction.tenantId()).ifPresent(instruction::tenantId);

      final var request =
          SimpleRequestMapper.toCreateProcessInstance(
              instruction, multiTenancyCfg.isChecksEnabled());
      if (request.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(request.getLeft());
      }

      final var authenticatedServices =
          processInstanceServices.withAuthentication(
              authenticationProvider.getCamundaAuthentication());

      if (instruction.getAwaitCompletion()) {
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
