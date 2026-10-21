/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Spring's default {@link OidcUserService}, except that a {@code /userinfo} response that cannot be
 * read fails the login like any other failed userinfo call, instead of with a 500.
 *
 * <p>For a 400 without a {@code WWW-Authenticate} error, Spring's {@code
 * OAuth2ErrorResponseErrorHandler} parses the body as OAuth2 error JSON. A body that isn't JSON,
 * for example an HTML page from a gateway in front of the IdP, makes that parse throw {@link
 * HttpMessageNotReadableException}, which {@link DefaultOAuth2UserService} does not map to an
 * {@link OAuth2AuthenticationException}. It would otherwise escape the login filter. See <a
 * href="https://github.com/camunda/camunda/issues/55732">#55732</a>.
 */
public class UnreadableUserInfoOidcUserService extends OidcUserService {

  /** The error code DefaultOAuth2UserService itself uses for a failed userinfo call. */
  static final String INVALID_USER_INFO_RESPONSE = "invalid_user_info_response";

  public UnreadableUserInfoOidcUserService() {
    this(new DefaultOAuth2UserService());
  }

  UnreadableUserInfoOidcUserService(
      final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate) {
    setOauth2UserService(
        request -> {
          try {
            return delegate.loadUser(request);
          } catch (final HttpMessageNotReadableException e) {
            // Not the exception message: it can quote the response body.
            throw new OAuth2AuthenticationException(
                new OAuth2Error(
                    INVALID_USER_INFO_RESPONSE, "The UserInfo response could not be read", null),
                e);
          }
        });
  }
}
