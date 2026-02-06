/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.incident;

import static io.camunda.gateway.mcp.mapper.CallToolResultMapper.mapErrorToResult;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.FILTER_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.INCIDENT_KEY_POSITIVE_MESSAGE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.PAGE_DESCRIPTION;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.SORT_DESCRIPTION;

import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.gateway.mcp.model.McpIncidentFilter;
import io.camunda.gateway.mcp.model.McpSearchQueryPageRequest;
import io.camunda.gateway.protocol.model.IncidentSearchQuerySortRequest;
import io.camunda.gateway.protocol.model.JobActivationResult;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.IncidentServices;
import io.camunda.service.JobServices;
import io.camunda.service.JobServices.UpdateJobChangeset;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.util.Either;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class IncidentTools {

  private final IncidentServices incidentServices;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final JobServices<JobActivationResult> jobServices;

  public IncidentTools(
      final IncidentServices incidentServices,
      final CamundaAuthenticationProvider authenticationProvider,
      final JobServices<JobActivationResult> jobServices) {
    this.incidentServices = incidentServices;
    this.authenticationProvider = authenticationProvider;
    this.jobServices = jobServices;
  }

  @CamundaMcpTool(
      description = "Search for incidents. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult searchIncidents(
      @McpToolParam(description = FILTER_DESCRIPTION, required = false)
          final McpIncidentFilter filter,
      @McpToolParam(description = SORT_DESCRIPTION, required = false)
          final List<IncidentSearchQuerySortRequest> sort,
      @McpToolParam(description = PAGE_DESCRIPTION, required = false)
          final McpSearchQueryPageRequest page) {
    try {
      final var incidentSearchQuery = SearchQueryRequestMapper.toIncidentQuery(filter, page, sort);

      if (incidentSearchQuery.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(incidentSearchQuery.getLeft());
      }

      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toIncidentSearchQueryResponse(
              incidentServices
                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
                  .search(incidentSearchQuery.get())));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @CamundaMcpTool(
      description = "Get incident by key. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult getIncident(
      @McpToolParam(
              description =
                  "The assigned key of the incident, which acts as a unique identifier for this incident.")
          @Positive(message = INCIDENT_KEY_POSITIVE_MESSAGE)
          final Long incidentKey) {
    try {
      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toIncident(
              incidentServices
                  .withAuthentication(authenticationProvider.getCamundaAuthentication())
                  .getByKey(incidentKey)));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @CamundaMcpTool(description = "Resolve incident by key. " + EVENTUAL_CONSISTENCY_NOTE)
  public CallToolResult resolveIncident(
      @McpToolParam(description = "Key of the incident to resolve.")
          @Positive(message = INCIDENT_KEY_POSITIVE_MESSAGE)
          final Long incidentKey) {
    try {
      return CallToolResultMapper.fromPrimitive(
          incidentServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .resolveIncident(incidentKey, null),
          r -> "Incident with key %s resolved.".formatted(incidentKey),
          error ->
              isNoJobRetriesLeft(error)
                  ? resolveJobIncident(incidentKey)
                  : mapErrorToResult(error));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  private static boolean isNoJobRetriesLeft(final Throwable error) {
    return GatewayErrorMapper.unwrapError(error) instanceof final ServiceException se
        && Status.INVALID_STATE.equals(se.getStatus())
        && se.getMessage().contains("no retries left");
  }

  private CallToolResult resolveJobIncident(final Long incidentKey) {
    try {
      final CamundaAuthentication camundaAuthentication =
          authenticationProvider.getCamundaAuthentication();
      // fetch the incident to retrieve the job key
      final IncidentEntity incident =
          incidentServices.withAuthentication(camundaAuthentication).getByKey(incidentKey);
      // incident cannot be null, service throws exception if incident not found
      final Long jobKey = incident.jobKey();
      if (jobKey == null) {
        throw new ServiceException(
            "Cannot retrieve job key for job-based incident.", Status.INVALID_STATE);
      }
      // update retries for the job to 1
      final Either<Throwable, JobRecord> updateResult =
          CallToolResultMapper.executeServiceMethod(
              jobServices
                  .withAuthentication(camundaAuthentication)
                  .updateJob(jobKey, null, new UpdateJobChangeset(1, null)));
      if (updateResult.isLeft()) {
        return CallToolResultMapper.mapErrorToResult(updateResult.getLeft());
      }
      // resolve incident again
      return CallToolResultMapper.fromPrimitive(
          incidentServices.resolveIncident(incidentKey, null),
          r -> "Incident with key %s resolved.".formatted(incidentKey));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }
}
