/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisQueryDto;
import org.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import org.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.es.reader.BranchAnalysisReader;
import org.camunda.optimize.service.es.reader.DurationOutliersReader;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@Secured
@Component
@Path("/analysis")
public class AnalysisRestService {

  private BranchAnalysisReader branchAnalysisReader;
  private DurationOutliersReader durationOutliersReader;
  private SessionService sessionService;

  /**
   * Get the branch analysis from the given query information.
   *
   * @return All information concerning the branch analysis.
   */
  @POST
  @Path("/correlation")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public BranchAnalysisDto getBranchAnalysis(@Context ContainerRequestContext requestContext,
                                             BranchAnalysisQueryDto branchAnalysisDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return branchAnalysisReader.branchAnalysis(userId, branchAnalysisDto);
  }


  @GET
  @Path("/flowNodeOutliers")
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, FindingsDto> getFlowNodeOutlierMap(@Context ContainerRequestContext requestContext,
                                                        @QueryParam("processDefinitionKey") String processDefinitionKey,
                                                        @QueryParam("processDefinitionVersions") List<String> processDefinitionVersions,
                                                        @QueryParam("tenantIds") List<String> tenantIds
  ) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    List<String> normalizedTenants = normalizeTenants(tenantIds);
    return durationOutliersReader.getFlowNodeOutlierMap(
      processDefinitionKey,
      processDefinitionVersions,
      userId,
      normalizedTenants
    );
  }

  @GET
  @Path("/durationChart")
  @Produces(MediaType.APPLICATION_JSON)
  public List<DurationChartEntryDto> getCountByDurationChart(@Context ContainerRequestContext requestContext,
                                                             @QueryParam("processDefinitionKey") String processDefinitionKey,
                                                             @QueryParam("processDefinitionVersions") List<String> processDefinitionVersions,
                                                             @QueryParam("tenantIds") List<String> tenantIds,
                                                             @QueryParam("flowNodeId") String flowNodeId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    List<String> normalizedTenants = normalizeTenants(tenantIds);
    return durationOutliersReader.getCountByDurationChart(
      processDefinitionKey,
      processDefinitionVersions,
      flowNodeId,
      userId,
      normalizedTenants
    );
  }

  private List<String> normalizeTenants(List<String> tenantIds) {
    return tenantIds.stream().map(t -> t.equals("null") ? null : t).collect(Collectors.toList());
  }
}
