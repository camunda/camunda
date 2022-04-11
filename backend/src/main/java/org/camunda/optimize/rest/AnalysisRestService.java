/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisRequestDto;
import org.camunda.optimize.dto.optimize.query.analysis.BranchAnalysisResponseDto;
import org.camunda.optimize.dto.optimize.query.analysis.DurationChartEntryDto;
import org.camunda.optimize.dto.optimize.query.analysis.FindingsDto;
import org.camunda.optimize.dto.optimize.query.analysis.VariableTermDto;
import org.camunda.optimize.dto.optimize.rest.analysis.FlowNodeOutlierParametersRequestDto;
import org.camunda.optimize.dto.optimize.rest.analysis.FlowNodeOutlierVariableParametersRequestDto;
import org.camunda.optimize.dto.optimize.rest.analysis.ProcessDefinitionParametersRequestDto;
import org.camunda.optimize.service.BranchAnalysisService;
import org.camunda.optimize.service.OutlierAnalysisService;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.camunda.optimize.rest.util.TimeZoneUtil.extractTimezone;

@RequiredArgsConstructor
@Component
@Path("/analysis")
public class AnalysisRestService {

  private final BranchAnalysisService branchAnalysisService;
  private final OutlierAnalysisService outlierAnalysisService;
  private final SessionService sessionService;

  /**
   * Get the branch analysis from the given query information.
   *
   * @return All information concerning the branch analysis.
   */
  @POST
  @Path("/correlation")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public BranchAnalysisResponseDto getBranchAnalysis(@Context ContainerRequestContext requestContext,
                                                     BranchAnalysisRequestDto branchAnalysisDto) {

    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final ZoneId timezone = extractTimezone(requestContext);
    return branchAnalysisService.branchAnalysis(userId, branchAnalysisDto, timezone);
  }

  @GET
  @Path("/flowNodeOutliers")
  @Produces(MediaType.APPLICATION_JSON)
  public Map<String, FindingsDto> getFlowNodeOutlierMap(@Context ContainerRequestContext requestContext,
                                                        @BeanParam ProcessDefinitionParametersRequestDto parameters) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return outlierAnalysisService.getFlowNodeOutlierMap(parameters, userId);
  }

  @GET
  @Path("/durationChart")
  @Produces(MediaType.APPLICATION_JSON)
  public List<DurationChartEntryDto> getCountByDurationChart(@Context ContainerRequestContext requestContext,
                                                             @BeanParam FlowNodeOutlierParametersRequestDto parameters) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return outlierAnalysisService.getCountByDurationChart(parameters, userId);
  }

  @GET
  @Path("/significantOutlierVariableTerms")
  @Produces(MediaType.APPLICATION_JSON)
  public List<VariableTermDto> getSignificantOutlierVariableTerms(@Context ContainerRequestContext requestContext,
                                                                  @BeanParam FlowNodeOutlierParametersRequestDto parameters) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return outlierAnalysisService.getSignificantOutlierVariableTerms(parameters, userId);
  }

  @GET
  @Path("/significantOutlierVariableTerms/processInstanceIdsExport")
  // octet stream on success, json on potential error
  @Produces(value = {MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON})
  public Response getSignificantOutlierVariableTermsInstanceIds(@Context ContainerRequestContext requestContext,
                                                                @PathParam("fileName") String fileName,
                                                                @BeanParam FlowNodeOutlierVariableParametersRequestDto parameters) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final String resultFileName = fileName == null ? System.currentTimeMillis() + ".csv" : fileName;
    final List<String[]> processInstanceIdsCsv = CSVUtils.mapIdList(
      outlierAnalysisService.getSignificantOutlierVariableTermsInstanceIds(parameters, userId)
    );

    return Response
        .ok(CSVUtils.mapCsvLinesToCsvBytes(processInstanceIdsCsv, ','), MediaType.APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment; filename=" + resultFileName)
        .build();
  }

}
