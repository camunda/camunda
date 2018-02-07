package org.camunda.optimize.rest.providers;

import org.camunda.optimize.rest.ReportRestService;
import org.camunda.optimize.rest.SharingRestService;
import org.camunda.optimize.rest.util.AuthenticationUtil;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.security.SharingService;
import org.camunda.optimize.service.security.TokenService;
import org.glassfish.jersey.server.ContainerRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Priority;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Askar Akhmerov
 *
 */
@Secured
@Provider
@Priority(Priorities.AUTHENTICATION)
@Component
public class AuthenticationFilter implements ContainerRequestFilter {
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private static final String STATUS = "status";

  @Autowired
  private TokenService tokenService;

  @Autowired
  private SharingService sharingService;

  @Context
  private ResourceInfo resourceInfo;

  @Override
  public void filter(ContainerRequestContext requestContext) {
    //an exception, do not perform any checks and refreshes
    String path = ((ContainerRequest) requestContext).getPath(false);
    if (path != null && path.toLowerCase().startsWith(STATUS)) {
      return;
    }

    if (isSharable(requestContext.getMethod()) && authorizedToView(path)) {
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

  private boolean authorizedToView(String path) {
    boolean result;
    String[] strings = path.split("/");
    result = sharingService.findShare(strings[2]) != null;
    return result;
  }

  private boolean isSharable(String method) {
    return HttpMethod.GET.equals(method) && getSharableMethods().contains(resourceInfo.getResourceMethod());
  }

  private Set<Method> getSharableMethods() {
    Set<Method> sharableMethods = new HashSet<>();
    try {
      sharableMethods.add(SharingRestService.class.getMethod("evaluateReport", String.class));
    } catch (NoSuchMethodException e) {
      logger.error("can't build shared methods list", e);
    }
    return sharableMethods;
  }


}
