/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import io.camunda.auth.domain.model.AuthUser;
import io.camunda.auth.domain.port.outbound.UserReadPort;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RDBMS-backed implementation of {@link UserReadPort} using MyBatis. */
public class RdbmsUserReadAdapter implements UserReadPort {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsUserReadAdapter.class);

  private final UserMapper mapper;

  public RdbmsUserReadAdapter(final UserMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<AuthUser> findByUsername(final String username) {
    LOG.debug("Finding user by username={}", username);
    final UserEntity entity = mapper.findByUsername(username);
    return Optional.ofNullable(entity).map(RdbmsUserReadAdapter::toDomain);
  }

  @Override
  public Optional<AuthUser> findByKey(final long userKey) {
    LOG.debug("Finding user by userKey={}", userKey);
    final UserEntity entity = mapper.findByKey(userKey);
    return Optional.ofNullable(entity).map(RdbmsUserReadAdapter::toDomain);
  }

  static AuthUser toDomain(final UserEntity entity) {
    return new AuthUser(
        entity.getUserKey(),
        entity.getUsername(),
        entity.getName(),
        entity.getEmail(),
        entity.getPassword());
  }
}
