/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.DistributedTypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.authorization.DbMembershipState.RelationType;
import io.camunda.zeebe.engine.state.authorization.PersistedMappingRule;
import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.GroupState;
import io.camunda.zeebe.engine.state.immutable.MappingRuleState;
import io.camunda.zeebe.engine.state.immutable.MembershipState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoleState;
import io.camunda.zeebe.engine.state.immutable.TenantState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

public class MappingRuleDeleteProcessor
    implements DistributedTypedRecordProcessor<MappingRuleRecord> {

  private static final String MAPPING_RULE_NOT_FOUND_ERROR_MESSAGE =
      "Expected to delete mapping rule with id '%s', but a mapping rule with this id does not exist.";
  private final MappingRuleState mappingRuleState;
  private final TenantState tenantState;
  private final RoleState roleState;
  private final GroupState groupState;
  private final AuthorizationState authorizationState;
  private final MembershipState membershipState;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final CommandDistributionBehavior commandDistributionBehavior;

  public MappingRuleDeleteProcessor(
      final ProcessingState processingState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final CommandDistributionBehavior commandDistributionBehavior) {
    mappingRuleState = processingState.getMappingRuleState();
    tenantState = processingState.getTenantState();
    roleState = processingState.getRoleState();
    groupState = processingState.getGroupState();
    authorizationState = processingState.getAuthorizationState();
    membershipState = processingState.getMembershipState();
    this.authCheckBehavior = authCheckBehavior;
    this.keyGenerator = keyGenerator;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    this.commandDistributionBehavior = commandDistributionBehavior;
  }

  @Override
  public void processNewCommand(final TypedRecord<MappingRuleRecord> command) {
    final var record = command.getValue();
    final String id = record.getMappingRuleId();
    final var persistedMappingRuleOptional = mappingRuleState.get(id);
    if (persistedMappingRuleOptional.isEmpty()) {
      final var errorMessage = MAPPING_RULE_NOT_FOUND_ERROR_MESSAGE.formatted(id);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    final var authorizationRequest =
        new AuthorizationRequest(
            command, AuthorizationResourceType.MAPPING_RULE, PermissionType.DELETE);
    final var isAuthorized = authCheckBehavior.isAuthorized(authorizationRequest);
    if (isAuthorized.isLeft()) {
      final var rejection = isAuthorized.getLeft();
      rejectionWriter.appendRejection(command, rejection.type(), rejection.reason());
      responseWriter.writeRejectionOnCommand(command, rejection.type(), rejection.reason());
      return;
    }
    final long key = keyGenerator.nextKey();
    deleteMappingRule(persistedMappingRuleOptional.get(), key);
    responseWriter.writeEventOnCommand(key, MappingRuleIntent.DELETED, record, command);

    commandDistributionBehavior
        .withKey(key)
        .inQueue(DistributionQueue.IDENTITY.getQueueId())
        .distribute(command);
  }

  @Override
  public void processDistributedCommand(final TypedRecord<MappingRuleRecord> command) {
    final var record = command.getValue();
    mappingRuleState
        .get(record.getMappingRuleId())
        .ifPresentOrElse(
            persistedMappingRule -> deleteMappingRule(persistedMappingRule, command.getKey()),
            () -> {
              final var errorMessage =
                  MAPPING_RULE_NOT_FOUND_ERROR_MESSAGE.formatted(record.getMappingRuleKey());
              rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
            });

    commandDistributionBehavior.acknowledgeCommand(command);
  }

  private void deleteMappingRule(final PersistedMappingRule mappingRule, final long key) {
    final var mappingRuleId = mappingRule.getMappingRuleId();
    deleteAuthorizations(mappingRuleId);
    for (final var tenantId :
        membershipState.getMemberships(
            EntityType.MAPPING_RULE, mappingRule.getMappingRuleId(), RelationType.TENANT)) {
      final var tenant = tenantState.getTenantById(tenantId).orElseThrow();
      stateWriter.appendFollowUpEvent(
          tenant.getTenantKey(),
          TenantIntent.ENTITY_REMOVED,
          new TenantRecord()
              .setTenantKey(tenant.getTenantKey())
              .setTenantId(tenant.getTenantId())
              .setEntityId(mappingRule.getMappingRuleId())
              .setEntityType(EntityType.MAPPING_RULE));
    }
    for (final var roleId :
        membershipState.getMemberships(EntityType.MAPPING_RULE, mappingRuleId, RelationType.ROLE)) {
      final var role = roleState.getRole(roleId).orElseThrow();
      stateWriter.appendFollowUpEvent(
          role.getRoleKey(),
          RoleIntent.ENTITY_REMOVED,
          new RoleRecord()
              .setRoleKey(role.getRoleKey())
              .setRoleId(roleId)
              .setEntityId(mappingRuleId)
              .setEntityType(EntityType.MAPPING_RULE));
    }
    for (final var groupId :
        membershipState.getMemberships(
            EntityType.MAPPING_RULE, mappingRule.getMappingRuleId(), RelationType.GROUP)) {
      final var group = groupState.get(groupId).orElseThrow();
      stateWriter.appendFollowUpEvent(
          group.getGroupKey(),
          GroupIntent.ENTITY_REMOVED,
          new GroupRecord()
              .setGroupKey(group.getGroupKey())
              .setGroupId(groupId)
              .setEntityId(mappingRule.getMappingRuleId())
              .setEntityType(EntityType.MAPPING_RULE));
    }
    stateWriter.appendFollowUpEvent(
        key,
        MappingRuleIntent.DELETED,
        new MappingRuleRecord().setMappingRuleId(mappingRule.getMappingRuleId()));
  }

  private void deleteAuthorizations(final String mappingRuleId) {
    final var authorizationKeysForMappingRule =
        authorizationState.getAuthorizationKeysForOwner(
            AuthorizationOwnerType.MAPPING_RULE, mappingRuleId);

    authorizationKeysForMappingRule.forEach(
        authorizationKey -> {
          final var authorization = new AuthorizationRecord().setAuthorizationKey(authorizationKey);
          stateWriter.appendFollowUpEvent(
              authorizationKey, AuthorizationIntent.DELETED, authorization);
        });
  }
}
