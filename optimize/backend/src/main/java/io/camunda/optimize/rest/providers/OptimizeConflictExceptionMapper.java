/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import io.camunda.optimize.dto.optimize.rest.ConflictResponseDto;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
public class OptimizeConflictExceptionMapper implements ExceptionMapper<OptimizeConflictException> {

  private final LocalizationService localizationService;

  public OptimizeConflictExceptionMapper(@Context final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  @Override
  public Response toResponse(OptimizeConflictException conflictException) {
    log.info("Mapping OptimizeConflictException");

    return Response.status(Response.Status.CONFLICT)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(getConflictResponseDto(conflictException))
        .build();
  }

  private ConflictResponseDto getConflictResponseDto(OptimizeConflictException conflictException) {
    String errorCode = conflictException.getErrorCode();
    String errorMessage = localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);
    String detailedErrorMessage = conflictException.getMessage();

    return new ConflictResponseDto(
        errorCode, errorMessage, detailedErrorMessage, conflictException.getConflictedItems());
  }
}
