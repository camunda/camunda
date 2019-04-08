/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.providers;

import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;


@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {
  private final Logger logger = LoggerFactory.getLogger(getClass());

  private static Response buildGenericErrorResponse(Throwable e) {
    return Response
      .status(getStatusForError(e))
      .entity(new ErrorResponseDto(e.getMessage())).build();
  }

  private static Response.Status getStatusForError(Throwable e) {
    final Class<?> errorClass = e.getClass();

    if (NotAuthorizedException.class.equals(errorClass)) {
      return Response.Status.UNAUTHORIZED;
    }
    if (NotFoundException.class.equals(errorClass)) {
      return Response.Status.NOT_FOUND;
    }
    if (ForbiddenException.class.equals(errorClass)) {
      return Response.Status.FORBIDDEN;
    }
    if (BadRequestException.class.equals(errorClass)) {
      return Response.Status.BAD_REQUEST;
    }

    return Response.Status.INTERNAL_SERVER_ERROR;
  }

  @Override
  public Response toResponse(Throwable throwable) {
    logger.error("Mapping generic REST error", throwable);
    return buildGenericErrorResponse(throwable);
  }
}
