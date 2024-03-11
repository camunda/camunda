/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.providers;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.DefinitionExceptionResponseDto;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.exceptions.OptimizeImportDefinitionDoesNotExistException;

@Provider
@Slf4j
public class OptimizeImportDefinitionDoesNotExistsExceptionMapper
    implements ExceptionMapper<OptimizeImportDefinitionDoesNotExistException> {
  public static final String ERROR_CODE = "importDefinitionForbidden";

  private final LocalizationService localizationService;

  public OptimizeImportDefinitionDoesNotExistsExceptionMapper(
      @Context final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  @Override
  public Response toResponse(final OptimizeImportDefinitionDoesNotExistException exception) {
    log.info("Mapping OptimizeImportDefinitionDoesNotExistException");

    return Response.status(Response.Status.BAD_REQUEST)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(getMissingDefinitionResponseDto(exception))
        .build();
  }

  private DefinitionExceptionResponseDto getMissingDefinitionResponseDto(
      OptimizeImportDefinitionDoesNotExistException exception) {
    String errorCode = exception.getErrorCode();
    String errorMessage = localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);
    String detailedErrorMessage = exception.getMessage();

    return new DefinitionExceptionResponseDto(
        errorCode, errorMessage, detailedErrorMessage, exception.getMissingDefinitions());
  }
}
