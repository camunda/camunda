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
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Path(DashboardRestService.DASHBOARD_PATH)
@Component
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

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdResponseDto createNewDashboard(
      @Context final ContainerRequestContext requestContext,
      @Valid final DashboardDefinitionRestDto dashboardDefinitionDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
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

  @POST
  @Path("/{id}/copy")
  @Produces(MediaType.APPLICATION_JSON)
  public IdResponseDto copyDashboard(
      @Context final UriInfo uriInfo,
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String dashboardId,
      @QueryParam("collectionId") String collectionId,
      @QueryParam("name") final String newDashboardName) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);

    if (collectionId == null) {
      return dashboardService.copyDashboard(dashboardId, userId, newDashboardName);
    } else {
      // 'null' or collectionId value provided
      collectionId = normalizeNullStringValue(collectionId);
      return dashboardService.copyAndMoveDashboard(
          dashboardId, userId, collectionId, newDashboardName);
    }
  }

  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedDashboardDefinitionResponseDto getDashboard(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String dashboardId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final AuthorizedDashboardDefinitionResponseDto dashboardDefinition =
        dashboardService.getDashboardDefinition(dashboardId, userId);
    dashboardRestMapper.prepareRestResponse(
        dashboardDefinition, requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE));
    return dashboardDefinition;
  }

  @GET
  @Path(INSTANT_PREVIEW_PATH + "/{procDefKey}")
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedDashboardDefinitionResponseDto getInstantDashboard(
      @Context final ContainerRequestContext requestContext,
      @PathParam("procDefKey") final String processDefinitionKey,
      @QueryParam("template") final String dashboardJsonTemplateFilename) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final AuthorizedDashboardDefinitionResponseDto dashboardDefinition;
    dashboardDefinition =
        instantPreviewDashboardService.getInstantPreviewDashboard(
            processDefinitionKey, dashboardJsonTemplateFilename, userId);
    dashboardRestMapper.prepareRestResponse(
        dashboardDefinition, requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE));
    return dashboardDefinition;
  }

  @GET
  @Path("/management")
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedDashboardDefinitionResponseDto getManagementDashboard(
      @Context final ContainerRequestContext requestContext) {
    final AuthorizedDashboardDefinitionResponseDto dashboardDefinition =
        dashboardService.getManagementDashboard();
    dashboardRestMapper.prepareRestResponse(
        dashboardDefinition, requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE));
    return dashboardDefinition;
  }

  @PUT
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateDashboard(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String dashboardId,
      @Valid final DashboardDefinitionRestDto updatedDashboard) {
    updatedDashboard.setId(dashboardId);
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateDashboard(updatedDashboard);
    dashboardService.updateDashboard(updatedDashboard, userId);
  }

  @DELETE
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteDashboard(
      @Context final ContainerRequestContext requestContext,
      @PathParam("id") final String dashboardId) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
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
