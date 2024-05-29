/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.service;

import io.camunda.authentication.user.CamundaUserDetailsManager;
import io.camunda.identity.user.CamundaUser;
import io.camunda.identity.user.CamundaUserWithPassword;
import io.camunda.identity.usermanagement.repository.UserRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  @Transactional
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
      userRepository.createProfile(userWithCredential.user());
      return userRepository.loadUser(userWithCredential.user().username());
    } catch (final DuplicateKeyException e) {
      throw new RuntimeException("user.duplicate");
    }
  }

  @Transactional
  public void deleteUser(final int id) {
    final CamundaUser user = findUserById(id);
    userDetailsManager.deleteUser(user.username());
  }

  public CamundaUser findUserById(final Integer id) {
    try {
      return userRepository.loadUserById(id);
    } catch (final UsernameNotFoundException e) {
      throw new RuntimeException("user.notFound");
    }
  }

  public CamundaUser findUserByUsername(final String username) {
    try {
      return userRepository.loadUser(username);
    } catch (final UsernameNotFoundException e) {
      throw new RuntimeException("user.notFound");
    }
  }

  public List<CamundaUser> findAllUsers() {
    return userRepository.loadUsers();
  }

  @Transactional
  public CamundaUser updateUser(final Integer id, final CamundaUserWithPassword user) {
    try {
      if (!Objects.equals(id, user.user().id())) {
        throw new RuntimeException("user.notFound");
      }
      final CamundaUser existingUser = userRepository.loadUserById(id);
      if (existingUser == null || !existingUser.username().equals(user.user().username())) {
        throw new RuntimeException("user.notFound");
      }

      final UserDetails existingUserDetail =
          userDetailsManager.loadUserByUsername(existingUser.username());

      final UserDetails userDetails =
          org.springframework.security.core.userdetails.User.withUsername(existingUser.username())
              .password(user.password())
              .passwordEncoder(passwordEncoder::encode)
              .authorities(existingUserDetail.getAuthorities())
              .disabled(!user.user().enabled())
              .build();
      userDetailsManager.updateUser(userDetails);
      userRepository.updateProfile(user.user());
      return userRepository.loadUserById(id);
    } catch (final UsernameNotFoundException e) {
      throw new RuntimeException("user.notFound");
    }
  }
}
