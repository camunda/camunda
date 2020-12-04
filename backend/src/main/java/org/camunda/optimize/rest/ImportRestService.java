/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.rest.export.OptimizeEntityExportDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.entities.EntityImportService;
import org.camunda.optimize.service.exceptions.OptimizeImportFileInvalidException;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.OptimizeDateTimeFormatterFactory;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.mapper.ObjectMapperFactory;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@AllArgsConstructor
@Path("/import")
@Secured
@Component
public class ImportRestService {

  private final SessionService sessionService;
  private final EntityImportService entityImportService;
  private final ConfigurationService configurationService;

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdResponseDto importReport(@Context final ContainerRequestContext requestContext,
                                    @QueryParam("collectionId") String collectionId,
                                    final String exportedDtoJson) {
    final OptimizeEntityExportDto exportDto = readExportDtoOrFailIfInvalid(exportedDtoJson);
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return entityImportService.importEntity(userId, collectionId, exportDto);
  }

  private OptimizeEntityExportDto readExportDtoOrFailIfInvalid(final String exportedDtoJson) {
    if (StringUtils.isEmpty(exportedDtoJson)) {
      throw new OptimizeImportFileInvalidException("Could not import entity because the provided file is null or " +
                                                     "empty.");
    }

    final ObjectMapper objectMapper = new ObjectMapperFactory(
      new OptimizeDateTimeFormatterFactory().getObject(),
      configurationService
    ).createOptimizeMapper();

    try {
      return objectMapper.readValue(exportedDtoJson, OptimizeEntityExportDto.class);
    } catch (JsonProcessingException e) {
      throw new OptimizeImportFileInvalidException(
        "Could not import entity because the entity is not a valid OptimizeEntityExportDto. Error:" + e.getMessage());
    }
  }
}
