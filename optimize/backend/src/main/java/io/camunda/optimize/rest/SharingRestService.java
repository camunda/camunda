/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_LOCALE;
import static io.camunda.optimize.rest.util.TimeZoneUtil.extractTimezone;

import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.report.AdditionalProcessReportEvaluationFilterDto;
import io.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import io.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import io.camunda.optimize.dto.optimize.query.sharing.ShareSearchRequestDto;
import io.camunda.optimize.dto.optimize.query.sharing.ShareSearchResultResponseDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationDto;
import io.camunda.optimize.dto.optimize.rest.pagination.PaginationRequestDto;
import io.camunda.optimize.dto.optimize.rest.report.AuthorizedReportEvaluationResponseDto;
import io.camunda.optimize.rest.mapper.DashboardRestMapper;
import io.camunda.optimize.rest.mapper.ReportRestMapper;
import io.camunda.optimize.service.SettingsService;
import io.camunda.optimize.service.exceptions.SharingNotAllowedException;
import io.camunda.optimize.service.mixpanel.EventReportingService;
import io.camunda.optimize.service.mixpanel.client.EventReportingEvent;
import io.camunda.optimize.service.security.SessionService;
import io.camunda.optimize.service.security.SharingService;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.OptimizeProfile;
import jakarta.validation.Valid;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.ZoneId;
import java.util.function.Supplier;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Path(SharingRestService.SHARE_PATH)
@Component
public class SharingRestService {

  public static final String SHARE_PATH = "/share";
  public static final String REPORT_SUB_PATH = "/report";
  public static final String DASHBOARD_SUB_PATH = "/dashboard";
  public static final String EVALUATE_SUB_PATH = "/evaluate";

  private final SharingService sharingService;
  private final SettingsService settingsService;
  private final SessionService sessionService;
  private final ReportRestMapper reportRestMapper;
  private final DashboardRestMapper dashboardRestMapper;
  private final EventReportingService eventReportingService;
  private final Environment environment;

