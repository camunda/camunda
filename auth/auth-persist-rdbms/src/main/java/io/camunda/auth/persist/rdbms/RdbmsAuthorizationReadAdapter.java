/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import io.camunda.auth.domain.model.AuthorizationRecord;
import io.camunda.auth.domain.port.outbound.AuthorizationReadPort;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RDBMS-backed implementation of {@link AuthorizationReadPort} using MyBatis. */
public class RdbmsAuthorizationReadAdapter implements AuthorizationReadPort {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsAuthorizationReadAdapter.class);

  private final AuthorizationMapper mapper;

  public RdbmsAuthorizationReadAdapter(final AuthorizationMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public List<AuthorizationRecord> findByOwner(final String ownerId, final String ownerType) {
    LOG.debug("Finding authorizations by ownerId={} ownerType={}", ownerId, ownerType);
    final List<AuthorizationEntity> entities = mapper.findByOwner(ownerId, ownerType);
    return entities.stream()
        .map(RdbmsAuthorizationReadAdapter::toDomain)
        .collect(Collectors.toList());
  }

  @Override
  public List<AuthorizationRecord> findByOwnerAndResourceType(
      final String ownerId, final String ownerType, final String resourceType) {
    LOG.debug(
        "Finding authorizations by ownerId={} ownerType={} resourceType={}",
        ownerId,
        ownerType,
        resourceType);
    final List<AuthorizationEntity> entities =
        mapper.findByOwnerAndResourceType(ownerId, ownerType, resourceType);
    return entities.stream()
        .map(RdbmsAuthorizationReadAdapter::toDomain)
        .collect(Collectors.toList());
  }

  static AuthorizationRecord toDomain(final AuthorizationEntity entity) {
    return new AuthorizationRecord(
        entity.getAuthorizationKey(),
        entity.getOwnerId(),
        entity.getOwnerType(),
        entity.getResourceType(),
        entity.getResourceId(),
        parsePermissionTypes(entity.getPermissionTypes()));
  }

  static Set<String> parsePermissionTypes(final String permissionTypes) {
    if (permissionTypes == null || permissionTypes.isBlank()) {
      return Collections.emptySet();
    }
    return Arrays.stream(permissionTypes.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
