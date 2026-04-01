/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.process.instance;

import static io.camunda.gateway.mcp.tool.ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PROCESS_INSTANCE_KEY_NOT_NULL_MESSAGE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PROCESS_INSTANCE_KEY_POSITIVE_MESSAGE;

import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedAdvancedDateTimeFilterStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedIntegerFilterPropertyPlainValueStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionKeyFilterPropertyPlainValueStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceCreationInstructionByIdStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceCreationInstructionByKeyStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceCreationInstructionStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceFilterStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceKeyFilterPropertyPlainValueStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessInstanceStateFilterPropertyPlainValueStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedStringFilterPropertyPlainValueStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedStringFilterPropertyStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedVariableValueFilterPropertyStrictContract;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.config.tool.McpToolParamsUnwrapped;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.gateway.mcp.model.McpDateRange;
import io.camunda.gateway.mcp.model.McpProcessInstanceCreation;
import io.camunda.gateway.mcp.model.McpProcessInstanceFilter;
import io.camunda.gateway.mcp.model.McpProcessInstanceSearchQuery;
import io.camunda.gateway.mcp.model.McpVariableValue;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ProcessInstanceServices;
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
      @McpToolParamsUnwrapped @Valid final McpProcessInstanceSearchQuery query) {
    try {
      final var strictRequest = toStrictContract(query);
      final var processInstanceQuery =
          SearchQueryRequestMapper.toProcessInstanceQueryStrict(strictRequest);
      if (processInstanceQuery.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(processInstanceQuery.getLeft());
      }

      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toProcessInstanceSearchQueryResponse(
              processInstanceServices.search(
                  processInstanceQuery.get(), authenticationProvider.getCamundaAuthentication())));
    } catch (final IllegalArgumentException e) {
      return CallToolResultMapper.mapProblemToResult(
          GatewayErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_ARGUMENT"));
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
          @NotNull(message = PROCESS_INSTANCE_KEY_NOT_NULL_MESSAGE)
          @Positive(message = PROCESS_INSTANCE_KEY_POSITIVE_MESSAGE)
          final Long processInstanceKey) {
    try {
      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toProcessInstance(
              processInstanceServices.getByKey(
                  processInstanceKey, authenticationProvider.getCamundaAuthentication())));
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
      @McpToolParamsUnwrapped @Valid final McpProcessInstanceCreation creationInstruction) {
    try {
      final var validationError = validateCreationInstruction(creationInstruction);
      if (validationError != null) {
        return CallToolResultMapper.mapProblemToResult(validationError);
      }

      final var strict = toStrictCreationInstruction(creationInstruction);
      final var request =
          RequestMapper.toCreateProcessInstance(strict, multiTenancyCfg.isChecksEnabled());
      if (request.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(request.getLeft());
      }

      final var processInstanceCreateRequest = request.get();
      final var camundaAuthentication = authenticationProvider.getCamundaAuthentication();
      if (Boolean.TRUE.equals(processInstanceCreateRequest.awaitCompletion())) {
        return CallToolResultMapper.from(
            processInstanceServices.createProcessInstanceWithResult(
                processInstanceCreateRequest, camundaAuthentication),
            ResponseMapper::toCreateProcessInstanceWithResultResponse);
      }

      return CallToolResultMapper.from(
          processInstanceServices.createProcessInstance(
              processInstanceCreateRequest, camundaAuthentication),
          ResponseMapper::toCreateProcessInstanceResponse);
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  // -- Facade → Strict contract conversion --

  private static GeneratedProcessInstanceSearchQueryRequestStrictContract toStrictContract(
      final McpProcessInstanceSearchQuery query) {
    return new GeneratedProcessInstanceSearchQueryRequestStrictContract(
        query.page(), query.sort(), toStrictFilter(query.filter()));
  }

  private static GeneratedProcessInstanceFilterStrictContract toStrictFilter(
      final McpProcessInstanceFilter filter) {
    if (filter == null) {
      return null;
    }
    return new GeneratedProcessInstanceFilterStrictContract(
        toStrictDateRange(filter.startDate()),
        toStrictDateRange(filter.endDate()),
        filter.state() != null
            ? new GeneratedProcessInstanceStateFilterPropertyPlainValueStrictContract(
                filter.state().getValue())
            : null,
        filter.hasIncident(),
        null, // tenantId — not exposed in MCP
        toStrictVariableValueFilters(filter.variables()),
        filter.processInstanceKey() != null
            ? new GeneratedProcessInstanceKeyFilterPropertyPlainValueStrictContract(
                filter.processInstanceKey())
            : null,
        null, // parentProcessInstanceKey — not exposed in MCP
        null, // parentElementInstanceKey — not exposed in MCP
        null, // batchOperationId — not exposed in MCP
        null, // batchOperationKey — not exposed in MCP
        null, // errorMessage — not exposed in MCP
        null, // hasRetriesLeft — not exposed in MCP
        null, // elementInstanceState — not exposed in MCP
        null, // elementId — not exposed in MCP
        null, // hasElementInstanceIncident — not exposed in MCP
        null, // incidentErrorHashCode — not exposed in MCP
        filter.tags(),
        wrapString(filter.businessId()),
        wrapString(filter.processDefinitionId()),
        wrapString(filter.processDefinitionName()),
        filter.processDefinitionVersion() != null
            ? new GeneratedIntegerFilterPropertyPlainValueStrictContract(
                filter.processDefinitionVersion())
            : null,
        null, // processDefinitionVersionTag — not exposed in MCP
        filter.processDefinitionKey() != null
            ? new GeneratedProcessDefinitionKeyFilterPropertyPlainValueStrictContract(
                filter.processDefinitionKey())
            : null,
        null // $or — not exposed in MCP
        );
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

  // -- Process instance creation: validation + facade → strict contract conversion --

  private static org.springframework.http.ProblemDetail validateCreationInstruction(
      final McpProcessInstanceCreation instruction) {
    final var byIdSet =
        instruction.processDefinitionId() != null && !instruction.processDefinitionId().isBlank();
    final var byKeySet =
        instruction.processDefinitionKey() != null && !instruction.processDefinitionKey().isBlank();

    if (!byIdSet && !byKeySet) {
      return GatewayErrorMapper.createProblemDetail(
          HttpStatus.BAD_REQUEST,
          "At least one of [processDefinitionId, processDefinitionKey] is required.",
          "INVALID_ARGUMENT");
    }
    if (byIdSet && byKeySet) {
      return GatewayErrorMapper.createProblemDetail(
          HttpStatus.BAD_REQUEST,
          "Only one of [processDefinitionId, processDefinitionKey] is allowed.",
          "INVALID_ARGUMENT");
    }
    return null;
  }

  private static GeneratedProcessInstanceCreationInstructionStrictContract
      toStrictCreationInstruction(final McpProcessInstanceCreation instruction) {
    final var defId = instruction.processDefinitionId();
    if (defId != null && !defId.isBlank()) {
      return new GeneratedProcessInstanceCreationInstructionByIdStrictContract(
          defId,
          instruction.processDefinitionVersion(),
          instruction.variables(),
          instruction.tenantId(),
          null, // operationReference — not exposed in MCP
          null, // startInstructions — not exposed in MCP
          null, // runtimeInstructions — not exposed in MCP
          instruction.awaitCompletion(),
          instruction.fetchVariables(),
          instruction.requestTimeout(),
          instruction.tags(),
          instruction.businessId());
    }
    return new GeneratedProcessInstanceCreationInstructionByKeyStrictContract(
        instruction.processDefinitionKey(),
        instruction.processDefinitionVersion(),
        instruction.variables(),
        null, // startInstructions — not exposed in MCP
        null, // runtimeInstructions — not exposed in MCP
        instruction.tenantId(),
        null, // operationReference — not exposed in MCP
        instruction.awaitCompletion(),
        instruction.requestTimeout(),
        instruction.fetchVariables(),
        instruction.tags(),
        instruction.businessId());
  }
}
