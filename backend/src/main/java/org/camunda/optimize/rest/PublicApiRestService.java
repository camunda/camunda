/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.dto.optimize.rest.export.report.ReportDefinitionExportDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginatedDataExportDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableRequestDto;
import org.camunda.optimize.service.dashboard.DashboardService;
import org.camunda.optimize.service.entities.EntityExportService;
import org.camunda.optimize.service.export.JsonReportResultExportService;
import org.camunda.optimize.service.report.ReportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.ElasticsearchStatusException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

import javax.validation.Valid;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.camunda.optimize.rest.constants.RestConstants.AUTH_COOKIE_TOKEN_VALUE_PREFIX;

@AllArgsConstructor
@Slf4j
@Path(PublicApiRestService.PUBLIC_PATH)
@Component
public class PublicApiRestService {
  public static final String PUBLIC_PATH = "/public";

  public static final String EXPORT_SUB_PATH = "/export";
  public static final String REPORT_SUB_PATH = "/report";
  public static final String DASHBOARD_SUB_PATH = "/dashboard";
  public static final String REPORT_EXPORT_PATH = EXPORT_SUB_PATH + REPORT_SUB_PATH;
  public static final String REPORT_BY_ID_PATH = REPORT_SUB_PATH + "/{reportId}";
  public static final String DASHBOARD_BY_ID_PATH = DASHBOARD_SUB_PATH + "/{dashboardId}";
  public static final String REPORT_EXPORT_BY_ID_PATH = EXPORT_SUB_PATH + REPORT_BY_ID_PATH;
  public static final String REPORT_EXPORT_JSON_SUB_PATH = REPORT_EXPORT_BY_ID_PATH + "/result/json";
  public static final String REPORT_EXPORT_DEFINITION_SUB_PATH = REPORT_EXPORT_PATH + "/definition/json";
  public static final String DASHBOARD_EXPORT_DEFINITION_SUB_PATH = EXPORT_SUB_PATH + DASHBOARD_SUB_PATH + "/definition/json";

  public static final String QUERY_PARAMETER_ACCESS_TOKEN = "access_token";

  private final ConfigurationService configurationService;
  private final JsonReportResultExportService jsonReportResultExportService;
  private final EntityExportService entityExportService;
  private final ReportService reportService;
  private final DashboardService dashboardService;

  @GET
  @Path(REPORT_EXPORT_JSON_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @SneakyThrows
  public PaginatedDataExportDto exportReportData(@Context ContainerRequestContext requestContext,
                                                 @PathParam("reportId") String reportId,
                                                 @BeanParam @Valid final PaginationScrollableRequestDto paginationRequestDto) {
    validateAccessToken(requestContext, getJsonExportAccessToken());
    final ZoneId timezone = ZoneId.of("UTC");
    try {
      return jsonReportResultExportService.getJsonForEvaluatedReportResult(
        reportId,
        timezone,
        PaginationScrollableDto.fromPaginationRequest(paginationRequestDto)
      );
    } catch (ElasticsearchStatusException e) {
      // In case the user provides a parsable but invalid scroll id (e.g. scroll id was earlier valid, but now
      // expired) the message from ElasticSearch is a bit cryptic. Therefore we extract the useful information so
      // that the user gets an appropriate response.
      throw Optional.ofNullable(e.getCause())
        .filter(pag -> pag.getMessage().contains("search_context_missing_exception"))
        .map(pag -> (Exception) new BadRequestException(pag.getMessage()))
        // In case the exception happened for another reason, just re-throw it as is
        .orElse(e);
    }
  }

  @POST
  @Path(REPORT_EXPORT_DEFINITION_SUB_PATH)
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public List<ReportDefinitionExportDto> exportReportDefinition(final @Context ContainerRequestContext requestContext,
                                                                final @RequestBody Set<String> reportIds) {
    validateAccessToken(requestContext, getJsonExportAccessToken());
    return entityExportService.getReportExportDtos(Optional.ofNullable(reportIds).orElse(Collections.emptySet()));
  }

  @POST
  @Path(DASHBOARD_EXPORT_DEFINITION_SUB_PATH)
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public List<OptimizeEntityExportDto> exportDashboardDefinition(final @Context ContainerRequestContext requestContext,
                                                                 final @RequestBody Set<String> dashboardIds) {
    validateAccessToken(requestContext, getJsonExportAccessToken());
    return entityExportService.getDashboardExportDtos(Optional.ofNullable(dashboardIds).orElse(Collections.emptySet()));
  }

  @DELETE
  @Path(REPORT_BY_ID_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteReportDefinition(final @Context ContainerRequestContext requestContext,
                                     final @PathParam("reportId") String reportId) {
    validateAccessToken(requestContext, getJsonExportAccessToken());
    reportService.deleteReport(reportId);
  }

  @DELETE
  @Path(DASHBOARD_BY_ID_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  public void deleteDashboardDefinition(final @Context ContainerRequestContext requestContext,
                                        final @PathParam("dashboardId") String dashboardId) {
    validateAccessToken(requestContext, getJsonExportAccessToken());
    dashboardService.deleteDashboard(dashboardId);
  }

  private void validateAccessToken(final ContainerRequestContext requestContext, final String expectedAccessToken) {
    final MultivaluedMap<String, String> queryParameters = requestContext.getUriInfo().getQueryParameters();
    final String queryParameterAccessToken = queryParameters.getFirst(QUERY_PARAMETER_ACCESS_TOKEN);

    if (expectedAccessToken == null) {
      throw new NotAuthorizedException("The parameter 'accessToken' for the JSON export was not provided in the " +
                                         "configuration, therefore all JSON export requests will be blocked. Please" +
                                         "check the documentation to set this parameter appropriately and restart " +
                                         "the server");
    }
    if (!expectedAccessToken.equals(extractAuthorizationHeaderToken(requestContext))
      && !expectedAccessToken.equals(queryParameterAccessToken)) {
      throw new NotAuthorizedException("Invalid or no JSON export API 'accessToken' provided.");
    }
  }

  private String extractAuthorizationHeaderToken(ContainerRequestContext requestContext) {
    return Optional.ofNullable(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION))
      .map(providedValue -> {
        if (providedValue.startsWith(AUTH_COOKIE_TOKEN_VALUE_PREFIX)) {
          return providedValue.replaceFirst(AUTH_COOKIE_TOKEN_VALUE_PREFIX, "");
        }
        return providedValue;
      }).orElse(null);
  }

  private String getJsonExportAccessToken() {
    return configurationService.getJsonExportConfiguration().getAccessToken();
  }
}
