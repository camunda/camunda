/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.export.ExportService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


@Path("/export")
@Secured
@Component
public class ExportRestService {

  @Autowired
  private ExportService exportService;

  @Autowired
  private SessionService sessionService;

  @GET
  // octet stream on success, json on potential error
  @Produces(value = {MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON})
  @Path("csv/{reportId}/{fileName}")
  public Response getCsvReport(@Context ContainerRequestContext requestContext,
                               @PathParam("reportId") String reportId,
                               @PathParam("fileName") String fileName,
                               @QueryParam("excludedColumns") String excludedColumnsString) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    final String resultFileName = fileName == null ? System.currentTimeMillis() + ".csv" : fileName;
    final Set<String> excludedColumns = Optional.ofNullable(excludedColumnsString)
      .map(strings -> Arrays.stream(strings.split(",")).collect(Collectors.toSet()))
      .orElse(Collections.emptySet());

    final Optional<byte[]> csvForReport =
      exportService.getCsvBytesForEvaluatedReportResult(userId, reportId, excludedColumns);

    return csvForReport
      .map(csvBytes -> Response
        .ok(csvBytes, MediaType.APPLICATION_OCTET_STREAM)
        .header("Content-Disposition", "attachment; filename=" + resultFileName)
        .build()
      )
      .orElse(Response.status(Response.Status.NOT_FOUND).build());
  }
}
