/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.providers;

import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.ServiceUnavailableException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
@Slf4j
public class ServiceUnavailableExceptionMapper implements ExceptionMapper<ServiceUnavailableException> {

  @Override
  public Response toResponse(ServiceUnavailableException exception) {
    log.info("Mapping ServiceUnavailableException");

    return Response
      .status(Response.Status.SERVICE_UNAVAILABLE)
      .type(MediaType.TEXT_PLAIN_TYPE)
      .entity("null")
      .build();
  }
}
