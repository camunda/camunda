package org.camunda.optimize.rest;

import org.camunda.optimize.dto.optimize.query.security.CredentialsDto;
import org.camunda.optimize.rest.providers.Secured;
import org.camunda.optimize.rest.util.AuthenticationUtil;
import org.camunda.optimize.service.security.AuthenticationService;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
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

import static org.camunda.optimize.rest.util.AuthenticationUtil.createDeleteOptimizeAuthCookie;
import static org.camunda.optimize.rest.util.AuthenticationUtil.createNewOptimizeAuthCookie;

/**
 * Basic implementation of authentication tokens creation based on user credentials.
 * Please note that authentication token validation/refresh is performed in request filters.
 */
@Path("/authentication")
@Component
public class AuthenticationRestService {

  private final AuthenticationService authenticationService;
  private final ConfigurationService configurationService;
  private final SessionService sessionService;

  @Autowired
  public AuthenticationRestService(final AuthenticationService authenticationService,
                                   final ConfigurationService configurationService, final SessionService sessionService) {
    this.authenticationService = authenticationService;
    this.configurationService = configurationService;
    this.sessionService = sessionService;
  }

  /**
   * Authenticate an user given his credentials.
   *
   * @param credentials the credentials of the user.
   * @return Response code 200 (OK) if it was possible to authenticate the user, otherwise status code 401
   * (Unauthorized).
   */
  @POST
  @Produces("application/json")
  @Consumes("application/json")
  public Response authenticateUser(CredentialsDto credentials) {
    String securityToken = authenticationService.authenticateUser(credentials);
    // Return the token on the response
    return Response.ok(securityToken)
      .cookie(createNewOptimizeAuthCookie(securityToken, configurationService.getTokenLifeTimeMinutes()))
      .build();
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
   * @return Status code 200 (OK) if the logout was successful.
   */
  @Secured
  @GET
  @Path("logout")
  public Response logout(@Context ContainerRequestContext requestContext) {
    AuthenticationUtil.getToken(requestContext).ifPresent(sessionService::invalidateAuthToken);
    return Response.status(200).entity("OK").cookie(createDeleteOptimizeAuthCookie()).build();
  }
}
