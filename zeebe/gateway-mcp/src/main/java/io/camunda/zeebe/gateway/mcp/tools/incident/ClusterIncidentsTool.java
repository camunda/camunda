/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.mcp.tools.incident;

import static io.camunda.zeebe.gateway.mcp.tools.incident.IncidentMapper.buildIncidentQuery;

import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.IncidentServices;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ClusterIncidentsTool {

  private static final Logger LOG = LoggerFactory.getLogger(ClusterIncidentsTool.class);

  private final IncidentServices incidentServices;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ClusterIncidentsTool(
      final IncidentServices incidentServices,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.incidentServices = incidentServices;
    this.authenticationProvider = authenticationProvider;
  }

  public IncidentSearchResponse searchIncidents(IncidentSearchRequest request) {
    try {
      LOG.debug("Searching for incidents with request: {}", request);

      // Build the incident query
      final IncidentQuery query = buildIncidentQuery(request);

      // Execute the search with default authentication
      final SearchQueryResult<IncidentEntity> result =
          incidentServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .search(query);

      // Convert results to DTOs
      final List<Incident> incidents =
          result.items().stream().map(IncidentMapper::toIncident).collect(Collectors.toList());

      LOG.debug("Found {} incidents", incidents.size());
      return new IncidentSearchResponse(incidents, result.total(), null);

    } catch (final Exception e) {
      LOG.error("Error searching for incidents", e);
      return new IncidentSearchResponse(null, null, "Error searching incidents: " + e.getMessage());
    }
  }
}
