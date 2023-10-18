/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.security.identity;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.authentication.Tokens;
import io.camunda.identity.sdk.authentication.dto.AuthCodeDto;
import io.camunda.operate.property.OperateProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static io.camunda.operate.OperateProfileService.IDENTITY_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.OperateURIs.IDENTITY_CALLBACK_URI;

@Component
@Profile(IDENTITY_AUTH_PROFILE)
public class IdentityService {

  private final OperateProperties operateProperties;

  private final Identity identity;

  private final IdentityRetryService identityRetryService;

  @Autowired
  public IdentityService(IdentityRetryService identityRetryService, OperateProperties operateProperties, Identity identity) {
    this.identityRetryService = identityRetryService;
    this.operateProperties = operateProperties;
    this.identity = identity;
  }

  public String getRedirectUrl(final HttpServletRequest req) {
    return identity
        .authentication()
        .authorizeUriBuilder(getRedirectURI(req, IDENTITY_CALLBACK_URI))
        .build()
        .toString();
  }

  public void logout() {
    final IdentityAuthentication authentication =
        (IdentityAuthentication) SecurityContextHolder.getContext().getAuthentication();
    identity.authentication().revokeToken(authentication.getTokens().getRefreshToken());
  }

  public String getRedirectURI(final HttpServletRequest req, final String redirectTo) {
    final String fixedRedirectRootUrl = operateProperties.getIdentity().getRedirectRootUrl();

    String redirectRootUri;
    if (StringUtils.isNotBlank(fixedRedirectRootUrl)) {
      redirectRootUri = fixedRedirectRootUrl;
    } else {
      redirectRootUri = req.getScheme() + "://" + req.getServerName();
      if ((req.getScheme().equals("http") && req.getServerPort() != 80)
          || (req.getScheme().equals("https") && req.getServerPort() != 443)) {
        redirectRootUri += ":" + req.getServerPort();
      }
    }

    final String result;
    if (contextPathIsUUID(req.getContextPath())) {
      final String clusterId = req.getContextPath().replace("/", "");
      result = redirectRootUri + redirectTo + "?uuid=" + clusterId;
    } else {
      result = redirectRootUri + req.getContextPath() + redirectTo;
    }
    return result;
  }

  public IdentityAuthentication getAuthenticationFor(
      final HttpServletRequest req, final AuthCodeDto authCodeDto) throws Exception {
    final Tokens tokens =
        identityRetryService.requestWithRetry(
            () ->
                identity
                    .authentication()
                    .exchangeAuthCode(authCodeDto, getRedirectURI(req, IDENTITY_CALLBACK_URI)),
            "IdentityService#getAuthentication");
    final IdentityAuthentication authentication = new IdentityAuthentication();
    authentication.authenticate(tokens);
    return authentication;
  }

  private boolean contextPathIsUUID(String contextPath) {
    try {
      UUID.fromString(contextPath.replace("/", ""));
      return true;
    } catch (Exception e) {
      // Assume it isn't a UUID
      return false;
    }
  }
}
