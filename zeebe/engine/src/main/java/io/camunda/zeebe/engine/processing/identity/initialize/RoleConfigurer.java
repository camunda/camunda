/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.initialize;

import io.camunda.security.configuration.ConfiguredRole;
import io.camunda.security.validation.RoleValidator;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.stream.Stream;

public class RoleConfigurer implements EntityInitializationConfigurer<ConfiguredRole, RoleRecord> {

  private final RoleValidator validator;

  public RoleConfigurer(final RoleValidator roleValidator) {
    validator = roleValidator;
  }

  @Override
  public Either<List<String>, RoleRecord> configure(final ConfiguredRole role) {
    final List<String> violations = validator.validate(role.roleId(), role.name());

    violations.addAll(validator.validateMembers(role.users(), EntityType.USER));
    violations.addAll(validator.validateMembers(role.clients(), EntityType.CLIENT));
    violations.addAll(validator.validateMembers(role.mappingRules(), EntityType.MAPPING_RULE));
    violations.addAll(validator.validateMembers(role.groups(), EntityType.GROUP));

    if (!violations.isEmpty()) {
      return Either.left(violations);
    }

    return Either.right(mapToRecord(role));
  }

  public List<RoleRecord> configureMembers(final ConfiguredRole role) {
    final String roleId = role.roleId();
    return Stream.of(
            mapToTenantMembers(roleId, role.users(), EntityType.USER),
            mapToTenantMembers(roleId, role.groups(), EntityType.GROUP),
            mapToTenantMembers(roleId, role.mappingRules(), EntityType.MAPPING_RULE),
            mapToTenantMembers(roleId, role.clients(), EntityType.CLIENT))
        .flatMap(s -> s)
        .toList();
  }

  private static Stream<RoleRecord> mapToTenantMembers(
      final String roleId, final List<String> memberIds, final EntityType entityType) {
    if (memberIds == null) {
      return Stream.empty();
    }
    return memberIds.stream()
        .map(id -> new RoleRecord().setRoleId(roleId).setEntityId(id).setEntityType(entityType));
  }

  private RoleRecord mapToRecord(final ConfiguredRole role) {
    return new RoleRecord()
        .setRoleId(role.roleId())
        .setName(role.name())
        .setDescription(role.description());
  }
}
