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
import io.camunda.auth.domain.port.outbound.RoleWritePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RDBMS-backed implementation of {@link RoleWritePort} using MyBatis. */
public class RdbmsRoleWriteAdapter implements RoleWritePort {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsRoleWriteAdapter.class);

  private final RoleMapper mapper;

  public RdbmsRoleWriteAdapter(final RoleMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void save(final AuthRole role) {
    LOG.debug("Saving role roleId={}", role.roleId());
    final RoleEntity entity = toEntity(role);
    final int updated = mapper.update(entity);
    if (updated == 0) {
      mapper.insert(entity);
    }
  }

  @Override
  public void deleteById(final String roleId) {
    LOG.debug("Deleting role by roleId={}", roleId);
    mapper.deleteById(roleId);
  }

  @Override
  public void addMember(final String roleId, final String memberId, final MemberType memberType) {
    LOG.debug("Adding member memberId={} memberType={} to role roleId={}", memberId, memberType, roleId);
    final MembershipEntity entity = new MembershipEntity();
    entity.setEntityId(roleId);
    entity.setMemberId(memberId);
    entity.setMemberType(memberType.name());
    mapper.insertMember(entity);
  }

  @Override
  public void removeMember(
      final String roleId, final String memberId, final MemberType memberType) {
    LOG.debug("Removing member memberId={} from role roleId={}", memberId, roleId);
    final MembershipEntity entity = new MembershipEntity();
    entity.setEntityId(roleId);
    entity.setMemberId(memberId);
    entity.setMemberType(memberType.name());
    mapper.deleteMember(entity);
  }

  private static RoleEntity toEntity(final AuthRole role) {
    final RoleEntity entity = new RoleEntity();
    entity.setRoleKey(role.roleKey());
    entity.setRoleId(role.roleId());
    entity.setName(role.name());
    entity.setDescription(role.description());
    return entity;
  }
}
