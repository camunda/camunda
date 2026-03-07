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
import io.camunda.auth.domain.port.outbound.TenantWritePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** RDBMS-backed implementation of {@link TenantWritePort} using MyBatis. */
public class RdbmsTenantWriteAdapter implements TenantWritePort {

  private static final Logger LOG = LoggerFactory.getLogger(RdbmsTenantWriteAdapter.class);

  private final TenantMapper mapper;

  public RdbmsTenantWriteAdapter(final TenantMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void save(final AuthTenant tenant) {
    LOG.debug("Saving tenant tenantId={}", tenant.tenantId());
    final TenantEntity entity = toEntity(tenant);
    final int updated = mapper.update(entity);
    if (updated == 0) {
      mapper.insert(entity);
    }
  }

  @Override
  public void deleteById(final String tenantId) {
    LOG.debug("Deleting tenant by tenantId={}", tenantId);
    mapper.deleteById(tenantId);
  }

  @Override
  public void addMember(
      final String tenantId, final String memberId, final MemberType memberType) {
    LOG.debug(
        "Adding member memberId={} memberType={} to tenant tenantId={}",
        memberId,
        memberType,
        tenantId);
    final MembershipEntity entity = new MembershipEntity();
    entity.setEntityId(tenantId);
    entity.setMemberId(memberId);
    entity.setMemberType(memberType.name());
    mapper.insertMember(entity);
  }

  @Override
  public void removeMember(
      final String tenantId, final String memberId, final MemberType memberType) {
    LOG.debug("Removing member memberId={} from tenant tenantId={}", memberId, tenantId);
    final MembershipEntity entity = new MembershipEntity();
    entity.setEntityId(tenantId);
    entity.setMemberId(memberId);
    entity.setMemberType(memberType.name());
    mapper.deleteMember(entity);
  }

  private static TenantEntity toEntity(final AuthTenant tenant) {
    final TenantEntity entity = new TenantEntity();
    entity.setTenantKey(tenant.tenantKey());
    entity.setTenantId(tenant.tenantId());
    entity.setName(tenant.name());
    entity.setDescription(tenant.description());
    return entity;
  }
}
