/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.service.UserServices;
import io.camunda.service.search.query.SearchQueryBuilders;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class CamundaUserDetailsService implements UserDetailsService {
  private final UserServices userServices;
  private final PasswordEncoder passwordEncoder;

  public CamundaUserDetailsService(
      final UserServices userServices, final PasswordEncoder passwordEncoder) {
    this.userServices = userServices;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
    if ("admin".equals(username)) {
      return User.builder().username(username).password(passwordEncoder.encode("admin")).build();
    }
    final var userQuery =
        SearchQueryBuilders.userSearchQuery(
            fn -> fn.filter(f -> f.username(username)).page(p -> p.size(1)));
    final var candidates = userServices.search(userQuery).items();
    if (candidates.isEmpty()) {
      throw new UsernameNotFoundException("User not found");
    }
    final var camundaUser = candidates.getFirst().value();
    return User.builder().username(camundaUser.username()).password(camundaUser.password()).build();
  }
}
