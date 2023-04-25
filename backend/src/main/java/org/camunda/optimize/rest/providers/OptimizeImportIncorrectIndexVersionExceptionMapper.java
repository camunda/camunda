/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.providers;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.ImportedIndexMismatchResponseDto;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.exceptions.OptimizeImportIncorrectIndexVersionException;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
@Slf4j
public class OptimizeImportIncorrectIndexVersionExceptionMapper
  implements ExceptionMapper<OptimizeImportIncorrectIndexVersionException> {
  public static final String ERROR_CODE = "importIndexVersionMismatch";

  private final LocalizationService localizationService;

  public OptimizeImportIncorrectIndexVersionExceptionMapper(@Context final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  @Override
  public Response toResponse(final OptimizeImportIncorrectIndexVersionException exception) {
    log.info("Mapping OptimizeImportIncorrectIndexVersionException");

    return Response
      .status(Response.Status.BAD_REQUEST)
      .type(MediaType.APPLICATION_JSON_TYPE)
      .entity(getIndexMismatchResponseDto(exception))
      .build();
  }

  private ImportedIndexMismatchResponseDto getIndexMismatchResponseDto(OptimizeImportIncorrectIndexVersionException exception) {
    String errorMessage = localizationService.getDefaultLocaleMessageForApiErrorCode(ERROR_CODE);
    String detailedErrorMessage = exception.getMessage();

    return new ImportedIndexMismatchResponseDto(
      ERROR_CODE,
      errorMessage,
      detailedErrorMessage,
      exception.getMismatchingIndices()
    );
  }
}
