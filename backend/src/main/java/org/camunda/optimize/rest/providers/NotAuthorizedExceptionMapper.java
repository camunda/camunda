/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.providers;

import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class NotAuthorizedExceptionMapper implements ExceptionMapper<NotAuthorizedException> {

  @Override
  public Response toResponse(NotAuthorizedException throwable) {
    return Response
      .status(Response.Status.UNAUTHORIZED)
      .type(MediaType.APPLICATION_JSON_TYPE)
      .entity(new ErrorResponseDto(throwable.getMessage())).build();
  }

}
