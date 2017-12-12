package org.camunda.optimize.rest.util;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

public class RestResponseUtil {

  public static Response buildServerErrorResponse(Throwable e) {
    if (e.getClass().equals(NotAuthorizedException.class)) {
      return buildServerAuthenticationErrorResponse(e.getMessage());
    }
    if (e.getClass().equals(NotFoundException.class)) {
      return buildServerNotFoundErrorResponse(e.getMessage());
    }
    return buildServerErrorResponse(e.getMessage());
  }

  private static Response buildServerNotFoundErrorResponse(String message) {
    return Response
        .serverError()
        .status(Response.Status.NOT_FOUND)
        .entity("{ \"errorMessage\" : \"" + message + "\"}").build();
  }

  private static Response buildServerAuthenticationErrorResponse(String message) {
    return Response
        .serverError()
        .status(Response.Status.UNAUTHORIZED)
        .entity("{ \"errorMessage\" : \"" + message + "\"}").build();
  }

  public static Response buildServerErrorResponse(String message) {
    return Response
        .serverError()
        .entity("{ \"errorMessage\" : \"" + message + "\"}").build();
  }
}
