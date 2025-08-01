/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import io.camunda.search.entities.UserEntity;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.UserServices;
import java.util.Optional;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class CamundaUserDetailsService implements UserDetailsService {

  private final UserServices userServices;

  public CamundaUserDetailsService(final UserServices userServices) {
    this.userServices = userServices;
  }

  @Override
  public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
    return Optional.ofNullable(username)
        .filter(u -> !u.isBlank())
        .map(this::getUser)
        .map(this::toUserDetails)
        .orElseThrow(() -> new UsernameNotFoundException(username));
  }

  private UserEntity getUser(final String username) {
    try {
      return userServices.withAuthentication(CamundaAuthentication.anonymous()).getUser(username);
    } catch (final Exception e) {
      throw new UsernameNotFoundException(username, e);
    }
  }

  private UserDetails toUserDetails(final UserEntity user) {
    return User.withUsername(user.username()).password(user.password()).build();
  }
}
