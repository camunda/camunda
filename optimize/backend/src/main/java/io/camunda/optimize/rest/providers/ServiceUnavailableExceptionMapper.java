/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;

@Provider
public class ServiceUnavailableExceptionMapper
    implements ExceptionMapper<ServiceUnavailableException> {

  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(ServiceUnavailableExceptionMapper.class);

  @Override
  public Response toResponse(final ServiceUnavailableException exception) {
    log.info("Mapping ServiceUnavailableException");

    return Response.status(Response.Status.SERVICE_UNAVAILABLE)
        .type(MediaType.TEXT_PLAIN_TYPE)
        .entity("null")
        .build();
  }
}
