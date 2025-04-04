/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import io.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import io.camunda.optimize.service.LocalizationService;
import io.camunda.optimize.service.security.AuthCookieService;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Provider
@Slf4j
public class NotAuthorizedExceptionMapper implements ExceptionMapper<NotAuthorizedException> {

  private static final String NOT_AUTHORIZED_ERROR_CODE = "notAuthorizedError";
  private final LocalizationService localizationService;
  private final AuthCookieService cookieService;

  public NotAuthorizedExceptionMapper(
      @Context final LocalizationService localizationService,
      @Context final AuthCookieService cookieService) {
    this.localizationService = localizationService;
    this.cookieService = cookieService;
  }

  @Override
  public Response toResponse(final NotAuthorizedException notAuthorizedException) {
    log.debug("Mapping NotAuthorizedException");

    return Response.status(Response.Status.UNAUTHORIZED)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .cookie(cookieService.createDeleteOptimizeAuthNewCookie(true).toArray(new NewCookie[0]))
        .entity(getErrorResponseDto(notAuthorizedException))
        .build();
  }

  private ErrorResponseDto getErrorResponseDto(final NotAuthorizedException exception) {
    final String errorMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(NOT_AUTHORIZED_ERROR_CODE);
    final String detailedErrorMessage = exception.getMessage();

    return new ErrorResponseDto(NOT_AUTHORIZED_ERROR_CODE, errorMessage, detailedErrorMessage);
  }
}
