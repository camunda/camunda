/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.providers;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.camunda.optimize.service.LocalizationService;
import org.glassfish.jersey.server.ParamException;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
@Slf4j
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {

  public static final String GENERIC_ERROR_CODE = "serverError";
  public static final String NOT_FOUND_ERROR_CODE = "notFoundError";
  public static final String BAD_REQUEST_ERROR_CODE = "badRequestError";

  private final LocalizationService localizationService;

  public GenericExceptionMapper(@Context final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  private Response buildGenericErrorResponse(Throwable e) {
    return Response
      .status(getStatusForError(e))
      .type(MediaType.APPLICATION_JSON_TYPE)
      .entity(getErrorResponseDto(e))
      .build();
  }

  private static Response.Status getStatusForError(Throwable e) {
    final Class<?> errorClass = e.getClass();

    if (NotFoundException.class.equals(errorClass)) {
      return Response.Status.NOT_FOUND;
    } else if (BadRequestException.class.equals(errorClass)) {
      return Response.Status.BAD_REQUEST;
    } else if (ParamException.PathParamException.class.equals(errorClass)) {
      return Response.Status.BAD_REQUEST;
    }

    return Response.Status.INTERNAL_SERVER_ERROR;
  }

  private ErrorResponseDto getErrorResponseDto(Throwable e) {
    final Class<?> errorClass = e.getClass();
    final String errorCode;

    if (NotFoundException.class.equals(errorClass)) {
      errorCode = NOT_FOUND_ERROR_CODE;
    } else if (BadRequestException.class.equals(errorClass)
      || ParamException.PathParamException.class.equals(errorClass)) {
      errorCode = BAD_REQUEST_ERROR_CODE;
    } else {
      errorCode = GENERIC_ERROR_CODE;
    }

    String localisedMessage = localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);

    return new ErrorResponseDto(
      errorCode,
      localisedMessage,
      e.getMessage()
    );
  }

  @Override
  public Response toResponse(Throwable throwable) {
    log.error("Mapping generic REST error", throwable);
    return buildGenericErrorResponse(throwable);
  }

}
