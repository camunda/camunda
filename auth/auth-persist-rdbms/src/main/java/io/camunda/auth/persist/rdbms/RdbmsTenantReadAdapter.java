/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.persist.rdbms;

import io.camunda.auth.domain.model.AuthTenant;
import io.camunda.auth.domain.model.MemberType;
import io.camunda.auth.domain.port.outbound.TenantReadPort;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RDBMS-backed implementation of {@link TenantReadPort} using MyBatis. */
public class RdbmsTenantReadAdapter implements TenantReadPort {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsTenantReadAdapter.class);

  private final TenantMapper mapper;

  public RdbmsTenantReadAdapter(final TenantMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Optional<AuthTenant> findById(final String tenantId) {
    LOG.debug("Finding tenant by tenantId={}", tenantId);
    final TenantEntity entity = mapper.findById(tenantId);
    return Optional.ofNullable(entity).map(RdbmsTenantReadAdapter::toDomain);
  }

  @Override
  public List<AuthTenant> findByMember(final String memberId, final MemberType memberType) {
    LOG.debug("Finding tenants by memberId={} memberType={}", memberId, memberType);
    final List<TenantEntity> entities = mapper.findByMember(memberId, memberType.name());
    return entities.stream().map(RdbmsTenantReadAdapter::toDomain).collect(Collectors.toList());
  }

  static AuthTenant toDomain(final TenantEntity entity) {
    return new AuthTenant(
        entity.getTenantKey(), entity.getTenantId(), entity.getName(), entity.getDescription());
  }
}
