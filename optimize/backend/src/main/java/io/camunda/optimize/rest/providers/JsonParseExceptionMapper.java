/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import com.fasterxml.jackson.core.JsonParseException;
import jakarta.annotation.Priority;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;

@Provider
// The priority is needed to make sure it takes precedence over the default Jackson mapper
//  https://stackoverflow.com/a/45482110
@Priority(1)
public class JsonParseExceptionMapper implements ExceptionMapper<JsonParseException> {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(JsonParseExceptionMapper.class);

  @Override
  public Response toResponse(final JsonParseException exception) {
    return Response.status(Response.Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN).build();
  }
}
