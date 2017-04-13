package org.camunda.optimize.rest.util;

import javax.ws.rs.core.Response;

public class RestResponseUtil {

  public static Response buildOkResponse() {
    return Response.status(200).entity("OK").build();
  }

  public static Response buildServerErrorResponse(Exception e) {
    return Response
        .serverError()
        .entity("{ \"errorMessage\" : \"It was not possible to compute the import progress. Reason: " +
          e.getMessage() + "\"}").build();
  }
}
