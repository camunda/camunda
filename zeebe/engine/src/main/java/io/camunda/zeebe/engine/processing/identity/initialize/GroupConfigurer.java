/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.initialize;

import io.camunda.security.configuration.ConfiguredGroup;
import io.camunda.security.validation.GroupValidator;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.stream.Stream;

public class GroupConfigurer
    implements EntityInitializationConfigurer<ConfiguredGroup, GroupRecord> {

  private final GroupValidator validator;

  public GroupConfigurer(final GroupValidator validator) {
    this.validator = validator;
  }

  @Override
  public Either<List<String>, GroupRecord> configure(final ConfiguredGroup group) {
    final List<String> violations = validator.validate(group.groupId(), group.name());
    violations.addAll(validator.validateMembers(group.users(), EntityType.USER));
    violations.addAll(validator.validateMembers(group.roles(), EntityType.ROLE));
    violations.addAll(validator.validateMembers(group.mappingRules(), EntityType.MAPPING_RULE));
    violations.addAll(validator.validateMembers(group.clients(), EntityType.CLIENT));

    if (!violations.isEmpty()) {
      return Either.left(violations);
    }

    return Either.right(mapToRecord(group));
  }

  public List<GroupRecord> configureMembers(final ConfiguredGroup group) {
    final String tenantId = group.groupId();
    return Stream.of(
            mapToGroupMembers(tenantId, group.users(), EntityType.USER),
            mapToGroupMembers(tenantId, group.roles(), EntityType.ROLE),
            mapToGroupMembers(tenantId, group.mappingRules(), EntityType.MAPPING_RULE),
            mapToGroupMembers(tenantId, group.clients(), EntityType.CLIENT))
        .flatMap(s -> s)
        .toList();
  }

  private static Stream<GroupRecord> mapToGroupMembers(
      final String groupId, final List<String> memberIds, final EntityType entityType) {
    if (memberIds == null) {
      return Stream.empty();
    }
    return memberIds.stream()
        .map(id -> new GroupRecord().setGroupId(groupId).setEntityId(id).setEntityType(entityType));
  }

  private GroupRecord mapToRecord(final ConfiguredGroup group) {
    return new GroupRecord()
        .setGroupId(group.groupId())
        .setName(group.name())
        .setDescription(group.description());
  }
}
