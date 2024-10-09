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
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.glassfish.jersey.server.ParamException;
import org.slf4j.Logger;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {

  public static final String GENERIC_ERROR_CODE = "serverError";
  public static final String NOT_FOUND_ERROR_CODE = "notFoundError";
  public static final String BAD_REQUEST_ERROR_CODE = "badRequestError";
  private static final Logger log = org.slf4j.LoggerFactory.getLogger(GenericExceptionMapper.class);

  private final LocalizationService localizationService;

  public GenericExceptionMapper(@Context final LocalizationService localizationService) {
    this.localizationService = localizationService;
  }

  private Response buildGenericErrorResponse(final Throwable e) {
    return Response.status(getStatusForError(e))
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(getErrorResponseDto(e))
        .build();
  }

  private static Response.Status getStatusForError(final Throwable e) {
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

  private ErrorResponseDto getErrorResponseDto(final Throwable e) {
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

    final String localizedMessage =
        localizationService.getDefaultLocaleMessageForApiErrorCode(errorCode);

    return new ErrorResponseDto(errorCode, localizedMessage, e.getMessage());
  }

  @Override
  public Response toResponse(final Throwable throwable) {
    log.error("Mapping generic REST error", throwable);
    return buildGenericErrorResponse(throwable);
  }
}
