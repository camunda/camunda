package org.camunda.optimize.rest.providers;

import org.camunda.optimize.rest.util.AuthenticationUtil;
import org.camunda.optimize.service.security.TokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * @author Askar Akhmerov
 *
 */
@Secured
@Provider
@Priority(Priorities.AUTHENTICATION)
@Component
public class AuthenticationFilter implements ContainerRequestFilter {

  @Autowired
  private TokenService tokenService;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    String token = AuthenticationUtil.getToken(requestContext);

    try {

      // Validate the token
      tokenService.validateToken(token);

    } catch (Exception e) {
      requestContext.abortWith(
          Response.status(Response.Status.UNAUTHORIZED).build());
    }
  }



}
