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
import io.camunda.identity.automation.usermanagement.model.Profile;
import io.camunda.identity.automation.usermanagement.repository.UserProfileRepository;
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
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@Transactional
public class UserService {
  private final CamundaUserDetailsManager camundaUserDetailsManager;
  private final UserProfileRepository userProfileRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(
      final CamundaUserDetailsManager camundaUserDetailsManager,
      final UserProfileRepository userProfileRepository,
      final PasswordEncoder passwordEncoder) {
    this.camundaUserDetailsManager = camundaUserDetailsManager;
    this.userProfileRepository = userProfileRepository;
    this.passwordEncoder = passwordEncoder;
  }

  public boolean userExistsByUsername(@NotBlank final String username) {
    return camundaUserDetailsManager.userExists(username);
  }

  public CamundaUser createUser(@Valid final CamundaUserWithPassword camundaUserWithPassword)
      throws IllegalArgumentException {
    try {

      final UserDetails userDetails =
          User.withUsername(camundaUserWithPassword.getUsername())
              .password(camundaUserWithPassword.getPassword())
              .passwordEncoder(passwordEncoder::encode)
              .disabled(!camundaUserWithPassword.isEnabled())
              .roles("DEFAULT_USER")
              .build();

      camundaUserDetailsManager.createUser(userDetails);

      final CamundaUser createdUser =
          userProfileRepository.findByUsername(userDetails.getUsername());

      userProfileRepository.save(
          new Profile(
              createdUser.getId(),
              camundaUserWithPassword.getEmail(),
              camundaUserWithPassword.getName()));

      return userProfileRepository.findByUsername(camundaUserWithPassword.getUsername());

    } catch (final DuplicateKeyException e) {
      throw new IllegalArgumentException("User already exists.");
    }
  }

  public void deleteUser(@NotNull(message = "Invalid user ID.") final Long id) {
    final CamundaUser user = findUserById(id);
    camundaUserDetailsManager.deleteUser(user.getUsername());
  }

  public CamundaUser findUserById(@NotNull(message = "Invalid user ID.") final Long id) {
    return userProfileRepository
        .findUserById(id)
        .orElseThrow(() -> new IllegalArgumentException("User invalid."));
  }

  public CamundaUser findUserByUsername(
      @NotBlank(message = "Username invalid.") final String username) {
    final CamundaUser user = userProfileRepository.findByUsername(username);
    if (user == null) {
      throw new IllegalArgumentException("User invalid.");
    }
    return user;
  }

  public List<CamundaUser> findUsersByUsernameIn(
      @NotNull(message = "Username invalid.") final List<String> usernames) {
    return userProfileRepository.findAllByUsernameIn(usernames);
  }

  public List<CamundaUser> findAllUsers() {
    return userProfileRepository.findAllUsers();
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
      userProfileRepository.save(
          new Profile(
              existingUser.getId(), userWithPassword.getEmail(), userWithPassword.getName()));

      return userProfileRepository
          .findUserById(id)
          .orElseThrow(() -> new IllegalArgumentException("User invalid."));
    } catch (final UsernameNotFoundException e) {
      throw new IllegalArgumentException("User invalid.");
    }
  }
}
