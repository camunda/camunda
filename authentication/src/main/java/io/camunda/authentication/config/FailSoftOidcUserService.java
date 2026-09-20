/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * An {@link OidcUserService} that degrades to ID-token-only claims, with a WARN log, when the
 * {@code /userinfo} call fails to produce a usable response: a transport error, the IdP rejecting
 * the access token (e.g. an audience mismatch, see <a
 * href="https://github.com/camunda/camunda/issues/58310">#58310</a>), or a malformed / non-JSON
 * response body (e.g. an HTML error page from a misconfigured gateway in front of the IdP). {@link
 * org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService} maps all three to
 * the same {@code invalid_user_info_response} error code, and none of them yields any claims to
 * validate — so none of them can be the OIDC Core &sect;5.3.2 subject-mismatch violation that
 * follows below. A subject mismatch between the ID token and a userinfo response that <em>did</em>
 * come back still fails the login: {@link OidcUserService#loadUser} enforces that check itself, and
 * this class only intercepts the call, never the result.
 */
public class FailSoftOidcUserService extends OidcUserService {

  private static final Logger LOG = LoggerFactory.getLogger(FailSoftOidcUserService.class);

  // Deliberately a separate instance from `this`: it must always skip the userinfo call, while
  // `this` must attempt it on every request. Reusing `this` for the fallback (by flipping
  // retrieveUserInfo on it directly) would either double the userinfo call or silently disable
  // it on the happy path.
  private final OidcUserService idTokenOnlyFallback = newIdTokenOnlyService();

  public FailSoftOidcUserService(final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate) {
    setOauth2UserService(
        request -> {
          try {
            return delegate.loadUser(request);
          } catch (final OAuth2AuthenticationException ex) {
            throw new UserInfoFetchFailedException(ex.getError(), ex);
          }
        });
  }

  @Override
  public OidcUser loadUser(final OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    try {
      return super.loadUser(userRequest);
    } catch (final UserInfoFetchFailedException ex) {
      // Logged at WARN without the throwable: this fires on every login (and, since
      // OAuth2LoginConfigurer wires this same bean into
      // OidcAuthorizedClientRefreshedEventListener, every access-token refresh) for an IdP
      // with a structural mismatch, so a full stack trace here is pure log noise. The trace
      // is still available at DEBUG for whoever needs to dig in.
      LOG.warn(
          "OIDC /userinfo call failed for registration '{}' ({}): {}; continuing login with "
              + "ID token claims only",
          userRequest.getClientRegistration().getRegistrationId(),
          ex.getError().getErrorCode(),
          ex.getCause().getMessage());
      LOG.debug("OIDC /userinfo call failure detail", ex.getCause());
      return idTokenOnlyFallback.loadUser(userRequest);
    }
  }

  private static OidcUserService newIdTokenOnlyService() {
    final var service = new OidcUserService();
    service.setRetrieveUserInfo(request -> false);
    return service;
  }

  private static final class UserInfoFetchFailedException extends OAuth2AuthenticationException {

    UserInfoFetchFailedException(final OAuth2Error error, final Throwable cause) {
      super(error, cause);
    }
  }
}
