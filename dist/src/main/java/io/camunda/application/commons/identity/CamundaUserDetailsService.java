/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.identity;

import io.camunda.search.clients.reader.UserReader;
import io.camunda.search.entities.UserEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserQuery;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class CamundaUserDetailsService implements UserDetailsService {

  private static final Logger LOG = LoggerFactory.getLogger(CamundaUserDetailsService.class);

  private final UserReader userReader;

  public CamundaUserDetailsService(final UserReader userReader) {
    this.userReader = userReader;
    LOG.debug(
        "CamundaUserDetailsService initialized with UserReader implementation: {}",
        userReader.getClass().getName());
  }

  @Override
  public UserDetails loadUserByUsername(final String username) throws UsernameNotFoundException {
    LOG.debug("Loading user details for username: {}", username);
    try {
      // Diagnostic: list all users in the database
      final SearchQueryResult<UserEntity> allUsers =
          userReader.search(UserQuery.of(b -> b), ResourceAccessChecks.disabled());
      LOG.debug(
          "Diagnostic: total users in DB = {}, usernames = {}",
          allUsers.total(),
          allUsers.items() != null
              ? allUsers.items().stream().map(UserEntity::username).toList()
              : "null");
    } catch (final Exception e) {
      LOG.debug("Diagnostic: failed to list all users: {}", e.getMessage());
    }
    UserEntity entity = null;
    try {
      entity =
          Optional.ofNullable(username)
              .filter(u -> !u.isBlank())
              .map(u -> userReader.getById(u, ResourceAccessChecks.disabled()))
              .orElse(null);
    } catch (final Exception e) {
      LOG.debug("Exception from UserReader.getById('{}') : {}", username, e.getMessage(), e);
    }
    if (entity == null) {
      LOG.debug("User '{}' not found via UserReader (getById returned null)", username);
      throw new UsernameNotFoundException(username);
    }
    LOG.debug(
        "Found user '{}' (key={}, passwordPrefix='{}')",
        entity.username(),
        entity.userKey(),
        entity.password() != null && entity.password().length() > 10
            ? entity.password().substring(0, 10) + "..."
            : entity.password());
    return toUserDetails(entity);
  }

  private UserDetails toUserDetails(final UserEntity user) {
    return User.withUsername(user.username()).password(user.password()).build();
  }
}
