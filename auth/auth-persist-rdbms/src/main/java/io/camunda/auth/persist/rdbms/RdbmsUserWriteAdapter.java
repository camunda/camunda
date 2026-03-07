/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import io.camunda.auth.domain.model.AuthUser;
import io.camunda.auth.domain.port.outbound.UserWritePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RDBMS-backed implementation of {@link UserWritePort} using MyBatis. */
public class RdbmsUserWriteAdapter implements UserWritePort {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsUserWriteAdapter.class);

  private final UserMapper mapper;

  public RdbmsUserWriteAdapter(final UserMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void save(final AuthUser user) {
    LOG.debug("Saving user username={}", user.username());
    final UserEntity entity = toEntity(user);
    final int updated = mapper.update(entity);
    if (updated == 0) {
      mapper.insert(entity);
    }
  }

  @Override
  public void deleteByUsername(final String username) {
    LOG.debug("Deleting user by username={}", username);
    mapper.deleteByUsername(username);
  }

  private static UserEntity toEntity(final AuthUser user) {
    final UserEntity entity = new UserEntity();
    entity.setUserKey(user.userKey());
    entity.setUsername(user.username());
    entity.setName(user.name());
    entity.setEmail(user.email());
    entity.setPassword(user.password());
    return entity;
  }
}