  public SharingRestService(
      final SharingService sharingService,
      final SettingsService settingsService,
      final SessionService sessionService,
      final ReportRestMapper reportRestMapper,
      final DashboardRestMapper dashboardRestMapper,
      final EventReportingService eventReportingService,
      final Environment environment) {
    this.sharingService = sharingService;
    this.settingsService = settingsService;
    this.sessionService = sessionService;
    this.reportRestMapper = reportRestMapper;
    this.dashboardRestMapper = dashboardRestMapper;
    this.eventReportingService = eventReportingService;
    this.environment = environment;
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @Path(REPORT_SUB_PATH)
  public IdResponseDto createNewReportShare(
      @Context final ContainerRequestContext requestContext,
      final ReportShareRestDto createSharingDto) {
    return executeIfSharingEnabled(
        () ->
            sharingService.createNewReportShareIfAbsent(
                createSharingDto, sessionService.getRequestUserOrFailNotAuthorized(requestContext)),
        EventReportingEvent.REPORT_SHARE_ENABLED,
        "Sharing of reports is disabled per Optimize configuration");
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @Path(DASHBOARD_SUB_PATH)
  public IdResponseDto createNewDashboardShare(
      @Context final ContainerRequestContext requestContext,
      final DashboardShareRestDto createSharingDto) {
    return executeIfSharingEnabled(
        () ->
            sharingService.createNewDashboardShare(
                createSharingDto, sessionService.getRequestUserOrFailNotAuthorized(requestContext)),
        EventReportingEvent.DASHBOARD_SHARE_ENABLED,
        "Sharing of dashboards is disabled per Optimize configuration");
  }

  @DELETE
  @Path(REPORT_SUB_PATH + "/{shareId}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteReportShare(@PathParam("shareId") final String reportShareId) {
    sharingService.deleteReportShare(reportShareId);
    eventReportingService.sendEntityEvent(EventReportingEvent.REPORT_SHARE_DISABLED, reportShareId);
  }

  @DELETE
  @Path(DASHBOARD_SUB_PATH + "/{shareId}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteDashboardShare(@PathParam("shareId") final String dashboardShareId) {
    sharingService.deleteDashboardShare(dashboardShareId);
    eventReportingService.sendEntityEvent(
        EventReportingEvent.DASHBOARD_SHARE_DISABLED, dashboardShareId);
  }

  @GET
  @Path(REPORT_SUB_PATH + "/{reportId}")
  @Produces(MediaType.APPLICATION_JSON)
  public ReportShareRestDto findShareForReport(@PathParam("reportId") final String reportId) {
    return sharingService.findShareForReport(reportId).orElse(null);
  }

  @GET
  @Path(DASHBOARD_SUB_PATH + "/{dashboardId}")
  @Produces(MediaType.APPLICATION_JSON)
  public DashboardShareRestDto findShareForDashboard(
      @PathParam("dashboardId") final String dashboardId) {
    return sharingService.findShareForDashboard(dashboardId).orElse(null);
  }

  @POST
  @Path(REPORT_SUB_PATH + "/{shareId}" + EVALUATE_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedReportEvaluationResponseDto evaluateReport(
      @Context final ContainerRequestContext requestContext,
      @PathParam("shareId") final String reportShareId,
      @BeanParam @Valid final PaginationRequestDto paginationRequestDto) {
    final ZoneId timezone = extractTimezone(requestContext);
    return reportRestMapper.mapToLocalizedEvaluationResponseDto(
        sharingService.evaluateReportShare(
            reportShareId, timezone, PaginationDto.fromPaginationRequest(paginationRequestDto)),
        requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE),
        // In multi-instance SaaS, name resolution will be reenabled in
        // https://github.com/camunda/camunda-optimize/issues/10123
        ConfigurationService.getOptimizeProfile(environment) == OptimizeProfile.CLOUD);
  }

  @POST
  @Path(DASHBOARD_SUB_PATH + "/{shareId}" + REPORT_SUB_PATH + "/{reportId}" + EVALUATE_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public AuthorizedReportEvaluationResponseDto evaluateReport(
      @Context final ContainerRequestContext requestContext,
      @PathParam("shareId") final String dashboardShareId,
      @PathParam("reportId") final String reportId,
      final AdditionalProcessReportEvaluationFilterDto reportEvaluationFilter,
      @BeanParam @Valid final PaginationRequestDto paginationRequestDto) {
    final ZoneId timezone = extractTimezone(requestContext);
    return reportRestMapper.mapToLocalizedEvaluationResponseDto(
        sharingService.evaluateReportForSharedDashboard(
            dashboardShareId,
            reportId,
            timezone,
            reportEvaluationFilter,
            PaginationDto.fromPaginationRequest(paginationRequestDto)),
        requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE),
        // In multi-instance SaaS, name resolution will be reenabled in
        // https://github.com/camunda/camunda-optimize/issues/10123
        ConfigurationService.getOptimizeProfile(environment) == OptimizeProfile.CLOUD);
  }

  @GET
  @Path(DASHBOARD_SUB_PATH + "/{shareId}" + EVALUATE_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public DashboardDefinitionRestDto evaluateDashboard(
      @Context final ContainerRequestContext requestContext,
      @PathParam("shareId") final String dashboardShareId) {
    final DashboardDefinitionRestDto dashboardDefinitionDto =
        sharingService.evaluateDashboard(dashboardShareId).orElse(null);
    dashboardRestMapper.prepareRestResponse(
        dashboardDefinitionDto,
        requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE),
        // In multi-instance SaaS, name resolution will be reenabled in
        // https://github.com/camunda/camunda-optimize/issues/10123
        ConfigurationService.getOptimizeProfile(environment) == OptimizeProfile.CLOUD);
    return dashboardDefinitionDto;
  }

  /**
   * Returns status code - 200: if user that performs the request is allowed to share the dashboard
   * - 403: if the user does not have the authorization to share the dashboard - 404: if the
   * dashboard for the id does not exist - 500: if there were problems checking the authorizations.
   */
  @GET
  @Path(DASHBOARD_SUB_PATH + "/{dashboardId}/isAuthorizedToShare")
  @Produces(MediaType.APPLICATION_JSON)
  public Response isAuthorizedToShareDashboard(
      @Context final ContainerRequestContext requestContext,
      @PathParam("dashboardId") final String dashboardId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    sharingService.validateAndCheckAuthorization(dashboardId, userId);
    // if no error was thrown
    return Response.status(Response.Status.OK).entity("OK").build();
  }

  @POST
  @Path("/status")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public ShareSearchResultResponseDto checkShareStatus(final ShareSearchRequestDto searchRequest) {
    return sharingService.checkShareStatus(searchRequest);
  }

  private IdResponseDto executeIfSharingEnabled(
      final Supplier<IdResponseDto> supplier,
      final EventReportingEvent eventName,
      final String sharingDisabledMessage) {
    return settingsService
        .getSettings()
        .getSharingEnabled()
        .filter(Boolean::booleanValue)
        .map(
            isEnabled -> {
              final IdResponseDto responseDto = supplier.get();
              eventReportingService.sendEntityEvent(eventName, responseDto.getId());
              return responseDto;
            })
        .orElseThrow(() -> new SharingNotAllowedException(sharingDisabledMessage));
  }
}
