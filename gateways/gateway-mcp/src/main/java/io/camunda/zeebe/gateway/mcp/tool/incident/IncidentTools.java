/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.tool.incident;

import static io.camunda.zeebe.gateway.mcp.mapper.CallToolResultMapper.mapErrorToResult;
import static io.camunda.zeebe.gateway.mcp.tool.ToolDescriptions.EVENTUAL_CONSISTENCY_NOTE;

import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.IncidentServices;
import io.camunda.service.JobServices;
import io.camunda.service.JobServices.UpdateJobChangeset;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.zeebe.gateway.mcp.mapper.McpErrorMapper;
import io.camunda.zeebe.gateway.mcp.mapper.search.SearchQueryFilterMapper;
import io.camunda.zeebe.gateway.mcp.mapper.search.SearchQueryPageMapper;
import io.camunda.zeebe.gateway.mcp.mapper.search.SearchQuerySortRequestMapper;
import io.camunda.zeebe.gateway.mcp.model.IncidentSearchFilter;
import io.camunda.zeebe.gateway.mcp.model.IncidentSearchQuerySortRequest;
import io.camunda.zeebe.gateway.mcp.model.SearchQueryPageRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpTool.McpAnnotations;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class IncidentTools {

  private static final Logger LOGGER = LoggerFactory.getLogger(IncidentTools.class);

  private final IncidentServices incidentServices;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final JobServices jobServices;

  public IncidentTools(
      final IncidentServices incidentServices,
      final CamundaAuthenticationProvider authenticationProvider,
      final JobServices jobServices) {
    this.incidentServices = incidentServices;
    this.authenticationProvider = authenticationProvider;
    this.jobServices = jobServices;
  }

  @McpTool(
      description = "Search for incidents. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult searchIncidents(
      @McpToolParam(description = "Filter search by the given fields", required = false) @Valid
          final IncidentSearchFilter filter,
      @McpToolParam(description = "Sort criteria", required = false)
          final List<@Valid IncidentSearchQuerySortRequest> sort,
      @McpToolParam(description = "Pagination criteria", required = false) @Valid
          final SearchQueryPageRequest page) {
    try {
      final var sortRequest = SearchQuerySortRequestMapper.toIncidentSearchSort(sort);
      if (sortRequest.isLeft()) {
        return sortRequest.mapLeft(CallToolResultMapper::mapViolationsToResult).getLeft();
      }

      final var result =
          incidentServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(
                  SearchQueryBuilders.incidentSearchQuery()
                      .filter(SearchQueryFilterMapper.toIncidentFilter(filter))
                      .page(SearchQueryPageMapper.toSearchQueryPage(page))
                      .sort(sortRequest.get())
                      .build());

      return CallToolResultMapper.from(result);
    } catch (final Exception e) {
      return mapErrorToResult(e);
    }
  }

  @McpTool(
      description = "Get incident by key. " + EVENTUAL_CONSISTENCY_NOTE,
      annotations = @McpAnnotations(readOnlyHint = true))
  public CallToolResult getIncident(
      @McpToolParam(
              description =
                  "The assigned key of the incident, which acts as a unique identifier for this incident.")
          @Positive
          final Long incidentKey) {
    try {
      return CallToolResultMapper.from(
          incidentServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .getByKey(incidentKey));
    } catch (final Exception e) {
      return mapErrorToResult(e);
    }
  }

  @McpTool(description = "Resolve incident by key. " + EVENTUAL_CONSISTENCY_NOTE)
  public CallToolResult resolveIncident(
      @McpToolParam(description = "Key of the incident to resolve.") @Positive
          final Long incidentKey) {
    try {
      return CallToolResultMapper.fromPrimitive(
          incidentServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .resolveIncident(incidentKey, null),
          error -> {
            if (McpErrorMapper.unwrapError(error) instanceof final ServiceException se
                && Status.INVALID_STATE.equals(se.getStatus())
                && se.getMessage().contains("no retries left")) {
              // no retries left for a job incident
              return resolveJobIncident(incidentKey);
            }
            return mapErrorToResult(error);
          },
          r -> "RESOLVED");
    } catch (final Exception e) {
      return mapErrorToResult(e);
    }
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
      CallToolResultMapper.from(
          jobServices
              .withAuthentication(camundaAuthentication)
              .updateJob(jobKey, null, new UpdateJobChangeset(1, null)),
          r -> r);
      // resolve incident again
      return CallToolResultMapper.fromPrimitive(
          incidentServices.resolveIncident(incidentKey, null), r -> "RESOLVED");
    } catch (final Exception e) {
      return mapErrorToResult(e);
    }
  }
}
