package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.CredentialsDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.rest.util.AuthenticationUtil;
import org.camunda.optimize.service.security.AuthenticationProvider;
import org.camunda.optimize.service.security.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;

/**
 * Basic implementation of authentication tokens creation based on user credentials.
 * Please note that authentication token validation/refresh is performed in request filters.
 *
 * @author Askar Akhmerov
 */
@Path("/authentication")
@Component
public class Authentication {
  private final Logger logger = LoggerFactory.getLogger(Authentication.class);

  @Autowired
  private AuthenticationProvider authenticationProvider;

  @Autowired
  private TokenService tokenService;

  @POST
  @Produces("application/json")
  @Consumes("application/json")
  public Response authenticateUser(CredentialsDto credentials) {

    try {

      // Authenticate the user using the credentials provided
      authenticationProvider.authenticate(credentials.getUsername(), credentials.getPassword());

      // Issue a token for the user
      String token = tokenService.issueToken(credentials.getUsername());

      // Return the token on the response
      return Response.ok(token).build();

    } catch (Exception e) {
      logger.error("Error during user authentication", e);
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
  }

  @Secured
  @GET
  @Path("test")
  public Response testAuthentication() {
    return Response.status(200).entity("OK").build();
  }

  @Secured
  @GET
  @Path("logout")
  public Response logout(ContainerRequestContext requestContext) {
    String token = AuthenticationUtil.getToken(requestContext);
    tokenService.expireToken(token);
    return Response.status(200).entity("OK").build();
  }
}
