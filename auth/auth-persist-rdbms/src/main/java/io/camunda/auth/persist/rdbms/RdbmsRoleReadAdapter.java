/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import io.camunda.auth.domain.model.AuthRole;
import io.camunda.auth.domain.model.MemberType;
import io.camunda.auth.domain.port.outbound.RoleReadPort;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RDBMS-backed implementation of {@link RoleReadPort} using MyBatis. */
public class RdbmsRoleReadAdapter implements RoleReadPort {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsRoleReadAdapter.class);

  private final RoleMapper mapper;

  public RdbmsRoleReadAdapter(final RoleMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<AuthRole> findById(final String roleId) {
    LOG.debug("Finding role by roleId={}", roleId);
    final RoleEntity entity = mapper.findById(roleId);
    return Optional.ofNullable(entity).map(RdbmsRoleReadAdapter::toDomain);
  }

  @Override
  public List<AuthRole> findByMember(final String memberId, final MemberType memberType) {
    LOG.debug("Finding roles by memberId={} memberType={}", memberId, memberType);
    final List<RoleEntity> entities = mapper.findByMember(memberId, memberType.name());
    return entities.stream().map(RdbmsRoleReadAdapter::toDomain).collect(Collectors.toList());
  }

  static AuthRole toDomain(final RoleEntity entity) {
    return new AuthRole(
        entity.getRoleKey(), entity.getRoleId(), entity.getName(), entity.getDescription());
  }
}
