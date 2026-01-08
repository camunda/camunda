/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.initialize;

import io.camunda.security.configuration.ConfiguredTenant;
import io.camunda.security.validation.TenantValidator;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.stream.Stream;

public class TenantConfigurer
    implements EntityInitializationConfigurer<ConfiguredTenant, TenantRecord> {

  private final TenantValidator validator;

  public TenantConfigurer(final TenantValidator validator) {
    this.validator = validator;
  }

  @Override
  public Either<List<String>, TenantRecord> configure(final ConfiguredTenant tenant) {
    final List<String> violations = validator.validate(tenant.tenantId(), tenant.name());
    violations.addAll(validator.validateTenantMembers(tenant.users(), EntityType.USER));
    violations.addAll(validator.validateTenantMembers(tenant.groups(), EntityType.GROUP));
    violations.addAll(validator.validateTenantMembers(tenant.roles(), EntityType.ROLE));
    violations.addAll(
        validator.validateTenantMembers(tenant.mappingRules(), EntityType.MAPPING_RULE));
    violations.addAll(validator.validateTenantMembers(tenant.clients(), EntityType.CLIENT));

    if (!violations.isEmpty()) {
      return Either.left(violations);
    }

    return Either.right(mapToRecord(tenant));
  }

  public List<TenantRecord> configureMembers(final ConfiguredTenant tenant) {
    final String tenantId = tenant.tenantId();
    return Stream.of(
            mapToTenantMembers(tenantId, tenant.users(), EntityType.USER),
            mapToTenantMembers(tenantId, tenant.groups(), EntityType.GROUP),
            mapToTenantMembers(tenantId, tenant.roles(), EntityType.ROLE),
            mapToTenantMembers(tenantId, tenant.mappingRules(), EntityType.MAPPING_RULE),
            mapToTenantMembers(tenantId, tenant.clients(), EntityType.CLIENT))
        .flatMap(s -> s)
        .toList();
  }

  private static Stream<TenantRecord> mapToTenantMembers(
      final String tenantId, final List<String> memberIds, final EntityType entityType) {
    if (memberIds == null) {
      return Stream.empty();
    }
    return memberIds.stream()
        .map(
            id ->
                new TenantRecord().setTenantId(tenantId).setEntityId(id).setEntityType(entityType));
  }

  private TenantRecord mapToRecord(final ConfiguredTenant tenant) {
    return new TenantRecord()
        .setTenantId(tenant.tenantId())
        .setName(tenant.name())
        .setDescription(tenant.description());
  }
}
