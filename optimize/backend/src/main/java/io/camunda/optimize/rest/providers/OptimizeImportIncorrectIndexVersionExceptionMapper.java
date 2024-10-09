/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import io.camunda.optimize.dto.optimize.rest.ImportedIndexMismatchResponseDto;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.exceptions.OptimizeImportIncorrectIndexVersionException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;

@Provider
public class OptimizeImportIncorrectIndexVersionExceptionMapper
    implements ExceptionMapper<OptimizeImportIncorrectIndexVersionException> {

  public static final String ERROR_CODE = "importIndexVersionMismatch";
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(OptimizeImportIncorrectIndexVersionExceptionMapper.class);

  private final LocalizationService localizationService;

  public OptimizeImportIncorrectIndexVersionExceptionMapper(
      @Context final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  @Override
  public Response toResponse(final OptimizeImportIncorrectIndexVersionException exception) {
    log.info("Mapping OptimizeImportIncorrectIndexVersionException");

    return Response.status(Response.Status.BAD_REQUEST)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(getIndexMismatchResponseDto(exception))
        .build();
  }

  private ImportedIndexMismatchResponseDto getIndexMismatchResponseDto(
      final OptimizeImportIncorrectIndexVersionException exception) {
    final String errorMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(ERROR_CODE);
    final String detailedErrorMessage = exception.getMessage();

    return new ImportedIndexMismatchResponseDto(
        ERROR_CODE, errorMessage, detailedErrorMessage, exception.getMismatchingIndices());
  }
}
