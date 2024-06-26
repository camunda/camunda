/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.security;

import io.camunda.identity.usermanagement.model.MappedUser;
import io.camunda.identity.usermanagement.service.MappedUserService;
import io.camunda.identity.usermanagement.service.UserService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
@Profile("auth-oidc")
/** TODO: it should be also accessible for profile based deployment for operate/tasklist/... */
public class CamundaOidcUserService extends OidcUserService {

  final CamundaUserDetailsManager camundaUserDetailsManager;
  final UserService userService;
  final MappedUserService mappedUserService;

  public CamundaOidcUserService(
      final CamundaUserDetailsManager camundaUserDetailsManager,
      final UserService userService,
      final MappedUserService mappedUserService) {
    this.camundaUserDetailsManager = camundaUserDetailsManager;
    this.userService = userService;
    this.mappedUserService = mappedUserService;
  }

  @Override
  public OidcUser loadUser(final OidcUserRequest userRequest) throws OAuth2AuthenticationException {
    final var oidcUser = super.loadUser(userRequest);
    final var providerDetails = userRequest.getClientRegistration().getProviderDetails();
    final var userNameAttributeName =
        providerDetails.getUserInfoEndpoint().getUserNameAttributeName();

    final Set<GrantedAuthority> authorities = new HashSet<>(oidcUser.getAuthorities());
    // TODO here we can use an identity table to look up the user:
    //  not only by name maybe with some advance token mapping rule
    final List<MappedUser> mappedUsers = mappedUserService.loadMappedUsers(oidcUser);

    if (mappedUsers.isEmpty()) {
      // TODO should we create any mapped user in this case?
    }

    mappedUsers.forEach(
        mappedUser -> {
          final UserDetails details =
              camundaUserDetailsManager.loadUserByUsername(mappedUser.getUser().username());
          authorities.addAll(details.getAuthorities());
        });

    return new DefaultOidcUser(
        authorities, oidcUser.getIdToken(), oidcUser.getUserInfo(), userNameAttributeName);
  }
}
