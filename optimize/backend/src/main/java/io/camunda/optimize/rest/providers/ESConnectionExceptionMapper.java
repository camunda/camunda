/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.elasticsearch.client.transport.NoNodeAvailableException;

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
