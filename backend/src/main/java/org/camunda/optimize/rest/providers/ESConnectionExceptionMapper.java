/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.providers;

import org.elasticsearch.client.transport.NoNodeAvailableException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class ESConnectionExceptionMapper implements ExceptionMapper<NoNodeAvailableException> {

  @Override
  public Response toResponse(NoNodeAvailableException exception) {
    return Response.status(exception.status().getStatus())
      .entity(exception.getMessage())
      .type(MediaType.APPLICATION_JSON)
      .build();
  }

}
