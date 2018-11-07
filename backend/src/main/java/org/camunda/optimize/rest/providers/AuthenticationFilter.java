package org.camunda.optimize.rest.providers;

import org.camunda.optimize.plugin.AuthenticationExtractorProvider;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.rest.util.AuthenticationUtil;
import org.camunda.optimize.service.security.ApplicationAuthorizationService;
import org.camunda.optimize.service.security.SessionService;
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
import java.net.URI;
import java.net.URISyntaxException;

import static org.camunda.optimize.rest.util.AuthenticationUtil.createDeleteOptimizeAuthCookie;

@Secured
@Provider
@Priority(Priorities.AUTHENTICATION)
@Component
public class AuthenticationFilter implements ContainerRequestFilter {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  private static final String CSV_SUFFIX = ".csv";
  private static final String LOG_IN = "/login";
  private static final String STATUS = "status";

  @Autowired
  private SessionService sessionService;

  @Autowired
  private AuthenticationExtractorProvider authenticationExtractorProvider;

  @Autowired
  private EngineContextFactory engineContextFactory;

  @Autowired
  private ApplicationAuthorizationService applicationAuthorizationService;

  @Override
  public void filter(ContainerRequestContext requestContext) {
    String path = retrievePath(requestContext);
    if (path != null && path.toLowerCase().startsWith(STATUS)) {
      // we need that for the status web socket requests to work
      return;
    }
    try {
      String token = AuthenticationUtil.getToken(requestContext);
      boolean isValidToken = sessionService.isValidToken(token);
      if (!isValidToken) {
        handleInvalidToken(requestContext);
      }
    } catch (Exception e) {
      logger.debug("Error during issuing of security token!", e);
      handleInvalidToken(requestContext);
    }
  }

  private String retrievePath(ContainerRequestContext requestContext) {
    return ((ContainerRequest) requestContext).getPath(false);
  }

  private void handleInvalidToken(ContainerRequestContext requestContext) {
    String path = retrievePath(requestContext);

    if (isCSVRequest(path)) {
      redirectToLoginPage(requestContext);
    } else {
      requestContext.abortWith(
        Response.status(Response.Status.UNAUTHORIZED).cookie(createDeleteOptimizeAuthCookie()).build());
    }
  }

  private void redirectToLoginPage(ContainerRequestContext requestContext) {
    URI loginUri = null;
    try {
      loginUri = new URI(LOG_IN);
    } catch (URISyntaxException e) {
      logger.debug("can't build URI to login", e);
    }
    requestContext.abortWith(
      Response.temporaryRedirect(loginUri).cookie(createDeleteOptimizeAuthCookie()).build()
    );
  }

  private boolean isCSVRequest(String path) {
    return path.endsWith(CSV_SUFFIX);
  }

}
