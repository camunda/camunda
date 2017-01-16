package org.camunda.optimize.rest;

import org.camunda.optimize.dto.CredentialsTO;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.service.security.AuthenticationProvider;
import org.camunda.optimize.service.security.TokenService;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

/**
 * Basic implementation of authentication tokens creation based on user credentials.
 * Please note that authentication token validation/refresh is performed in request filters.
 *
 * @author Askar Akhmerov
 */
@Path("/authentication")
public class Authentication {

  @Inject
  private AuthenticationProvider authenticationProvider;

  @Inject
  private TokenService tokenService;

  @POST
  @Produces("application/json")
  @Consumes("application/json")
  public Response authenticateUser(CredentialsTO credentials) {

    try {

      // Authenticate the user using the credentials provided
      authenticationProvider.authenticate(credentials.getUsername(), credentials.getPassword());

      // Issue a token for the user
      String token = tokenService.issueToken(credentials.getUsername());

      // Return the token on the response
      return Response.ok(token).build();

    } catch (Exception e) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
  }

  @Secured
  @GET
  @Path("test")
  public Response testAuthentication() {
    return Response.status(200).entity("OK").build();
  }
}
