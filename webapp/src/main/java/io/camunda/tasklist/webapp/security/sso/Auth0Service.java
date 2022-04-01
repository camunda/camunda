/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.security.sso;

import static io.camunda.tasklist.webapp.security.TasklistURIs.SSO_CALLBACK;

import com.auth0.AuthenticationController;
import com.auth0.IdentityVerificationException;
import com.auth0.Tokens;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import java.time.Duration;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile(TasklistProfileService.SSO_AUTH_PROFILE)
public class Auth0Service {

  private static final Logger LOGGER = LoggerFactory.getLogger(Auth0Service.class);
  private static final String LOGOUT_URL_TEMPLATE = "https://%s/v2/logout?client_id=%s&returnTo=%s";
  private static final String AUDIENCE_URL_TEMPLATE = "https://%s/userinfo";

  private static final List<String> SCOPES =
      List.of(
          "openid", "profile", "email", "offline_access" // request refresh token
          );

  @Autowired private BeanFactory beanFactory;

  @Autowired private AuthenticationController authenticationController;

  @Autowired private TasklistProperties tasklistProperties;

  public void authenticate(final HttpServletRequest req, final HttpServletResponse res) {
    final Tokens tokens = retrieveTokens(req, res);
    final TokenAuthentication authentication = beanFactory.getBean(TokenAuthentication.class);
    authentication.authenticate(tokens.getIdToken(), tokens.getRefreshToken());
    SecurityContextHolder.getContext().setAuthentication(authentication);
    sessionExpiresWhenAuthenticationExpires(req);
  }

  private void sessionExpiresWhenAuthenticationExpires(final HttpServletRequest req) {
    req.getSession().setMaxInactiveInterval(-1);
  }

  public String getAuthorizeUrl(final HttpServletRequest req, final HttpServletResponse res) {
    return authenticationController
        .buildAuthorizeUrl(req, res, getRedirectURI(req, SSO_CALLBACK, true))
        .withAudience(
            String.format(AUDIENCE_URL_TEMPLATE, tasklistProperties.getAuth0().getBackendDomain()))
        .withScope(String.join(" ", SCOPES))
        .build();
  }

  public String getLogoutUrlFor(final String returnTo) {
    return String.format(
        LOGOUT_URL_TEMPLATE,
        tasklistProperties.getAuth0().getDomain(),
        tasklistProperties.getAuth0().getClientId(),
        returnTo);
  }

  public Tokens retrieveTokens(final HttpServletRequest req, final HttpServletResponse res) {
    final String operationName = "retrieve tokens";
    final RetryPolicy<Tokens> retryPolicy =
        new RetryPolicy<Tokens>()
            .handle(IdentityVerificationException.class)
            .withDelay(Duration.ofMillis(500))
            .withMaxAttempts(10)
            .onRetry(e -> LOGGER.debug("Retrying #{} {}", e.getAttemptCount(), operationName))
            .onAbort(e -> LOGGER.error("Abort {} by {}", operationName, e.getFailure()))
            .onRetriesExceeded(
                e ->
                    LOGGER.error("Retries {} exceeded for {}", e.getAttemptCount(), operationName));
    return Failsafe.with(retryPolicy).get(() -> authenticationController.handle(req, res));
  }

  public String getRedirectURI(final HttpServletRequest req, final String redirectTo) {
    return getRedirectURI(req, redirectTo, false);
  }

  public String getRedirectURI(
      final HttpServletRequest req, final String redirectTo, boolean omitContextPath) {
    String redirectUri = req.getScheme() + "://" + req.getServerName();
    if ((req.getScheme().equals("http") && req.getServerPort() != 80)
        || (req.getScheme().equals("https") && req.getServerPort() != 443)) {
      redirectUri += ":" + req.getServerPort();
    }
    final String clusterId = req.getContextPath().replace("/", "");
    if (omitContextPath) {
      return redirectUri + redirectTo + "?uuid=" + clusterId;
    } else {
      return redirectUri + req.getContextPath() + redirectTo;
    }
  }
}
