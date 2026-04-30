/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.process.instance;

import static io.camunda.gateway.mcp.tool.ToolDescriptions.PROCESS_INSTANCE_KEY_NOT_NULL_MESSAGE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PROCESS_INSTANCE_KEY_POSITIVE_MESSAGE;

import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.ElementInstanceServices;
import io.camunda.service.IncidentServices;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.VariableServices;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.validation.annotation.Validated;

@Validated
public class ProcessStateTools {

  public static final String TOOL_NAME = "getProcessState";

  private final ProcessInstanceServices processInstanceServices;
  private final VariableServices variableServices;
  private final ElementInstanceServices elementInstanceServices;
  private final IncidentServices incidentServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ProcessStateTools(
      final ProcessInstanceServices processInstanceServices,
      final VariableServices variableServices,
      final ElementInstanceServices elementInstanceServices,
      final IncidentServices incidentServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.processInstanceServices = processInstanceServices;
    this.variableServices = variableServices;
    this.elementInstanceServices = elementInstanceServices;
    this.incidentServices = incidentServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaMcpTool(
      name = TOOL_NAME,
      title = "Get process state",
      description =
          """
          Get the current state of a process instance by its key.

          Returns the instance's state (ACTIVE, COMPLETED, TERMINATED), all variables, \
          currently active element instances, and any open incidents. \
          Use this after starting a process instance to monitor its progress \
          or to investigate failures.""",
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult getProcessState(
      @McpToolParam(description = "The key of the process instance to inspect.")
          @NotNull(message = PROCESS_INSTANCE_KEY_NOT_NULL_MESSAGE)
          @Positive(message = PROCESS_INSTANCE_KEY_POSITIVE_MESSAGE)
          final Long processInstanceKey) {
    try {
      return CallToolResultMapper.from(assembleProcessState(processInstanceKey));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  private Map<String, Object> assembleProcessState(final long processInstanceKey) {
    final CamundaAuthentication auth = authenticationProvider.getCamundaAuthentication();

    final var instance = processInstanceServices.getByKey(processInstanceKey, auth);

    final var variables =
        variableServices
            .search(
                VariableQuery.of(
                    b -> b.filter(f -> f.processInstanceKeys(processInstanceKey)).unlimited()),
                auth)
            .items()
            .stream()
            .map(
                v -> {
                  final Map<String, Object> varMap = new LinkedHashMap<>();
                  varMap.put("name", v.name());
                  varMap.put("value", v.value());
                  return varMap;
                })
            .toList();

    final var activeElements =
        elementInstanceServices
            .search(
                FlowNodeInstanceQuery.of(
                    b ->
                        b.filter(
                                f ->
                                    f.processInstanceKeys(processInstanceKey)
                                        .states(FlowNodeState.ACTIVE.name()))
                            .unlimited()),
                auth)
            .items()
            .stream()
            .map(
                fni -> {
                  final Map<String, Object> fniMap = new LinkedHashMap<>();
                  fniMap.put("flowNodeId", fni.flowNodeId());
                  fniMap.put("flowNodeName", fni.flowNodeName());
                  fniMap.put("type", fni.type() != null ? fni.type().name() : null);
                  return fniMap;
                })
            .toList();

    final var incidents =
        incidentServices
            .search(
                IncidentQuery.of(
                    b -> b.filter(f -> f.processInstanceKeys(processInstanceKey)).unlimited()),
                auth)
            .items()
            .stream()
            .map(
                inc -> {
                  final Map<String, Object> incMap = new LinkedHashMap<>();
                  incMap.put("incidentKey", inc.incidentKey());
                  incMap.put("errorType", inc.errorType() != null ? inc.errorType().name() : null);
                  incMap.put("errorMessage", inc.errorMessage());
                  incMap.put("flowNodeId", inc.flowNodeId());
                  return incMap;
                })
            .toList();

    final Map<String, Object> result = new LinkedHashMap<>();
    result.put("processInstanceKey", instance.processInstanceKey());
    result.put("state", instance.state() != null ? instance.state().name() : null);
    result.put("hasIncident", instance.hasIncident());
    result.put("variables", variables);
    result.put("activeElementInstances", activeElements);
    result.put("incidents", incidents);
    return result;
  }
}
