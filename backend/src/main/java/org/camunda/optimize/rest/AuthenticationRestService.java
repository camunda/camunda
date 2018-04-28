package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.CredentialsDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.rest.util.AuthenticationUtil;
import org.camunda.optimize.service.security.AuthenticationService;
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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;

/**
 * Basic implementation of authentication tokens creation based on user credentials.
 * Please note that authentication token validation/refresh is performed in request filters.
 */
@Path("/authentication")
@Component
public class AuthenticationRestService {
  private final Logger logger = LoggerFactory.getLogger(AuthenticationRestService.class);

  @Autowired
  private AuthenticationService authenticationService;

  @Autowired
  private TokenService tokenService;

  /**
   * Authenticate an user given his credentials.
   *
   * @param credentials the credentials of the user.
   * @return Response code 200 (OK) if it was possible to authenticate the user, otherwise status code 401 (Unauthorized).
   */
  @POST
  @Produces("application/json")
  @Consumes("application/json")
  public Response authenticateUser(CredentialsDto credentials) {
    try {
      String token = authenticationService.authenticateUser(credentials);

      // Return the token on the response
      return Response.ok(token).build();

    } catch (Exception e) {
      if (logger.isDebugEnabled()) {
        logger.error("Error during user authentication", e);
      }
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
  }

  /**
   * An endpoint to test if you are authenticated.
   *
   * @return Status code 200 (OK) if you are authenticated.
   */
  @Secured
  @GET
  @Path("test")
  public Response testAuthentication() {
    return Response.status(200).entity("OK").build();
  }

  /**
   * Logout yourself from Optimize.
   *
   * @param requestContext
   * @return Status code 200 (OK) if the logout was successful.
   */
  @Secured
  @GET
  @Path("logout")
  public Response logout(@Context ContainerRequestContext requestContext) {
    String token = AuthenticationUtil.getToken(requestContext);
    tokenService.expireToken(token);
    return Response.status(200).entity("OK").build();
  }
}
