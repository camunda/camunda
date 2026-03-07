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
import io.camunda.auth.domain.port.outbound.GroupReadPort;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RDBMS-backed implementation of {@link GroupReadPort} using MyBatis. */
public class RdbmsGroupReadAdapter implements GroupReadPort {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsGroupReadAdapter.class);

  private final GroupMapper mapper;

  public RdbmsGroupReadAdapter(final GroupMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<AuthGroup> findById(final String groupId) {
    LOG.debug("Finding group by groupId={}", groupId);
    final GroupEntity entity = mapper.findById(groupId);
    return Optional.ofNullable(entity).map(RdbmsGroupReadAdapter::toDomain);
  }

  @Override
  public List<AuthGroup> findByMember(final String memberId, final MemberType memberType) {
    LOG.debug("Finding groups by memberId={} memberType={}", memberId, memberType);
    final List<GroupEntity> entities = mapper.findByMember(memberId, memberType.name());
    return entities.stream().map(RdbmsGroupReadAdapter::toDomain).collect(Collectors.toList());
  }

  static AuthGroup toDomain(final GroupEntity entity) {
    return new AuthGroup(
        entity.getGroupKey(), entity.getGroupId(), entity.getName(), entity.getDescription());
  }
}
