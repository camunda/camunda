/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.joining;

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
  public List<IdResponseDto> importEntities(@Context final ContainerRequestContext requestContext,
                                            @QueryParam("collectionId") String collectionId,
                                            final String exportedDtoJson) {
    final Set<OptimizeEntityExportDto> exportDtos = readExportDtoOrFailIfInvalid(exportedDtoJson);
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return entityImportService.importEntities(userId, collectionId, exportDtos);
  }

  private Set<OptimizeEntityExportDto> readExportDtoOrFailIfInvalid(final String exportedDtoJson) {
    if (StringUtils.isEmpty(exportedDtoJson)) {
      throw new OptimizeImportFileInvalidException(
        "Could not import entity because the provided file is null or empty."
      );
    }

    final ObjectMapper objectMapper = new ObjectMapperFactory(
      new OptimizeDateTimeFormatterFactory().getObject(),
      configurationService
    ).createOptimizeMapper();

    try {
      //@formatter:off
      final Set<OptimizeEntityExportDto> exportDtos =
        objectMapper.readValue(exportedDtoJson, new TypeReference<Set<OptimizeEntityExportDto>>() {});
      //@formatter:on
      final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
      Set<ConstraintViolation<OptimizeEntityExportDto>> violations = new HashSet<>();
      exportDtos.forEach(exportDto -> {
        violations.addAll(validator.validate(exportDto));
      });
      if (!violations.isEmpty()) {
        throw new OptimizeImportFileInvalidException(
          String.format(
            "Could not import entities because the provided file contains invalid OptimizeExportDtos. " +
              "Errors: %n%s",
            violations.stream()
            .map(c -> c.getPropertyPath() + " " + c.getMessage())
            .collect(joining(","))
          ));
      }
      return exportDtos;
    } catch (JsonProcessingException e) {
      throw new OptimizeImportFileInvalidException(
        "Could not import entities because the provided file is not a valid list of OptimizeEntityExportDtos." +
          " Error:" + e.getMessage());
    }
  }
}
