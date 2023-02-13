/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.providers;

import com.fasterxml.jackson.core.JsonParseException;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Priority;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
// The priority is needed to make sure it takes precedence over the default Jackson mapper
//  https://stackoverflow.com/a/45482110
@Priority(1)
@Slf4j
public class JsonParseExceptionMapper implements ExceptionMapper<JsonParseException> {

  @Override
  public Response toResponse(final JsonParseException exception) {
    return Response
      .status(Response.Status.BAD_REQUEST)
      .type(MediaType.TEXT_PLAIN)
      .build();
  }

}
