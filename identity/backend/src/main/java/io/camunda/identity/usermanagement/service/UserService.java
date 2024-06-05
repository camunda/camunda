/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement.service;

import io.camunda.authentication.user.CamundaUserDetailsManager;
import io.camunda.identity.usermanagement.CamundaUser;
import io.camunda.identity.usermanagement.CamundaUserWithPassword;
import io.camunda.identity.usermanagement.model.Profile;
import io.camunda.identity.usermanagement.repository.UserProfileRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {
  private final CamundaUserDetailsManager userDetailsManager;
  private final UserProfileRepository userProfileRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(
      final CamundaUserDetailsManager userDetailsManager,
      final UserProfileRepository userProfileRepository,
      final PasswordEncoder passwordEncoder) {
    this.userDetailsManager = userDetailsManager;
    this.userProfileRepository = userProfileRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public CamundaUser createUser(final CamundaUserWithPassword userWithCredential) {
    try {
      final UserDetails userDetails =
          User.withUsername(userWithCredential.getUsername())
              .password(userWithCredential.getPassword())
              .passwordEncoder(passwordEncoder::encode)
              .disabled(!userWithCredential.isEnabled())
              .roles("DEFAULT_USER")
              .build();
      userDetailsManager.createUser(userDetails);
      final var createdUser = userProfileRepository.findByUsername(userDetails.getUsername());
      userProfileRepository.save(new Profile(createdUser.getId(), userWithCredential.getEmail()));
      return userProfileRepository.findByUsername(userWithCredential.getUsername());
    } catch (final DuplicateKeyException e) {
      throw new RuntimeException("user.duplicate");
    }
  }

  public void deleteUser(final Long id) {
    final CamundaUser user = findUserById(id);
    userDetailsManager.deleteUser(user.getUsername());
  }

  public CamundaUser findUserById(final Long id) {
    final var user = userProfileRepository.findUserById(id);
    if (user == null) {
      throw new RuntimeException("user.notFound");
    }
    return user;
  }

  public CamundaUser findUserByUsername(final String username) {
    final var user = userProfileRepository.findByUsername(username);
    if (user == null) {
      throw new RuntimeException("user.notFound");
    }
    return user;
  }

  public List<CamundaUser> findUsersByUsernameIn(final List<String> usernames) {
    return userProfileRepository.findAllByUsernameIn(usernames);
  }

  public List<CamundaUser> findAllUsers() {
    return userProfileRepository.findAllUsers();
  }

  public CamundaUser updateUser(final Long id, final CamundaUserWithPassword user) {
    try {
      if (!Objects.equals(id, user.getId())) {
        throw new RuntimeException("user.notFound");
      }
      final CamundaUser existingUser = userProfileRepository.findUserById(id);
      if (existingUser == null || !existingUser.getUsername().equals(user.getUsername())) {
        throw new RuntimeException("user.notFound");
      }

      final UserDetails existingUserDetail =
          userDetailsManager.loadUserByUsername(existingUser.getUsername());

      final UserDetails userDetails =
          User.withUsername(existingUser.getUsername())
              .password(user.getPassword())
              .passwordEncoder(passwordEncoder::encode)
              .authorities(existingUserDetail.getAuthorities())
              .disabled(!user.isEnabled())
              .build();
      userDetailsManager.updateUser(userDetails);
      userProfileRepository.save(new Profile(existingUser.getId(), user.getEmail()));
      return userProfileRepository.findUserById(id);
    } catch (final UsernameNotFoundException e) {
      throw new RuntimeException("user.notFound");
    }
  }
}
