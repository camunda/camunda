/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.automation.usermanagement.service;

import io.camunda.identity.automation.security.CamundaUserDetails;
import io.camunda.identity.automation.security.CamundaUserDetailsManager;
import io.camunda.identity.automation.usermanagement.CamundaUser;
import io.camunda.identity.automation.usermanagement.CamundaUserWithPassword;
import io.camunda.identity.automation.usermanagement.model.NativeUser;
import io.camunda.identity.automation.usermanagement.repository.NativeUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class UserService {
  private final CamundaUserDetailsManager camundaUserDetailsManager;
  private final NativeUserRepository nativeUserRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(
      final CamundaUserDetailsManager camundaUserDetailsManager,
      final NativeUserRepository nativeUserRepository,
      final PasswordEncoder passwordEncoder) {
    this.camundaUserDetailsManager = camundaUserDetailsManager;
    this.nativeUserRepository = nativeUserRepository;
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
      camundaUserDetailsManager.createUser(userDetails);
      final var createdUser = nativeUserRepository.findByUsername(userDetails.getUsername());
      nativeUserRepository.save(
          new NativeUser(
              createdUser.getId(), userWithCredential.getEmail(), userWithCredential.getName()));
      return nativeUserRepository.findByUsername(userWithCredential.getUsername());
    } catch (final DuplicateKeyException e) {
      throw new IllegalArgumentException("User already exists.");
    }
  }

  public void deleteUser(@NotNull(message = "Invalid user ID.") final Long id) {
    final CamundaUser user = findUserById(id);
    camundaUserDetailsManager.deleteUser(user.getUsername());
  }

  public CamundaUser findUserById(@NotNull(message = "Invalid user ID.") final Long id) {
    return nativeUserRepository
        .findUserById(id)
        .orElseThrow(() -> new IllegalArgumentException("User invalid."));
  }

  public CamundaUser findUserByUsername(
      @NotBlank(message = "Username invalid.") final String username) {
    final var user = nativeUserRepository.findByUsername(username);
    if (user == null) {
      throw new IllegalArgumentException("User invalid.");
    }
    return user;
  }

  public List<CamundaUser> findUsersByUsernameIn(
      @NotNull(message = "Username invalid.") final List<String> usernames) {
    return nativeUserRepository.findAllByUsernameIn(usernames);
  }

  public List<CamundaUser> findAllUsers() {
    return nativeUserRepository.findAllUsers();
  }

  public CamundaUser updateUser(
      @NotNull(message = "UserID invalid.") final Long id,
      @Valid final CamundaUserWithPassword userWithPassword) {
    try {
      if (!Objects.equals(id, userWithPassword.getId())) {
        throw new IllegalArgumentException("User invalid.");
      }

      final CamundaUser existingUser = findUserById(id);

      if (existingUser == null
          || !existingUser.getUsername().equals(userWithPassword.getUsername())) {
        throw new IllegalArgumentException("User invalid.");
      }

      final CamundaUserDetails existingUserDetail =
          camundaUserDetailsManager.loadUserByUsername(existingUser.getUsername());

      final UserDetails userDetails =
          User.withUsername(existingUser.getUsername())
              .password(
                  StringUtils.hasText(userWithPassword.getPassword())
                      ? userWithPassword.getPassword()
                      : existingUserDetail.getPassword())
              .passwordEncoder(passwordEncoder::encode)
              .roles(existingUserDetail.getRoles().toArray(new String[0]))
              .disabled(!userWithPassword.isEnabled())
              .build();

      camundaUserDetailsManager.updateUser(userDetails);
      nativeUserRepository.save(
          new NativeUser(
              existingUser.getId(), userWithPassword.getEmail(), userWithPassword.getName()));

      return nativeUserRepository
          .findUserById(id)
          .orElseThrow(() -> new IllegalArgumentException("User invalid."));
    } catch (final UsernameNotFoundException e) {
      throw new IllegalArgumentException("User invalid.");
    }
  }
}
