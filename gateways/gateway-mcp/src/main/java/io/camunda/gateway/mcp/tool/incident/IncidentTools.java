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
import static io.camunda.gateway.mcp.tool.ToolDescriptions.INCIDENT_KEY_NOT_NULL_MESSAGE;
import static io.camunda.gateway.mcp.tool.ToolDescriptions.INCIDENT_KEY_POSITIVE_MESSAGE;

import io.camunda.gateway.mapping.http.GatewayErrorMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.mcp.config.tool.CamundaMcpTool;
import io.camunda.gateway.mcp.config.tool.McpToolParamsUnwrapped;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.gateway.protocol.model.JobActivationResult;
import io.camunda.gateway.protocol.model.simple.IncidentSearchQuery;
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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
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
      annotations = @McpAnnotations(readOnlyHint = true),
      processesServer = true)
  public CallToolResult searchIncidents(
      @McpToolParamsUnwrapped @Valid final IncidentSearchQuery query) {
    try {
      final var incidentSearchQuery = SearchQueryRequestMapper.toIncidentQuery(query);

      if (incidentSearchQuery.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(incidentSearchQuery.getLeft());
      }

      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toIncidentSearchQueryResponse(
              incidentServices.search(
                  incidentSearchQuery.get(), authenticationProvider.getCamundaAuthentication())));
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
          @NotNull(message = INCIDENT_KEY_NOT_NULL_MESSAGE)
          @Positive(message = INCIDENT_KEY_POSITIVE_MESSAGE)
          final Long incidentKey) {
    try {
      return CallToolResultMapper.from(
          SearchQueryResponseMapper.toIncident(
              incidentServices.getByKey(
                  incidentKey, authenticationProvider.getCamundaAuthentication())));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }

  @CamundaMcpTool(description = "Resolve incident by key. " + EVENTUAL_CONSISTENCY_NOTE)
  public CallToolResult resolveIncident(
      @McpToolParam(description = "Key of the incident to resolve.")
          @NotNull(message = INCIDENT_KEY_NOT_NULL_MESSAGE)
          @Positive(message = INCIDENT_KEY_POSITIVE_MESSAGE)
          final Long incidentKey) {
    try {
      final var authentication = authenticationProvider.getCamundaAuthentication();
      return CallToolResultMapper.fromPrimitive(
          incidentServices.resolveIncident(incidentKey, null, authentication),
          r -> "Incident with key %s resolved.".formatted(incidentKey),
          error ->
              isNoJobRetriesLeft(error)
                  ? resolveJobIncident(incidentKey, authentication)
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

  private CallToolResult resolveJobIncident(
      final Long incidentKey, final CamundaAuthentication authentication) {
    try {
      // fetch the incident to retrieve the job key
      final var incident = incidentServices.getByKey(incidentKey, authentication);
      // incident cannot be null, service throws exception if incident not found
      final var jobKey = incident.jobKey();
      if (jobKey == null) {
        throw new ServiceException(
            "Cannot retrieve job key for job-based incident.", Status.INVALID_STATE);
      }
      // update retries for the job to 1
      final Either<Throwable, JobRecord> updateResult =
          CallToolResultMapper.executeServiceMethod(
              jobServices.updateJob(jobKey, null, new UpdateJobChangeset(1, null), authentication));
      if (updateResult.isLeft()) {
        return CallToolResultMapper.mapErrorToResult(updateResult.getLeft());
      }
      // resolve incident again
      return CallToolResultMapper.fromPrimitive(
          incidentServices.resolveIncident(incidentKey, null, authentication),
          r -> "Incident with key %s resolved.".formatted(incidentKey));
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }
}
