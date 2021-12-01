/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginatedDataExportDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableDto;
import org.camunda.optimize.dto.optimize.rest.pagination.PaginationScrollableRequestDto;
import org.camunda.optimize.service.export.JsonExportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.ElasticsearchStatusException;
import org.springframework.stereotype.Component;

import javax.validation.Valid;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.time.ZoneId;
import java.util.Optional;

import static org.camunda.optimize.rest.constants.RestConstants.AUTH_COOKIE_TOKEN_VALUE_PREFIX;

@AllArgsConstructor
@Slf4j
@Path(JsonExportRestService.EXPORT_REPORT_PATH)
@Component
public class JsonExportRestService {
  public static final String EXPORT_REPORT_PATH = "public/export/report";
  public static final String REPORT_DATA_SUB_PATH ="/{reportId}/result/json";
  public static final String QUERY_PARAMETER_ACCESS_TOKEN = "access_token";

  private final ConfigurationService configurationService;
  private final JsonExportService jsonExportService;

  @GET
  @Path(REPORT_DATA_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @SneakyThrows
  public PaginatedDataExportDto exportReportData(@Context ContainerRequestContext requestContext,
                                                 @PathParam("reportId") String reportId,
                                                 @BeanParam @Valid final PaginationScrollableRequestDto paginationRequestDto) {
    validateAccessToken(requestContext, getJsonExportAccessToken());
    final ZoneId timezone = ZoneId.of("UTC");
    try {
      return jsonExportService.getJsonForEvaluatedReportResult(reportId, timezone,
                                                               PaginationScrollableDto.fromPaginationRequest(
                                                                 paginationRequestDto)
      );
    } catch (ElasticsearchStatusException e) {
      // In case the user provides a parseable but invalid scroll id (e.g. scroll id was earlier valid, but now
      // expired) the message from ElasticSearch is a bit cryptic. Therefore we extract the useful information so
      // that the user gets an appropriate response.
      throw Optional.ofNullable(e.getCause())
        .filter(pag -> pag.getMessage().contains("search_context_missing_exception"))
        .map(pag -> (Exception)new BadRequestException(pag.getMessage()))
        // In case the exception happened for another reason, just re-throw it as is
        .orElse(e);
    }
  }

  private void validateAccessToken(final ContainerRequestContext requestContext, final String expectedAccessToken) {
    final MultivaluedMap<String, String> queryParameters = requestContext.getUriInfo().getQueryParameters();
    final String queryParameterAccessToken = queryParameters.getFirst(QUERY_PARAMETER_ACCESS_TOKEN);

    if(expectedAccessToken == null) {
      throw new NotAuthorizedException("The parameter 'accessToken' for the JSON Export was not provided in the " +
                                            "configuration, therefore all JSON Export requests will be blocked. Please" +
                                            "check the documentation to set this parameter appropriately and restart " +
                                            "the server");
    }
    if (!expectedAccessToken.equals(extractAuthorizationHeaderToken(requestContext))
      && !expectedAccessToken.equals(queryParameterAccessToken)) {
      throw new NotAuthorizedException("Invalid or no JSON Export api 'accessToken' provided.");
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
