package org.camunda.optimize.rest.providers;

import org.camunda.optimize.rest.util.AuthenticationUtil;
import org.camunda.optimize.service.security.SessionService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.ext.Provider;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.camunda.optimize.rest.util.AuthenticationUtil.createNewOptimizeAuthCookie;

@Provider
@Priority(Priorities.AUTHENTICATION)
@Component
public class ResponseCookieRefreshFilter implements ContainerResponseFilter {

  private final SessionService sessionService;
  private final ConfigurationService configurationService;

  @Autowired
  public ResponseCookieRefreshFilter(final SessionService sessionService,
                                     final ConfigurationService configurationService) {
    this.sessionService = sessionService;
    this.configurationService = configurationService;
  }

  @Override
  public void filter(final ContainerRequestContext requestContext, final ContainerResponseContext responseContext) {
    AuthenticationUtil.getToken(requestContext)
      .filter(token -> sessionService.getExpiresAtLocalDateTime(token)
        .map(expiresAt -> {
          final LocalDateTime now = LocalDateUtil.getCurrentLocalDateTime();
          return expiresAt.isAfter(now)
            // token reached last third of lifeTime => refresh
            && Duration.between(now, expiresAt).toMinutes() <= (configurationService.getTokenLifeTimeMinutes() / 3);
        })
        .orElse(false)
      )
      .flatMap(sessionService::refreshAuthToken)
      .ifPresent(
        newToken -> responseContext.getHeaders().add(
          HttpHeaders.SET_COOKIE, createNewOptimizeAuthCookie(newToken).toCookie().toString()
        )
      );
  }
}
