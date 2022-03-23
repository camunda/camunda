/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ShareSearchResultResponseDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import org.camunda.optimize.dto.optimize.rest.report.AuthorizedReportEvaluationResponseDto;
import org.camunda.optimize.rest.mapper.DashboardRestMapper;
import org.camunda.optimize.rest.mapper.ReportRestMapper;
import org.camunda.optimize.service.exceptions.SharingNotAllowedException;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.security.SharingService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import javax.validation.Valid;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
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

import static org.camunda.optimize.rest.util.TimeZoneUtil.extractTimezone;

@AllArgsConstructor
@Path(SharingRestService.SHARE_PATH)
@Component
public class SharingRestService {

  public static final String SHARE_PATH = "/share";
  public static final String REPORT_SUB_PATH = "/report";
  public static final String DASHBOARD_SUB_PATH = "/dashboard";
  public static final String EVALUATE_SUB_PATH = "/evaluate";

  private final SharingService sharingService;
  private final ConfigurationService configurationService;
  private final SessionService sessionService;
  private final ReportRestMapper reportRestMapper;
  private final DashboardRestMapper dashboardRestMapper;

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @Path(REPORT_SUB_PATH)
  public IdResponseDto createNewReportShare(@Context ContainerRequestContext requestContext,
                                            ReportShareRestDto createSharingDto) {
    if (configurationService.getSharingEnabled()) {
      String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
      return sharingService.createNewReportShareIfAbsent(createSharingDto, userId);
    } else {
      throw new SharingNotAllowedException("Sharing of reports is disabled per Optimize configuration");
    }
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @Path(DASHBOARD_SUB_PATH)
  public IdResponseDto createNewDashboardShare(@Context ContainerRequestContext requestContext,
                                               DashboardShareRestDto createSharingDto) {
    if (configurationService.getSharingEnabled()) {
      String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
      return sharingService.createNewDashboardShare(createSharingDto, userId);
    } else {
      throw new SharingNotAllowedException("Sharing of dashboards is disabled per Optimize configuration");
    }
  }

  @DELETE
  @Path(REPORT_SUB_PATH + "/{shareId}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteReportShare(@PathParam("shareId") String reportShareId) {
    sharingService.deleteReportShare(reportShareId);
  }

  @DELETE
  @Path(DASHBOARD_SUB_PATH + "/{shareId}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteDashboardShare(@PathParam("shareId") String dashboardShareId) {
    sharingService.deleteDashboardShare(dashboardShareId);
  }

  @GET
  @Path(REPORT_SUB_PATH + "/{reportId}")
  @Produces(MediaType.APPLICATION_JSON)
  public ReportShareRestDto findShareForReport(@PathParam("reportId") String reportId) {
    return sharingService.findShareForReport(reportId).orElse(null);
  }

  @GET
  @Path(DASHBOARD_SUB_PATH + "/{dashboardId}")
  @Produces(MediaType.APPLICATION_JSON)
  public DashboardShareRestDto findShareForDashboard(@PathParam("dashboardId") String dashboardId) {
    return sharingService.findShareForDashboard(dashboardId).orElse(null);
  }

  @POST
  @Path(REPORT_SUB_PATH + "/{shareId}" + EVALUATE_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedReportEvaluationResponseDto evaluateReport(@Context ContainerRequestContext requestContext,
                                                              @PathParam("shareId") String reportShareId,
                                                              @BeanParam @Valid final PaginationRequestDto paginationRequestDto) {
    final ZoneId timezone = extractTimezone(requestContext);
    return reportRestMapper.mapToEvaluationResultDto(
      sharingService.evaluateReportShare(
        reportShareId,
        timezone,
        PaginationDto.fromPaginationRequest(paginationRequestDto)
      )
    );
  }

  @POST
  @Path(DASHBOARD_SUB_PATH + "/{shareId}" + REPORT_SUB_PATH + "/{reportId}" + EVALUATE_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public AuthorizedReportEvaluationResponseDto evaluateReport(@Context ContainerRequestContext requestContext,
                                                              @PathParam("shareId") String dashboardShareId,
                                                              @PathParam("reportId") String reportId,
                                                              AdditionalProcessReportEvaluationFilterDto reportEvaluationFilter,
                                                              @BeanParam @Valid final PaginationRequestDto paginationRequestDto) {
    final ZoneId timezone = extractTimezone(requestContext);
    return reportRestMapper.mapToEvaluationResultDto(
      sharingService.evaluateReportForSharedDashboard(
        dashboardShareId,
        reportId,
        timezone,
        reportEvaluationFilter,
        PaginationDto.fromPaginationRequest(paginationRequestDto)
      )
    );
  }

  @GET
  @Path(DASHBOARD_SUB_PATH + "/{shareId}" + EVALUATE_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public DashboardDefinitionRestDto evaluateDashboard(@PathParam("shareId") String dashboardShareId) {
    DashboardDefinitionRestDto dashboardDefinitionDto = sharingService.evaluateDashboard(dashboardShareId).orElse(null);
    dashboardRestMapper.prepareRestResponse(dashboardDefinitionDto);
    return dashboardDefinitionDto;
  }

  /**
   * Returns status code
   * - 200: if user that performs the request is allowed to share the dashboard
   * - 403: if the user does not have the authorization to share the dashboard
   * - 404: if the dashboard for the id does not exist
   * - 500: if there were problems checking the authorizations.
   */
  @GET
  @Path(DASHBOARD_SUB_PATH + "/{dashboardId}/isAuthorizedToShare")
  @Produces(MediaType.APPLICATION_JSON)
  public Response isAuthorizedToShareDashboard(@Context ContainerRequestContext requestContext,
                                               @PathParam("dashboardId") String dashboardId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    sharingService.validateAndCheckAuthorization(dashboardId, userId);
    // if no error was thrown
    return Response.status(Response.Status.OK).entity("OK").build();
  }

  @POST
  @Path("/status")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public ShareSearchResultResponseDto checkShareStatus(ShareSearchRequestDto searchRequest) {
    return sharingService.checkShareStatus(searchRequest);
  }

}
