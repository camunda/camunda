/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import io.camunda.auth.domain.model.AuthorizationRecord;
import io.camunda.auth.domain.port.outbound.AuthorizationWritePort;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RDBMS-backed implementation of {@link AuthorizationWritePort} using MyBatis. */
public class RdbmsAuthorizationWriteAdapter implements AuthorizationWritePort {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsAuthorizationWriteAdapter.class);

  private final AuthorizationMapper mapper;

  public RdbmsAuthorizationWriteAdapter(final AuthorizationMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void save(final AuthorizationRecord record) {
    LOG.debug("Saving authorization authorizationKey={}", record.authorizationKey());
    final AuthorizationEntity entity = toEntity(record);
    final int updated = mapper.update(entity);
    if (updated == 0) {
      mapper.insert(entity);
    }
  }

  @Override
  public void deleteByKey(final long authorizationKey) {
    LOG.debug("Deleting authorization by authorizationKey={}", authorizationKey);
    mapper.deleteByKey(authorizationKey);
  }

  private static AuthorizationEntity toEntity(final AuthorizationRecord record) {
    final AuthorizationEntity entity = new AuthorizationEntity();
    entity.setAuthorizationKey(record.authorizationKey());
    entity.setOwnerId(record.ownerId());
    entity.setOwnerType(record.ownerType());
    entity.setResourceType(record.resourceType());
    entity.setResourceId(record.resourceId());
    entity.setPermissionTypes(joinPermissionTypes(record.permissionTypes()));
    return entity;
  }

  static String joinPermissionTypes(final Set<String> permissionTypes) {
    if (permissionTypes == null || permissionTypes.isEmpty()) {
      return null;
    }
    return String.join(",", permissionTypes);
  }
}
