/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.auth.OidcGroupsLoader;
import io.camunda.security.auth.OidcPrincipalLoader;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

public class OAuth2UserAuthenticationConverter
    implements CamundaAuthenticationConverter<Authentication> {

  private final OidcPrincipalLoader usernameLoader;
  private final OidcGroupsLoader groupsLoader;

  public OAuth2UserAuthenticationConverter(final String usernameClaim, final String groupsClaim) {
    usernameLoader = new OidcPrincipalLoader(usernameClaim, null);
    groupsLoader = new OidcGroupsLoader(groupsClaim);
  }

  @Override
  public boolean supports(final Authentication authentication) {
    return authentication instanceof OAuth2AuthenticationToken;
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    final var oauth2Authentication = (OAuth2AuthenticationToken) authentication;
    final var oauth2User = (OidcUser) oauth2Authentication.getPrincipal();
    final var attributes = oauth2User.getAttributes();

    final var username = usernameLoader.load(attributes).username();
    final var displayName = oauth2User.getFullName();
    final var email = oauth2User.getEmail();
    final var groupIds = groupsLoader.load(attributes);

    return CamundaAuthentication.of(
        b ->
            b.username(username)
                .displayName(displayName)
                .email(email)
                .claims(attributes)
                .groupIds(groupIds));
  }
}
