/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.sso;

import static io.camunda.operate.OperateProfileService.SSO_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.OperateURIs.SSO_CALLBACK_URI;

import com.auth0.AuthenticationController;
import com.auth0.IdentityVerificationException;
import com.auth0.Tokens;
import io.camunda.identity.sdk.Identity;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.util.RetryOperation;
import io.camunda.operate.webapp.security.Permission;
import io.camunda.operate.webapp.security.sso.model.ClusterInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
@Profile(SSO_AUTH_PROFILE)
public class Auth0Service {

  private static final String LOGOUT_URL_TEMPLATE = "https://%s/v2/logout?client_id=%s&returnTo=%s";
  private static final String PERMISSION_URL_TEMPLATE = "%s/%s";

  private static final List<String> SCOPES =
      List.of(
          "openid", "profile", "email", "offline_access" // request refresh token
          );

  @Autowired private BeanFactory beanFactory;

  @Autowired private AuthenticationController authenticationController;

  @Value("${" + OperateProperties.PREFIX + ".auth0.domain}")
  private String domain;

  @Value("${" + OperateProperties.PREFIX + ".auth0.clientId}")
  private String clientId;

  @Autowired private OperateProperties operateProperties;

  @Autowired(required = false)
  @Qualifier("saasIdentity")
  private Identity identity;

  @Autowired
  @Qualifier("auth0_restTemplate")
  private RestTemplate restTemplate;

  public Authentication authenticate(final HttpServletRequest req, final HttpServletResponse res)
      throws Auth0ServiceException {
    try {
      final Tokens tokens = retrieveTokens(req, res);
      final TokenAuthentication authentication =
          new TokenAuthentication(
              operateProperties.getAuth0(), operateProperties.getCloud().getOrganizationId());
      authentication.authenticate(
          tokens.getIdToken(), tokens.getRefreshToken(), tokens.getAccessToken());
      checkPermission(authentication, tokens.getAccessToken());
      authentication.getAuthorizations();
      return authentication;
    } catch (Exception e) {
      throw new Auth0ServiceException(e);
    }
  }

  private void checkPermission(final TokenAuthentication authentication, final String accessToken) {
    final HttpHeaders headers = new HttpHeaders();

    headers.setBearerAuth(accessToken);
    final String urlDomain = operateProperties.getCloud().getPermissionUrl();
    final String url =
        String.format(
            PERMISSION_URL_TEMPLATE, urlDomain, operateProperties.getCloud().getOrganizationId());
    final ResponseEntity<ClusterInfo> responseEntity =
        restTemplate.exchange(url, HttpMethod.GET, new HttpEntity(headers), ClusterInfo.class);
    final ClusterInfo clusterInfo = responseEntity.getBody();

    if (clusterInfo.getSalesPlan() != null) {
      authentication.setSalesPlanType(clusterInfo.getSalesPlan().getType());
    }

    final ClusterInfo.Permission operatePermissions =
        clusterInfo.getPermissions().getCluster().getOperate();
    if (operatePermissions.getRead()) {
      authentication.addPermission(Permission.READ);
    } else {
      throw new InsufficientAuthenticationException("User doesn't have read access");
    }

    if (operatePermissions.getDelete()
        && operatePermissions.getCreate()
        && operatePermissions.getUpdate()) {
      authentication.addPermission(Permission.WRITE);
    }
  }

  public String getAuthorizeUrl(final HttpServletRequest req, final HttpServletResponse res) {
    return authenticationController
        .buildAuthorizeUrl(req, res, getRedirectURI(req, SSO_CALLBACK_URI, true))
        .withAudience(operateProperties.getCloud().getPermissionAudience())
        .withScope(String.join(" ", SCOPES))
        .build();
  }

  public String getLogoutUrlFor(final String returnTo) {
    return String.format(LOGOUT_URL_TEMPLATE, domain, clientId, returnTo);
  }

  public Tokens retrieveTokens(final HttpServletRequest req, final HttpServletResponse res)
      throws Exception {
    return RetryOperation.<Tokens>newBuilder()
        .noOfRetry(10)
        .delayInterval(500, TimeUnit.MILLISECONDS)
        .retryOn(IdentityVerificationException.class)
        .retryConsumer(() -> authenticationController.handle(req, res))
        .message("Auth0Service#retrieveTokens")
        .build()
        .retry();
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
