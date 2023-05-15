/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.dashboard.DashboardDefinitionRestDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardReportTileDto;
import org.camunda.optimize.dto.optimize.query.dashboard.tile.DashboardTileType;
import org.camunda.optimize.dto.optimize.rest.AuthorizedDashboardDefinitionResponseDto;
import org.camunda.optimize.rest.mapper.DashboardRestMapper;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.service.dashboard.InstantPreviewDashboardService;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.rest.constants.RestConstants.X_OPTIMIZE_CLIENT_LOCALE;
import static org.camunda.optimize.rest.queryparam.QueryParamUtil.normalizeNullStringValue;

@AllArgsConstructor
@Path("/dashboard")
@Component
public class DashboardRestService {

  private final DashboardService dashboardService;
  private final InstantPreviewDashboardService instantPreviewDashboardService;
  private final SessionService sessionService;
  private final DashboardRestMapper dashboardRestMapper;

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdResponseDto createNewDashboard(@Context final ContainerRequestContext requestContext,
                                          @Valid DashboardDefinitionRestDto dashboardDefinitionDto) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    if (dashboardDefinitionDto != null) {
      if (dashboardDefinitionDto.isManagementDashboard() || dashboardDefinitionDto.isInstantPreviewDashboard()) {
        throw new OptimizeValidationException("Management and Instant Preview Dashboards cannot be created");
      }
      validateDashboard(dashboardDefinitionDto);
    }
    return dashboardService.createNewDashboardAndReturnId(
      userId, Optional.ofNullable(dashboardDefinitionDto).orElseGet(DashboardDefinitionRestDto::new));
  }

  @POST
  @Path("/{id}/copy")
  @Produces(MediaType.APPLICATION_JSON)
  public IdResponseDto copyDashboard(@Context UriInfo uriInfo, @Context ContainerRequestContext requestContext,
                                     @PathParam("id") String dashboardId, @QueryParam("collectionId") String collectionId,
                                     @QueryParam("name") String newDashboardName) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);

    if (collectionId == null) {
      return dashboardService.copyDashboard(dashboardId, userId, newDashboardName);
    } else {
      // 'null' or collectionId value provided
      collectionId = normalizeNullStringValue(collectionId);
      return dashboardService.copyAndMoveDashboard(dashboardId, userId, collectionId, newDashboardName);
    }
  }

  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedDashboardDefinitionResponseDto getDashboard(@Context ContainerRequestContext requestContext,
                                                               @PathParam("id") String dashboardId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    AuthorizedDashboardDefinitionResponseDto dashboardDefinition;
    try {
      dashboardDefinition = dashboardService.getDashboardDefinition(dashboardId, userId);
    } catch (NotFoundException | ForbiddenException e) {
      // This is potentially a case of magic link creation, let's wait a bit and give it another chance
      try {
        Thread.sleep(1500);
      } catch (InterruptedException ex) {
        // Not critical, do nothing
        Thread.currentThread().interrupt();
      }
      dashboardDefinition = dashboardService.getDashboardDefinition(dashboardId, userId);
    }

    dashboardRestMapper.prepareRestResponse(dashboardDefinition, requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE));
    return dashboardDefinition;
  }

  @GET
  @Path("/instant/{procDefKey}")
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedDashboardDefinitionResponseDto getInstantDashboard(@Context ContainerRequestContext requestContext,
                                                                      @PathParam("procDefKey") String processDefinitionKey,
                                                                      @QueryParam("template") String dashboardJsonTemplateFilename) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    AuthorizedDashboardDefinitionResponseDto dashboardDefinition;
    dashboardDefinition = instantPreviewDashboardService.getInstantPreviewDashboard(
      processDefinitionKey,
      dashboardJsonTemplateFilename,
      userId
    );
    dashboardRestMapper.prepareRestResponse(dashboardDefinition, requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE));
    return dashboardDefinition;
  }

  @GET
  @Path("/management")
  @Produces(MediaType.APPLICATION_JSON)
  public AuthorizedDashboardDefinitionResponseDto getManagementDashboard(@Context ContainerRequestContext requestContext) {
    AuthorizedDashboardDefinitionResponseDto dashboardDefinition = dashboardService.getManagementDashboard();
    dashboardRestMapper.prepareRestResponse(dashboardDefinition, requestContext.getHeaderString(X_OPTIMIZE_CLIENT_LOCALE));
    return dashboardDefinition;
  }

  @PUT
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public void updateDashboard(@Context ContainerRequestContext requestContext, @PathParam("id") String dashboardId,
                              @Valid DashboardDefinitionRestDto updatedDashboard) {
    updatedDashboard.setId(dashboardId);
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    validateDashboard(updatedDashboard);
    dashboardService.updateDashboard(updatedDashboard, userId);
  }

  @DELETE
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteDashboard(@Context ContainerRequestContext requestContext, @PathParam("id") String dashboardId) {
    String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    dashboardService.deleteDashboardAsUser(dashboardId, userId);
  }

  private void validateDashboardTileTypes(final DashboardDefinitionRestDto dashboardDefinitionDto) {
    dashboardDefinitionDto.getTiles()
      .forEach(report -> {
        if (report.getType() == DashboardTileType.OPTIMIZE_REPORT) {
          if (StringUtils.isEmpty(report.getId())) {
            throw new OptimizeValidationException("All Optimize Reports must have an ID");
          }
        } else if (!StringUtils.isEmpty(report.getId())) {
          throw new OptimizeValidationException("Text and external URL tiles must not have an ID");
        }
      });
  }

  private void validateDashboard(final DashboardDefinitionRestDto updatedDashboard) {
    validateDashboardTileTypes(updatedDashboard);
    validateExternalDashboardLinks(updatedDashboard);
    dashboardService.validateDashboardDescription(updatedDashboard.getDescription());
  }

  private void validateExternalDashboardLinks(final DashboardDefinitionRestDto dashboardDefinitionDto) {
    final List<String> invalidExternalLinks = dashboardDefinitionDto.getTiles()
      .stream()
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
      .collect(Collectors.toList());
    if (!invalidExternalLinks.isEmpty()) {
      throw new OptimizeValidationException("Cannot save dashboard as the following external links are invalid: " + invalidExternalLinks);
    }
  }

  private boolean isValidURL(String url) {
    try {
      new URL(url).toURI();
      return true;
    } catch (MalformedURLException | URISyntaxException e) {
      return false;
    }
  }

}

