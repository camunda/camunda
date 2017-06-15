package org.camunda.optimize.rest.util;

import javax.ws.rs.core.Response;

public class RestResponseUtil {

  public static Response buildOkResponse() {
    return Response.status(200).entity("OK").build();
  }

  public static Response buildOkResponse(Object dtoMessage) {
    return Response.status(200).entity(dtoMessage).build();
  }

  public static Response buildServerErrorResponse(Exception e) {
    return buildServerErrorResponse(e.getMessage());
  }

  public static Response buildServerErrorResponse(String message) {
    return Response
        .serverError()
        .entity("{ \"errorMessage\" : \"" + message + "\"}").build();
  }
}
