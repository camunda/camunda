/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.tasklist.webapp.security.sso;

import static io.camunda.tasklist.webapp.security.TasklistURIs.SSO_CALLBACK;

import com.auth0.AuthenticationController;
import com.auth0.IdentityVerificationException;
import com.auth0.Tokens;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import io.camunda.tasklist.webapp.security.sso.model.ClusterInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.List;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Profile(TasklistProfileService.SSO_AUTH_PROFILE)
public class Auth0Service {
  private static final Logger LOGGER = LoggerFactory.getLogger(Auth0Service.class);
  private static final String LOGOUT_URL_TEMPLATE = "https://%s/v2/logout?client_id=%s&returnTo=%s";
  private static final String PERMISSION_URL_TEMPLATE = "%s/%s";

  private static final List<String> SCOPES =
      List.of(
          "openid", "profile", "email", "offline_access" // request refresh token
          );

  @Autowired private BeanFactory beanFactory;

  @Autowired private AuthenticationController authenticationController;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired
  @Qualifier("auth0_restTemplate")
  private RestTemplate restTemplate;

  public Authentication authenticate(final HttpServletRequest req, final HttpServletResponse res) {
    final Tokens tokens = retrieveTokens(req, res);
    final TokenAuthentication authentication = beanFactory.getBean(TokenAuthentication.class);
    authentication.authenticate(
        tokens.getIdToken(), tokens.getRefreshToken(), tokens.getAccessToken());
    checkPermission(authentication);
    return authentication;
  }

  private void checkPermission(final TokenAuthentication authentication) {
    final HttpHeaders headers = new HttpHeaders();

    headers.setBearerAuth(authentication.getAccessToken());
    final String urlDomain = tasklistProperties.getCloud().getPermissionUrl();
    final String url =
        String.format(
            PERMISSION_URL_TEMPLATE, urlDomain, tasklistProperties.getAuth0().getOrganization());
    final ResponseEntity<ClusterInfo> responseEntity =
        restTemplate.exchange(url, HttpMethod.GET, new HttpEntity(headers), ClusterInfo.class);

    final ClusterInfo clusterInfo = responseEntity.getBody();
    if (clusterInfo.getSalesPlan() != null) {
      authentication.setSalesPlanType(clusterInfo.getSalesPlan().getType());
    }

    final ClusterInfo.Permission tasklistPermissions =
        clusterInfo.getPermissions().getCluster().getTasklist();
    if (tasklistPermissions.getRead()) {
      authentication.addPermission(Permission.READ);
    } else {
      throw new InsufficientAuthenticationException("User doesn't have read access");
    }

    if (tasklistPermissions.getDelete()
        && tasklistPermissions.getCreate()
        && tasklistPermissions.getUpdate()) {
      authentication.addPermission(Permission.WRITE);
    }
  }

  public String getAuthorizeUrl(final HttpServletRequest req, final HttpServletResponse res) {
    return authenticationController
        .buildAuthorizeUrl(req, res, getRedirectURI(req, SSO_CALLBACK, true))
        .withAudience(tasklistProperties.getCloud().getPermissionAudience())
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
