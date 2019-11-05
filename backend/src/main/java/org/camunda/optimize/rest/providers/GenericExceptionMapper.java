/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.providers;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.glassfish.jersey.server.ParamException;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;


@Provider
@Slf4j
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {

  private static Response buildGenericErrorResponse(Throwable e) {
    return Response
      .status(getStatusForError(e))
      .type(MediaType.APPLICATION_JSON_TYPE)
      .entity(new ErrorResponseDto(e.getMessage()))
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

  @Override
  public Response toResponse(Throwable throwable) {
    log.error("Mapping generic REST error", throwable);
    return buildGenericErrorResponse(throwable);
  }

}
