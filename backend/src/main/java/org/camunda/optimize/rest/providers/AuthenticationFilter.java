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
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Askar Akhmerov
 *
 */
@Secured
@Provider
@Priority(Priorities.AUTHENTICATION)
@Component
public class AuthenticationFilter implements ContainerRequestFilter {
  private static final String CSV_SUFFIX = ".csv";
  private static final String LOG_IN = "/login";
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private static final String STATUS = "status";

  @Autowired
  private TokenService tokenService;

  @Context
  private ResourceInfo resourceInfo;

  @Override
  public void filter(ContainerRequestContext requestContext) {
    //an exception, do not perform any checks and refreshes
    String path = ((ContainerRequest) requestContext).getPath(false);
    if (path != null && path.toLowerCase().startsWith(STATUS)) {
      return;
    }

    try {
      String token = AuthenticationUtil.getToken(requestContext);
      // Validate the token
      tokenService.validateToken(token);

    } catch (Exception e) {
      if (logger.isDebugEnabled()) {
        logger.debug("Handling authentication token error", e);
      }
      if (path.endsWith(CSV_SUFFIX)) {
        URI loginUri = null;
        try {
          loginUri = new URI(LOG_IN);
        } catch (URISyntaxException e1) {
          logger.error("can't build URI to login");
        }
        requestContext.abortWith(
          Response.temporaryRedirect(loginUri).build()
        );
      } else {
        requestContext.abortWith(
          Response.status(Response.Status.UNAUTHORIZED).build());
      }
    }
  }

}
