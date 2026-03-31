/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.process.definition;

import static io.camunda.gateway.mcp.tool.ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PROCESS_DEFINITION_KEY_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PROCESS_DEFINITION_KEY_NOT_NULL_MESSAGE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PROCESS_DEFINITION_KEY_POSITIVE_MESSAGE;

import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionFilterStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionSearchQueryRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedProcessDefinitionSearchQuerySortRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedSearchQueryPageRequestStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedSortOrderEnum;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedStringFilterPropertyPlainValueStrictContract;
import io.camunda.gateway.mapping.http.search.contract.generated.GeneratedStringFilterPropertyStrictContract;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.config.tool.McpToolParamsUnwrapped;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.gateway.mcp.model.McpProcessDefinitionFilter;
import io.camunda.gateway.mcp.model.McpProcessDefinitionSearchQuery;
import io.camunda.gateway.protocol.model.ProcessDefinitionSearchQuerySortRequest;
import io.camunda.gateway.protocol.model.simple.SearchQueryPageRequest;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
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
public class ProcessDefinitionTools {

  private final ProcessDefinitionServices processDefinitionServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ProcessDefinitionTools(
      final ProcessDefinitionServices processDefinitionServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.processDefinitionServices = processDefinitionServices;
    this.authenticationProvider = authenticationProvider;
  }

  @CamundaMcpTool(
      description = "Search for process definitions. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult searchProcessDefinitions(
      @McpToolParamsUnwrapped @Valid final McpProcessDefinitionSearchQuery query) {
    try {
      final var strictRequest = toStrictContract(query);
      final var processDefinitionQuery =
          SearchQueryRequestMapper.toProcessDefinitionQueryStrict(strictRequest);
      if (processDefinitionQuery.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(processDefinitionQuery.getLeft());
      }

      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toProcessDefinitionSearchQueryResponse(
              processDefinitionServices.search(
                  processDefinitionQuery.get(),
                  authenticationProvider.getCamundaAuthentication())));
    } catch (final IllegalArgumentException e) {
      return CallToolResultMapper.mapProblemToResult(
          GatewayErrorMapper.createProblemDetail(
              HttpStatus.BAD_REQUEST, e.getMessage(), "INVALID_ARGUMENT"));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @CamundaMcpTool(
      description = "Get process definition by key. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult getProcessDefinition(
      @McpToolParam(description = PROCESS_DEFINITION_KEY_DESCRIPTION)
          @NotNull(message = PROCESS_DEFINITION_KEY_NOT_NULL_MESSAGE)
          @Positive(message = PROCESS_DEFINITION_KEY_POSITIVE_MESSAGE)
          final Long processDefinitionKey) {
    try {
      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toProcessDefinition(
              processDefinitionServices.getByKey(
                  processDefinitionKey, authenticationProvider.getCamundaAuthentication())));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @CamundaMcpTool(
      description = "Get the BPMN XML of a process definition by key. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult getProcessDefinitionXml(
      @McpToolParam(description = PROCESS_DEFINITION_KEY_DESCRIPTION)
          @NotNull(message = PROCESS_DEFINITION_KEY_NOT_NULL_MESSAGE)
          @Positive(message = PROCESS_DEFINITION_KEY_POSITIVE_MESSAGE)
          final Long processDefinitionKey) {
    try {
      final var xml =
          processDefinitionServices
              .getProcessDefinitionXml(
                  processDefinitionKey, authenticationProvider.getCamundaAuthentication())
              .orElseThrow(
                  () ->
                      new ServiceException(
                          "The BPMN XML for this process definition is not available.",
                          Status.NOT_FOUND));

      return CallToolResult.builder().addTextContent(xml).build();
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  // -- Facade → Strict contract conversion --

  private static GeneratedProcessDefinitionSearchQueryRequestStrictContract toStrictContract(
      final McpProcessDefinitionSearchQuery query) {
    return new GeneratedProcessDefinitionSearchQueryRequestStrictContract(
        toStrictPage(query.page()), toStrictSort(query.sort()), toStrictFilter(query.filter()));
  }

  private static GeneratedProcessDefinitionFilterStrictContract toStrictFilter(
      final McpProcessDefinitionFilter filter) {
    if (filter == null) {
      return null;
    }
    return new GeneratedProcessDefinitionFilterStrictContract(
        wrapString(filter.name()),
        filter.isLatestVersion(),
        filter.resourceName(),
        filter.version(),
        filter.versionTag(),
        wrapString(filter.processDefinitionId()),
        null, // tenantId — not exposed in MCP
        filter.processDefinitionKey(),
        filter.hasStartForm());
  }

  private static GeneratedSearchQueryPageRequestStrictContract toStrictPage(
      final SearchQueryPageRequest page) {
    if (page == null) {
      return null;
    }
    return new GeneratedSearchQueryPageRequestStrictContract(
        page.getLimit(), page.getFrom(), page.getAfter(), page.getBefore());
  }

  private static List<GeneratedProcessDefinitionSearchQuerySortRequestStrictContract> toStrictSort(
      final List<ProcessDefinitionSearchQuerySortRequest> sort) {
    if (sort == null || sort.isEmpty()) {
      return null;
    }
    return sort.stream()
        .map(
            s ->
                new GeneratedProcessDefinitionSearchQuerySortRequestStrictContract(
                    GeneratedProcessDefinitionSearchQuerySortRequestStrictContract.FieldEnum
                        .fromValue(s.getField().getValue()),
                    s.getOrder() != null
                        ? GeneratedSortOrderEnum.fromValue(s.getOrder().getValue())
                        : null))
        .toList();
  }

  private static GeneratedStringFilterPropertyStrictContract wrapString(final String value) {
    return value != null ? new GeneratedStringFilterPropertyPlainValueStrictContract(value) : null;
  }
}
