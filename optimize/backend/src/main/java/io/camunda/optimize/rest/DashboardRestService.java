/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_LOCALE;
import static io.camunda.optimize.rest.queryparam.QueryParamUtil.normalizeNullStringValue;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.query.IdResponseDto;
import io.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import io.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import io.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionResponseDto;
import io.camunda.optimize.rest.mapper.DashboardRestMapper;
import io.camunda.optimize.service.dashboard.DashboardService;
import io.camunda.optimize.service.dashboard.InstantPreviewDashboardService;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(REST_API_PATH + DashboardRestService.DASHBOARD_PATH)
public class DashboardRestService {

  public static final String DASHBOARD_PATH = "/dashboard";
  public static final String INSTANT_PREVIEW_PATH = "/instant";
  private final DashboardService dashboardService;
  private final InstantPreviewDashboardService instantPreviewDashboardService;
  private final SessionService sessionService;
  private final DashboardRestMapper dashboardRestMapper;

  public DashboardRestService(
      final DashboardService dashboardService,
      final InstantPreviewDashboardService instantPreviewDashboardService,
      final SessionService sessionService,
      final DashboardRestMapper dashboardRestMapper) {
    this.dashboardService = dashboardService;
    this.instantPreviewDashboardService = instantPreviewDashboardService;
    this.sessionService = sessionService;
    this.dashboardRestMapper = dashboardRestMapper;
  }

  @PostMapping()
  public IdResponseDto createNewDashboard(
      @Valid @RequestBody final DashboardDefinitionRestDto dashboardDefinitionDto,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    if (dashboardDefinitionDto != null) {
      if (dashboardDefinitionDto.isManagementDashboard()
          || dashboardDefinitionDto.isInstantPreviewDashboard()) {
        throw new OptimizeValidationException(
            "Management and Instant preview dashboards cannot be created");
      }
      validateDashboard(dashboardDefinitionDto);
    }
    return dashboardService.createNewDashboardAndReturnId(
        userId,
        Optional.ofNullable(dashboardDefinitionDto).orElseGet(DashboardDefinitionRestDto::new));
  }

  @PostMapping(path = "/{id}/copy")
  public IdResponseDto copyDashboard(
      @PathVariable("id") final String dashboardId,
      @RequestParam(name = "collectionId", required = false) String collectionId,
      @RequestParam(name = "name", required = false) final String newDashboardName,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);

    if (collectionId == null) {
      return dashboardService.copyDashboard(dashboardId, userId, newDashboardName);
    } else {
      // 'null' or collectionId value provided
      collectionId = normalizeNullStringValue(collectionId);
      return dashboardService.copyAndMoveDashboard(
          dashboardId, userId, collectionId, newDashboardName);
    }
  }

  @GetMapping(path = "/{id}")
  public AuthorizedDashboardDefinitionResponseDto getDashboard(
      @PathVariable(name = "id") final String dashboardId, final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    final AuthorizedDashboardDefinitionResponseDto dashboardDefinition =
        dashboardService.getDashboardDefinition(dashboardId, userId);
    dashboardRestMapper.prepareRestResponse(
        dashboardDefinition, request.getHeader(X_OPTIMIZE_CLIENT_LOCALE));
    return dashboardDefinition;
  }

  @GetMapping(path = INSTANT_PREVIEW_PATH + "/{procDefKey}")
  public AuthorizedDashboardDefinitionResponseDto getInstantDashboard(
      @PathVariable("procDefKey") final String processDefinitionKey,
      @RequestParam(name = "template", required = false) final String dashboardJsonTemplateFilename,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    final AuthorizedDashboardDefinitionResponseDto dashboardDefinition;
    dashboardDefinition =
        instantPreviewDashboardService.getInstantPreviewDashboard(
            processDefinitionKey, dashboardJsonTemplateFilename, userId);
    dashboardRestMapper.prepareRestResponse(
        dashboardDefinition, request.getHeader(X_OPTIMIZE_CLIENT_LOCALE));
    return dashboardDefinition;
  }

  @GetMapping(path = "/management")
  public AuthorizedDashboardDefinitionResponseDto getManagementDashboard(
      final HttpServletRequest request) {
    final AuthorizedDashboardDefinitionResponseDto dashboardDefinition =
        dashboardService.getManagementDashboard();
    dashboardRestMapper.prepareRestResponse(
        dashboardDefinition, request.getHeader(X_OPTIMIZE_CLIENT_LOCALE));
    return dashboardDefinition;
  }

  @PutMapping(path = "/{id}")
  public void updateDashboard(
      @PathVariable("id") final String dashboardId,
      @Valid @RequestBody final DashboardDefinitionRestDto updatedDashboard,
      final HttpServletRequest request) {
    updatedDashboard.setId(dashboardId);
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    validateDashboard(updatedDashboard);
    dashboardService.updateDashboard(updatedDashboard, userId);
  }

  @DeleteMapping(path = "/{id}")
  public void deleteDashboard(
      @PathVariable("id") final String dashboardId, final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    dashboardService.deleteDashboardAsUser(dashboardId, userId);
  }

  private void validateDashboardTileTypes(final DashboardDefinitionRestDto dashboardDefinitionDto) {
    dashboardDefinitionDto
        .getTiles()
        .forEach(
            report -> {
              if (report.getType() == DashboardTileType.OPTIMIZE_REPORT) {
                if (StringUtils.isEmpty(report.getId())) {
                  throw new OptimizeValidationException("All Optimize Reports must have an ID");
                }
              } else if (!StringUtils.isEmpty(report.getId())) {
                throw new OptimizeValidationException(
                    "Text and external URL tiles must not have an ID");
              }
            });
  }

  private void validateDashboard(final DashboardDefinitionRestDto updatedDashboard) {
    validateDashboardTileTypes(updatedDashboard);
    validateExternalDashboardLinks(updatedDashboard);
    dashboardService.validateDashboardDescription(updatedDashboard.getDescription());
  }

  private void validateExternalDashboardLinks(
      final DashboardDefinitionRestDto dashboardDefinitionDto) {
    final List<String> invalidExternalLinks =
        dashboardDefinitionDto.getTiles().stream()
            .filter(dashboard -> dashboard.getType() == DashboardTileType.EXTERNAL_URL)
            .map(DashboardReportTileDto::getConfiguration)
            .filter(Objects::nonNull)
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .map(reportConfig -> reportConfig.get("external"))
            .filter(Objects::nonNull)
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .filter(externalLinkString -> !isValidURL(externalLinkString))
            .toList();
    if (!invalidExternalLinks.isEmpty()) {
      throw new OptimizeValidationException(
          "Cannot save dashboard as the following external links are invalid: "
              + invalidExternalLinks);
    }
  }

  private boolean isValidURL(final String url) {
    try {
      new URL(url).toURI();
      return true;
    } catch (final MalformedURLException | URISyntaxException e) {
      return false;
    }
  }
}
