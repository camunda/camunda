/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.identity.usermanagement.service;

import io.camunda.identity.usermanagement.User;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UsersService {
  private final IdentityLocalUserDetailsManager userDetailsManager;

  private final PasswordEncoder passwordEncoder;

  public UsersService(
      final IdentityLocalUserDetailsManager userDetailsManager,
      final PasswordEncoder passwordEncoder) {
    this.userDetailsManager = userDetailsManager;
    this.passwordEncoder = passwordEncoder;
  }

  public User createUser(final User user) {
    try {
      final UserDetails userDetails =
          org.springframework.security.core.userdetails.User.withUsername(user.username())
              .password(user.password())
              .passwordEncoder(passwordEncoder::encode)
              .disabled(!user.enabled())
              .roles("DEFAULT_USER")
              .build();
      userDetailsManager.createUser(userDetails);
      return user;
    } catch (final DuplicateKeyException e) {
      throw new RuntimeException("user.duplicate");
    }
  }

  public void deleteUser(final String username) {
    if (!userDetailsManager.userExists(username)) {
      throw new RuntimeException("user.notFound");
    }
    userDetailsManager.deleteUser(username);
  }

  public Optional<User> findUserByUsername(final String username) {
    try {
      final UserDetails userDetails = userDetailsManager.loadUserByUsername(username);
      return Optional.of(
          new User(userDetails.getUsername(), userDetails.getPassword(), userDetails.isEnabled()));
    } catch (final UsernameNotFoundException e) {
      return Optional.empty();
    }
  }

  public List<User> findAllUsers() {
    return userDetailsManager.loadUsers().stream()
        .map(detail -> new User(detail.getUsername(), detail.getPassword(), detail.isEnabled()))
        .toList();
  }

  public void enableUser(final String username) {
    changeEnabledStatus(username, true);
  }

  public void disableUser(final String username) {
    changeEnabledStatus(username, false);
  }

  private void changeEnabledStatus(final String username, final boolean enabled) {
    final UserDetails existingUser = userDetailsManager.loadUserByUsername(username);
    if (existingUser != null && existingUser.isEnabled() != enabled) {
      final UserDetails user =
          org.springframework.security.core.userdetails.User.withUsername(username)
              .password(existingUser.getPassword())
              .passwordEncoder(passwordEncoder::encode)
              .authorities(existingUser.getAuthorities())
              .disabled(!enabled)
              .build();
      userDetailsManager.updateUser(user);
    }
  }
}
