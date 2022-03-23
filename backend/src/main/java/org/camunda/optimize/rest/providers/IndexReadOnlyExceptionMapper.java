/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest.providers;

import org.camunda.optimize.dto.optimize.rest.ErrorResponseDto;
import org.elasticsearch.cluster.block.ClusterBlockException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class IndexReadOnlyExceptionMapper implements ExceptionMapper<ClusterBlockException> {
  private static String ES_READ_ONLY_ERROR = "index read-only";

  @Override
  public javax.ws.rs.core.Response toResponse(ClusterBlockException e) {
    return Response
      .status(Response.Status.INTERNAL_SERVER_ERROR)
      .type(MediaType.APPLICATION_JSON_TYPE)
      .entity(getErrorMessage(e)).build();
  }

  private static ErrorResponseDto getErrorMessage(ClusterBlockException e) {
    if (e.getMessage().contains(ES_READ_ONLY_ERROR)) {
      String message = "Your Elasticsearch index is set to read-only mode. " +
        "The reason could be running out of free hard disk storage. " +
        "You might need to reach out to your system administrator to fix the issue.";
      return new ErrorResponseDto(message);
    } else {
      return new ErrorResponseDto(e.getMessage());
    }
  }

}
