/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import io.camunda.optimize.dto.optimize.rest.DefinitionExceptionResponseDto;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.exceptions.OptimizeImportForbiddenException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;

@Provider
public class OptimizeImportForbiddenExceptionMapper
    implements ExceptionMapper<OptimizeImportForbiddenException> {

  public static final String ERROR_CODE = "importDefinitionForbidden";
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(OptimizeImportForbiddenExceptionMapper.class);

  private final LocalizationService localizationService;

  public OptimizeImportForbiddenExceptionMapper(
      @Context final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  @Override
  public Response toResponse(final OptimizeImportForbiddenException exception) {
    log.info("Mapping OptimizeImportForbiddenException");

    return Response.status(Response.Status.FORBIDDEN)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(getForbiddenDefinitionResponseDto(exception))
        .build();
  }

  private DefinitionExceptionResponseDto getForbiddenDefinitionResponseDto(
      final OptimizeImportForbiddenException exception) {
    final String errorCode = exception.getErrorCode();
    final String errorMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);
    final String detailedErrorMessage = exception.getMessage();

    return new DefinitionExceptionResponseDto(
        errorCode, errorMessage, detailedErrorMessage, exception.getForbiddenDefinitions());
  }
}
