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
import org.camunda.optimize.dto.optimize.query.analysis.VariableTermDto;
import org.camunda.optimize.dto.optimize.rest.analysis.FlowNodeOutlierParametersRestDto;
import org.camunda.optimize.dto.optimize.rest.analysis.FlowNodeOutlierVariableParametersRestDto;
import org.camunda.optimize.dto.optimize.rest.analysis.ProcessDefinitionParametersRestDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.OutlierAnalysisService;
import org.camunda.optimize.service.es.reader.BranchAnalysisReader;
import org.camunda.optimize.service.export.CSVUtils;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Secured
@Component
@Path("/analysis")
public class AnalysisRestService {

  private BranchAnalysisReader branchAnalysisReader;
  private OutlierAnalysisService outlierAnalysisService;
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
                                                        @BeanParam ProcessDefinitionParametersRestDto parameters) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return outlierAnalysisService.getFlowNodeOutlierMap(parameters, userId);
  }

  @GET
  @Path("/durationChart")
  @Produces(MediaType.APPLICATION_JSON)
  public List<DurationChartEntryDto> getCountByDurationChart(@Context ContainerRequestContext requestContext,
                                                             @BeanParam FlowNodeOutlierParametersRestDto parameters) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return outlierAnalysisService.getCountByDurationChart(parameters, userId);
  }

  @GET
  @Path("/significantOutlierVariableTerms")
  @Produces(MediaType.APPLICATION_JSON)
  public List<VariableTermDto> getSignificantOutlierVariableTerms(@Context ContainerRequestContext requestContext,
                                                                  @BeanParam FlowNodeOutlierParametersRestDto parameters) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return outlierAnalysisService.getSignificantOutlierVariableTerms(parameters, userId);
  }

  @GET
  @Path("/significantOutlierVariableTerms/processInstanceIdsExport")
  // octet stream on success, json on potential error
  @Produces(value = {MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON})
  public Response getSignificantOutlierVariableTermsInstanceIds(@Context ContainerRequestContext requestContext,
                                                                @PathParam("fileName") String fileName,
                                                                @BeanParam FlowNodeOutlierVariableParametersRestDto parameters) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final String resultFileName = fileName == null ? System.currentTimeMillis() + ".csv" : fileName;
    final List<String[]> processInstanceIdsCsv = CSVUtils.mapIdList(
      outlierAnalysisService.getSignificantOutlierVariableTermsInstanceIds(parameters, userId)
    );

    return Response
        .ok(CSVUtils.mapCsvLinesToCsvBytes(processInstanceIdsCsv), MediaType.APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment; filename=" + resultFileName)
        .build();
  }

}
