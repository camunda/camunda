/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.providers;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.service.LocalizationService;
import org.camunda.optimize.service.security.AuthCookieService;

@Provider
@Slf4j
public class NotAuthorizedExceptionMapper implements ExceptionMapper<NotAuthorizedException> {

  private final LocalizationService localizationService;
  private final AuthCookieService cookieService;
  private static final String NOT_AUTHORIZED_ERROR_CODE = "notAuthorizedError";

  public NotAuthorizedExceptionMapper(
      @Context final LocalizationService localizationService,
      @Context final AuthCookieService cookieService) {
    this.localizationService = localizationService;
    this.cookieService = cookieService;
  }

  @Override
  public Response toResponse(NotAuthorizedException notAuthorizedException) {
    log.debug("Mapping NotAuthorizedException");

    return Response.status(Response.Status.UNAUTHORIZED)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .cookie(cookieService.createDeleteOptimizeAuthNewCookie(true))
        .entity(getErrorResponseDto(notAuthorizedException))
        .build();
  }

  private ErrorResponseDto getErrorResponseDto(NotAuthorizedException exception) {
    String errorMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(NOT_AUTHORIZED_ERROR_CODE);
    String detailedErrorMessage = exception.getMessage();

    return new ErrorResponseDto(NOT_AUTHORIZED_ERROR_CODE, errorMessage, detailedErrorMessage);
  }
}
