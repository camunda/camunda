/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.process.state;

import static io.camunda.gateway.mcp.tool.ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PROCESS_INSTANCE_KEY_NOT_NULL_MESSAGE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PROCESS_INSTANCE_KEY_POSITIVE_MESSAGE;

import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.ElementInstanceServices;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.VariableServices;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class ProcessStateTools {

  private final ProcessInstanceServices processInstanceServices;
  private final VariableServices variableServices;
  private final ElementInstanceServices elementInstanceServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ProcessStateTools(
      final ProcessInstanceServices processInstanceServices,
      final VariableServices variableServices,
      final ElementInstanceServices elementInstanceServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.processInstanceServices = processInstanceServices;
    this.variableServices = variableServices;
    this.elementInstanceServices = elementInstanceServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaMcpTool(
      description =
          "Get the current state of a process instance by its key. Returns the process state, "
              + "current variables, active element instances, and incident status. "
              + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult getProcessState(
      @McpToolParam(
              description =
                  "The assigned key of the process instance, which acts as a unique identifier for this process instance.")
          @NotNull(message = PROCESS_INSTANCE_KEY_NOT_NULL_MESSAGE)
          @Positive(message = PROCESS_INSTANCE_KEY_POSITIVE_MESSAGE)
          final Long processInstanceKey) {
    try {
      final var auth = authenticationProvider.getCamundaAuthentication();
      final var processInstance = processInstanceServices.getByKey(processInstanceKey, auth);
      final var variables = variableServices.search(variablesQuery(processInstanceKey), auth);
      final var activeElements =
          elementInstanceServices.search(activeElementInstancesQuery(processInstanceKey), auth);

      return CallToolResultMapper.from(
          new ProcessStateResult(
              SearchQueryResponseMapper.toProcessInstance(processInstance),
              SearchQueryResponseMapper.toVariableSearchQueryResponse(variables, false).getItems(),
              SearchQueryResponseMapper.toElementInstanceSearchQueryResponse(activeElements)
                  .getItems()));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  private static VariableQuery variablesQuery(final Long processInstanceKey) {
    return VariableQuery.of(b -> b.filter(f -> f.processInstanceKeys(processInstanceKey)));
  }

  private static FlowNodeInstanceQuery activeElementInstancesQuery(final Long processInstanceKey) {
    return FlowNodeInstanceQuery.of(
        b ->
            b.filter(
                f ->
                    f.processInstanceKeys(processInstanceKey).states(FlowNodeState.ACTIVE.name())));
  }
}
