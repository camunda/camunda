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
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.ZoneId;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(REST_API_PATH + SharingRestService.SHARE_PATH)
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

  @PostMapping(REPORT_SUB_PATH)
  public IdResponseDto createNewReportShare(
      @RequestBody final ReportShareRestDto createSharingDto, final HttpServletRequest request) {
    return executeIfSharingEnabled(
        () ->
            sharingService.createNewReportShareIfAbsent(
                createSharingDto, sessionService.getRequestUserOrFailNotAuthorized(request)),
        EventReportingEvent.REPORT_SHARE_ENABLED,
        "Sharing of reports is disabled per Optimize configuration");
  }

  @PostMapping(DASHBOARD_SUB_PATH)
  public IdResponseDto createNewDashboardShare(
      @RequestBody final DashboardShareRestDto createSharingDto, final HttpServletRequest request) {
    return executeIfSharingEnabled(
        () ->
            sharingService.createNewDashboardShare(
                createSharingDto, sessionService.getRequestUserOrFailNotAuthorized(request)),
        EventReportingEvent.DASHBOARD_SHARE_ENABLED,
        "Sharing of dashboards is disabled per Optimize configuration");
  }

  @DeleteMapping(REPORT_SUB_PATH + "/{shareId}")
  public void deleteReportShare(@PathVariable("shareId") final String reportShareId) {
    sharingService.deleteReportShare(reportShareId);
    eventReportingService.sendEntityEvent(EventReportingEvent.REPORT_SHARE_DISABLED, reportShareId);
  }

  @DeleteMapping(DASHBOARD_SUB_PATH + "/{shareId}")
  public void deleteDashboardShare(@PathVariable("shareId") final String dashboardShareId) {
    sharingService.deleteDashboardShare(dashboardShareId);
    eventReportingService.sendEntityEvent(
        EventReportingEvent.DASHBOARD_SHARE_DISABLED, dashboardShareId);
  }

  @GetMapping(REPORT_SUB_PATH + "/{reportId}")
  public ResponseEntity<ReportShareRestDto> findShareForReport(
      @PathVariable("reportId") final String reportId) {
    final Optional<ReportShareRestDto> result = sharingService.findShareForReport(reportId);
    if (result.isPresent()) {
      return ResponseEntity.ok(result.get());
    }

    return ResponseEntity.noContent().build();
  }

  @GetMapping(DASHBOARD_SUB_PATH + "/{dashboardId}")
  public ResponseEntity<DashboardShareRestDto> findShareForDashboard(
      @PathVariable("dashboardId") final String dashboardId) {
    final Optional<DashboardShareRestDto> result =
        sharingService.findShareForDashboard(dashboardId);
    if (result.isPresent()) {
      return ResponseEntity.ok(result.get());
    }

    return ResponseEntity.noContent().build();
  }

  @PostMapping(REPORT_SUB_PATH + "/{shareId}" + EVALUATE_SUB_PATH)
  public AuthorizedReportEvaluationResponseDto evaluateReport(
      @PathVariable("shareId") final String reportShareId,
      @Valid @RequestBody final PaginationRequestDto paginationRequestDto,
      final HttpServletRequest request) {
    final ZoneId timezone = extractTimezone(request);
    return reportRestMapper.mapToLocalizedEvaluationResponseDto(
        sharingService.evaluateReportShare(
            reportShareId, timezone, PaginationDto.fromPaginationRequest(paginationRequestDto)),
        request.getHeader(X_OPTIMIZE_CLIENT_LOCALE),
        // In multi-instance SaaS, name resolution will be reenabled in
        // https://github.com/camunda/camunda-optimize/issues/10123
        ConfigurationService.getOptimizeProfile(environment) == OptimizeProfile.CLOUD);
  }

  @PostMapping(
      DASHBOARD_SUB_PATH + "/{shareId}" + REPORT_SUB_PATH + "/{reportId}" + EVALUATE_SUB_PATH)
  public AuthorizedReportEvaluationResponseDto evaluateReport(
      @PathVariable("shareId") final String dashboardShareId,
      @PathVariable("reportId") final String reportId,
      @RequestBody final AdditionalProcessReportEvaluationFilterDto reportEvaluationFilter,
      @Valid final PaginationRequestDto paginationRequestDto,
      final HttpServletRequest request) {
    final ZoneId timezone = extractTimezone(request);
    return reportRestMapper.mapToLocalizedEvaluationResponseDto(
        sharingService.evaluateReportForSharedDashboard(
            dashboardShareId,
            reportId,
            timezone,
            reportEvaluationFilter,
            PaginationDto.fromPaginationRequest(paginationRequestDto)),
        request.getHeader(X_OPTIMIZE_CLIENT_LOCALE),
        // In multi-instance SaaS, name resolution will be reenabled in
        // https://github.com/camunda/camunda-optimize/issues/10123
        ConfigurationService.getOptimizeProfile(environment) == OptimizeProfile.CLOUD);
  }

  @GetMapping(DASHBOARD_SUB_PATH + "/{shareId}" + EVALUATE_SUB_PATH)
  public DashboardDefinitionRestDto evaluateDashboard(
      @PathVariable("shareId") final String dashboardShareId, final HttpServletRequest request) {
    final DashboardDefinitionRestDto dashboardDefinitionDto =
        sharingService.evaluateDashboard(dashboardShareId).orElse(null);
    dashboardRestMapper.prepareRestResponse(
        dashboardDefinitionDto,
        request.getHeader(X_OPTIMIZE_CLIENT_LOCALE),
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
  @GetMapping(DASHBOARD_SUB_PATH + "/{dashboardId}/isAuthorizedToShare")
  public String isAuthorizedToShareDashboard(
      @PathVariable("dashboardId") final String dashboardId, final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    sharingService.validateAndCheckAuthorization(dashboardId, userId);
    // if no error was thrown
    return "OK";
  }

  @PostMapping("/status")
  public ShareSearchResultResponseDto checkShareStatus(
      @RequestBody final ShareSearchRequestDto searchRequest) {
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
