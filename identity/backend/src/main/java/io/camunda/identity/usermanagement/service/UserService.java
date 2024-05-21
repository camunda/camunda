/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.service;

import io.camunda.identity.authentication.user.CamundaUserDetailsManager;
import io.camunda.identity.user.CamundaUser;
import io.camunda.identity.user.CamundaUserWithPassword;
import io.camunda.identity.usermanagement.repository.UserRepository;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
  private final CamundaUserDetailsManager userDetailsManager;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(
      final CamundaUserDetailsManager userDetailsManager,
      final UserRepository userRepository,
      final PasswordEncoder passwordEncoder) {
    this.userDetailsManager = userDetailsManager;
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public CamundaUser createUser(final CamundaUserWithPassword userWithCredential) {
    try {
      final UserDetails userDetails =
          org.springframework.security.core.userdetails.User.withUsername(
                  userWithCredential.user().username())
              .password(userWithCredential.password())
              .passwordEncoder(passwordEncoder::encode)
              .disabled(!userWithCredential.user().enabled())
              .roles("DEFAULT_USER")
              .build();
      userDetailsManager.createUser(userDetails);
      return new CamundaUser(userDetails.getUsername(), userDetails.isEnabled());
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

  public CamundaUser findUserByUsername(final String username) {
    try {
      final UserDetails userDetails = userDetailsManager.loadUserByUsername(username);
      return new CamundaUser(userDetails.getUsername(), userDetails.isEnabled());
    } catch (final UsernameNotFoundException e) {
      throw new RuntimeException("user.notFound");
    }
  }

  public List<CamundaUser> findAllUsers() {
    return userRepository.loadUsers();
  }

  public CamundaUser updateUser(final String username, final CamundaUserWithPassword user) {
    try {
      if (!username.equals(user.user().username())) {
        throw new RuntimeException("user.notFound");
      }

      final UserDetails existingUser = userDetailsManager.loadUserByUsername(username);

      final UserDetails userDetails =
          org.springframework.security.core.userdetails.User.withUsername(username)
              .password(user.password())
              .passwordEncoder(passwordEncoder::encode)
              .authorities(existingUser.getAuthorities())
              .disabled(!user.user().enabled())
              .build();
      userDetailsManager.updateUser(userDetails);
      return new CamundaUser(userDetails.getUsername(), userDetails.isEnabled());
    } catch (final UsernameNotFoundException e) {
      throw new RuntimeException("user.notFound");
    }
  }
}
