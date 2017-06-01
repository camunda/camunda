package org.camunda.optimize.rest.providers;

import org.camunda.optimize.rest.util.AuthenticationUtil;
import org.camunda.optimize.service.security.TokenService;
import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
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
  private final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);
  private static final String STATUS = "status";

  @Autowired
  private TokenService tokenService;

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {
    //an exception, do not perform any checks and refreshes
    String path = ((ContainerRequest) requestContext).getPath(false);
    if (path != null && path.startsWith(STATUS)) {
      return;
    }

    String token = AuthenticationUtil.getToken(requestContext);

    try {

      // Validate the token
      tokenService.validateToken(token);

    } catch (Exception e) {
      if (logger.isDebugEnabled()) {
        logger.debug("Handling authentication token error", e);
      }
      requestContext.abortWith(
          Response.status(Response.Status.UNAUTHORIZED).build());
    }
  }



}
