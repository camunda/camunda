/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import io.camunda.auth.domain.model.AuthGroup;
import io.camunda.auth.domain.model.MemberType;
import io.camunda.auth.domain.port.outbound.GroupWritePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RDBMS-backed implementation of {@link GroupWritePort} using MyBatis. */
public class RdbmsGroupWriteAdapter implements GroupWritePort {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsGroupWriteAdapter.class);

  private final GroupMapper mapper;

  public RdbmsGroupWriteAdapter(final GroupMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void save(final AuthGroup group) {
    LOG.debug("Saving group groupId={}", group.groupId());
    final GroupEntity entity = toEntity(group);
    final int updated = mapper.update(entity);
    if (updated == 0) {
      mapper.insert(entity);
    }
  }

  @Override
  public void deleteById(final String groupId) {
    LOG.debug("Deleting group by groupId={}", groupId);
    mapper.deleteById(groupId);
  }

  @Override
  public void addMember(
      final String groupId, final String memberId, final MemberType memberType) {
    LOG.debug(
        "Adding member memberId={} memberType={} to group groupId={}",
        memberId,
        memberType,
        groupId);
    final MembershipEntity entity = new MembershipEntity();
    entity.setEntityId(groupId);
    entity.setMemberId(memberId);
    entity.setMemberType(memberType.name());
    mapper.insertMember(entity);
  }

  @Override
  public void removeMember(
      final String groupId, final String memberId, final MemberType memberType) {
    LOG.debug("Removing member memberId={} from group groupId={}", memberId, groupId);
    final MembershipEntity entity = new MembershipEntity();
    entity.setEntityId(groupId);
    entity.setMemberId(memberId);
    entity.setMemberType(memberType.name());
    mapper.deleteMember(entity);
  }

  private static GroupEntity toEntity(final AuthGroup group) {
    final GroupEntity entity = new GroupEntity();
    entity.setGroupKey(group.groupKey());
    entity.setGroupId(group.groupId());
    entity.setName(group.name());
    entity.setDescription(group.description());
    return entity;
  }
}
