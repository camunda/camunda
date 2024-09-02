/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.providers;

import io.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.elasticsearch.cluster.block.ClusterBlockException;

@Provider
public class IndexReadOnlyExceptionMapper implements ExceptionMapper<ClusterBlockException> {
  private static final String ES_READ_ONLY_ERROR = "index read-only";

  @Override
  public Response toResponse(ClusterBlockException e) {
    return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(getErrorMessage(e))
        .build();
  }

  private static ErrorResponseDto getErrorMessage(ClusterBlockException e) {
    if (e.getMessage().contains(ES_READ_ONLY_ERROR)) {
      String message =
          "Your Elasticsearch index is set to read-only mode. "
              + "The reason could be running out of free hard disk storage. "
              + "You might need to reach out to your system administrator to fix the issue.";
      return new ErrorResponseDto(message);
    } else {
      return new ErrorResponseDto(e.getMessage());
    }
  }
}
