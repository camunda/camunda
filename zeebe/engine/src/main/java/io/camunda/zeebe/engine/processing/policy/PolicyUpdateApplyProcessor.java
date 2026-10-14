/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.PolicyUpdateState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.PolicyDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Applies the next chunk of an open policy update. Each command applies exactly one chunk, so the
 * events of a command stay bounded by the chunk budget. The position of the next chunk lives in the
 * assembly state; the last chunk completes the update.
 */
@NullMarked
public final class PolicyUpdateApplyProcessor implements TypedRecordProcessor<PolicyUpdateRecord> {

  /** Key of a command that creates an entity; the existing processor generates the key. */
  private static final long NEW_ENTITY_KEY = -1L;

  private static final Logger LOG = LoggerFactory.getLogger(PolicyUpdateApplyProcessor.class);

  private final PolicyUpdateState policyUpdateState;
  private final ProcessingState processingState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedCommandWriter commandWriter;

  public PolicyUpdateApplyProcessor(
      final PolicyUpdateState policyUpdateState,
      final ProcessingState processingState,
      final Writers writers) {
    this.policyUpdateState = policyUpdateState;
    this.processingState = processingState;
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    commandWriter = writers.command();
  }

  @Override
  public void processRecord(final TypedRecord<PolicyUpdateRecord> command) {
    final var record = command.getValue();
    final var envelopeId = record.getEnvelopeId();
    final var version = record.getPolicyVersion();
    LOG.debug("Applying next chunk of policy update '{}' (version {})", envelopeId, version);

    final var assembly = policyUpdateState.getAssembly(envelopeId);
    if (assembly.isEmpty() || assembly.get().getPolicyVersion() != version) {
      rejectionWriter.appendRejection(
          command,
          RejectionType.INVALID_STATE,
          "Expected an open update '%s' of policy version %d to apply, but none is open"
              .formatted(envelopeId, version));
      return;
    }

    final int chunkCount = assembly.get().getChunkCount();
    final int chunkIndex = assembly.get().getAppliedChunks();
    LOG.debug(
        "Processing chunk {} of {} of policy update '{}'", chunkIndex, chunkCount, envelopeId);
    if (chunkIndex == chunkCount) {
      // All entity commands of the previous pages are queued ahead of this command, so the
      // version switch below happens after they have been processed.
      appendApplied(
          command.getKey(), progress(envelopeId, version, chunkCount - 1, chunkCount, true));
      return;
    }

    final var chunk = policyUpdateState.getChunk(envelopeId, chunkIndex);
    LOG.trace("Content of chunk {} of policy update '{}': {}", chunkIndex, envelopeId, chunk);
    appendEntityCommands(chunk);

    appendApplied(command.getKey(), progress(envelopeId, version, chunkIndex, chunkCount, false));
    appendFollowUpCommand(
        command.getKey(),
        PolicyUpdateIntent.APPLY,
        progress(envelopeId, version, chunkIndex + 1, chunkCount, false));
  }

  private void appendApplied(final long key, final PolicyUpdateRecord progress) {
    LOG.trace(
        "Appending {} of policy update '{}' for chunk {} of {} (last page: {})",
        PolicyUpdateIntent.APPLIED,
        progress.getEnvelopeId(),
        progress.getChunkIndex(),
        progress.getChunkCount(),
        progress.isLastPage());
    stateWriter.appendFollowUpEvent(key, PolicyUpdateIntent.APPLIED, progress);
  }

  private void appendFollowUpCommand(final long key, final Intent intent, final RecordValue value) {
    LOG.trace("Appending command {} with key {}: {}", intent, key, value);
    commandWriter.appendFollowUpCommand(key, intent, value);
  }

  /**
   * Queues one command per entity. The existing command processors apply the change and distribute
   * it to the other partitions. Commands written by the engine are internal, so they skip the
   * permission check.
   */
  private void appendEntityCommands(final PolicyUpdateRecord chunk) {
    final long version = chunk.getPolicyVersion();
    for (final TenantRecordValue tenant : chunk.getTenants()) {
      final var record =
          new TenantRecord()
              .setTenantId(tenant.getTenantId())
              .setName(tenant.getName())
              .setDescription(tenant.getDescription())
              .setPolicyVersion(version);
      final var existing = processingState.getTenantState().getTenantById(tenant.getTenantId());
      if (existing.isPresent()) {
        final long key = existing.get().getTenantKey();
        appendFollowUpCommand(key, TenantIntent.UPDATE, record.setTenantKey(key));
      } else {
        appendFollowUpCommand(NEW_ENTITY_KEY, TenantIntent.CREATE, record);
      }
    }
    for (final RoleRecordValue role : chunk.getRoles()) {
      final var record =
          new RoleRecord()
              .setRoleId(role.getRoleId())
              .setName(role.getName())
              .setDescription(role.getDescription())
              .setPolicyVersion(version);
      final var existing = processingState.getRoleState().getRole(role.getRoleId());
      if (existing.isPresent()) {
        final long key = existing.get().getRoleKey();
        appendFollowUpCommand(key, RoleIntent.UPDATE, record.setRoleKey(key));
      } else {
        appendFollowUpCommand(NEW_ENTITY_KEY, RoleIntent.CREATE, record);
      }
    }
    for (final GroupRecordValue group : chunk.getGroups()) {
      final var record =
          new GroupRecord()
              .setGroupId(group.getGroupId())
              .setName(group.getName())
              .setDescription(group.getDescription())
              .setPolicyVersion(version);
      final var existing = processingState.getGroupState().get(group.getGroupId());
      if (existing.isPresent()) {
        final long key = existing.get().getGroupKey();
        appendFollowUpCommand(key, GroupIntent.UPDATE, record.setGroupKey(key));
      } else {
        appendFollowUpCommand(NEW_ENTITY_KEY, GroupIntent.CREATE, record);
      }
    }
    for (final MappingRuleRecordValue rule : chunk.getMappingRules()) {
      final var record =
          new MappingRuleRecord()
              .setMappingRuleId(rule.getMappingRuleId())
              .setName(rule.getName())
              .setClaimName(rule.getClaimName())
              .setClaimValue(rule.getClaimValue())
              .setPolicyVersion(version);
      final var existing = processingState.getMappingRuleState().get(rule.getMappingRuleId());
      if (existing.isPresent()) {
        final long key = existing.get().getMappingRuleKey();
        appendFollowUpCommand(key, MappingRuleIntent.UPDATE, record.setMappingRuleKey(key));
      } else {
        appendFollowUpCommand(NEW_ENTITY_KEY, MappingRuleIntent.CREATE, record);
      }
    }
    for (final PolicyMembershipEntryValue membership : chunk.getMemberships()) {
      appendMembershipCommand(
          membership, RoleIntent.ADD_ENTITY, GroupIntent.ADD_ENTITY, TenantIntent.ADD_ENTITY);
    }
    for (final AuthorizationRecordValue authorization : chunk.getAuthorizations()) {
      appendAuthorizationUpsert(authorization, version);
    }
    for (final AuthorizationRecordValue authorization : chunk.getAuthorizationDeletes()) {
      appendAuthorizationDelete(authorization);
    }
    for (final PolicyMembershipEntryValue membership : chunk.getMembershipDeletes()) {
      appendMembershipCommand(
          membership,
          RoleIntent.REMOVE_ENTITY,
          GroupIntent.REMOVE_ENTITY,
          TenantIntent.REMOVE_ENTITY);
    }
    for (final PolicyDeleteEntryValue delete : chunk.getDeletes()) {
      appendDelete(delete);
    }
  }

  /**
   * Queues an add or remove of an entity to a role, group or tenant. The target is looked up here,
   * because the processor needs its key. A missing target queues nothing. The existing processors
   * reject a duplicate add and a missing member, and those rejections are harmless for a replay.
   */
  private void appendMembershipCommand(
      final PolicyMembershipEntryValue membership,
      final RoleIntent roleIntent,
      final GroupIntent groupIntent,
      final TenantIntent tenantIntent) {
    final var targetId = membership.getTargetId();
    final var memberId = membership.getMemberId();
    final var memberType = membership.getMemberType();
    switch (membership.getTargetType()) {
      case ROLE ->
          processingState
              .getRoleState()
              .getRole(targetId)
              .ifPresent(
                  target -> {
                    final long key = target.getRoleKey();
                    appendFollowUpCommand(
                        key,
                        roleIntent,
                        new RoleRecord()
                            .setRoleKey(key)
                            .setRoleId(targetId)
                            .setEntityId(memberId)
                            .setEntityType(memberType));
                  });
      case GROUP ->
          processingState
              .getGroupState()
              .get(targetId)
              .ifPresent(
                  target -> {
                    final long key = target.getGroupKey();
                    appendFollowUpCommand(
                        key,
                        groupIntent,
                        new GroupRecord()
                            .setGroupKey(key)
                            .setGroupId(targetId)
                            .setEntityId(memberId)
                            .setEntityType(memberType));
                  });
      case TENANT ->
          processingState
              .getTenantState()
              .getTenantById(targetId)
              .ifPresent(
                  target -> {
                    final long key = target.getTenantKey();
                    appendFollowUpCommand(
                        key,
                        tenantIntent,
                        new TenantRecord()
                            .setTenantKey(key)
                            .setTenantId(targetId)
                            .setEntityId(memberId)
                            .setEntityType(memberType));
                  });
      default ->
          throw new IllegalStateException(
              "Expected a membership target of role, group or tenant, but got %s"
                  .formatted(membership.getTargetType()));
    }
  }

  /** Creates the authorization, or updates the permissions of the one with the same identity. */
  private void appendAuthorizationUpsert(final AuthorizationRecordValue value, final long version) {
    final long existingKey = findAuthorizationKey(value);
    final var record =
        authorizationRecord(value)
            .setPolicyVersion(version)
            .setPermissionTypes(value.getPermissionTypes());
    if (existingKey == NEW_ENTITY_KEY) {
      appendFollowUpCommand(NEW_ENTITY_KEY, AuthorizationIntent.CREATE, record);
    } else {
      record.setAuthorizationKey(existingKey);
      appendFollowUpCommand(existingKey, AuthorizationIntent.UPDATE, record);
    }
  }

  private void appendAuthorizationDelete(final AuthorizationRecordValue value) {
    final long existingKey = findAuthorizationKey(value);
    if (existingKey != NEW_ENTITY_KEY) {
      appendFollowUpCommand(
          existingKey,
          AuthorizationIntent.DELETE,
          authorizationRecord(value).setAuthorizationKey(existingKey));
    }
  }

  /**
   * Returns the key of the stored authorization with the same identity, or {@link #NEW_ENTITY_KEY}
   * if there is none. The state is read by key, so the owner's keys are scanned and each candidate
   * is compared right away, before the shared state buffer is reused.
   */
  private long findAuthorizationKey(final AuthorizationRecordValue value) {
    final var authorizationState = processingState.getAuthorizationState();
    for (final long key :
        authorizationState.getAuthorizationKeysForOwner(value.getOwnerType(), value.getOwnerId())) {
      final var stored = authorizationState.get(key);
      if (stored.isPresent()
          && stored.get().getResourceType() == value.getResourceType()
          && stored.get().getResourceMatcher() == value.getResourceMatcher()
          && stored.get().getResourceId().equals(value.getResourceId())
          && stored.get().getResourcePropertyName().equals(value.getResourcePropertyName())) {
        return key;
      }
    }
    return NEW_ENTITY_KEY;
  }

  private static AuthorizationRecord authorizationRecord(final AuthorizationRecordValue value) {
    return new AuthorizationRecord()
        .setOwnerType(value.getOwnerType())
        .setOwnerId(value.getOwnerId())
        .setResourceType(value.getResourceType())
        .setResourceMatcher(value.getResourceMatcher())
        .setResourceId(value.getResourceId())
        .setResourcePropertyName(value.getResourcePropertyName());
  }

  /** Deleting a missing entity queues nothing, so replays stay safe. */
  private void appendDelete(final PolicyDeleteEntryValue delete) {
    final var id = delete.getId();
    switch (delete.getEntityType()) {
      case TENANT ->
          processingState
              .getTenantState()
              .getTenantById(id)
              .ifPresent(
                  existing ->
                      appendFollowUpCommand(
                          existing.getTenantKey(),
                          TenantIntent.DELETE,
                          new TenantRecord()
                              .setTenantKey(existing.getTenantKey())
                              .setTenantId(id)));
      case ROLE ->
          processingState
              .getRoleState()
              .getRole(id)
              .ifPresent(
                  existing ->
                      appendFollowUpCommand(
                          existing.getRoleKey(),
                          RoleIntent.DELETE,
                          new RoleRecord().setRoleKey(existing.getRoleKey()).setRoleId(id)));
      case GROUP ->
          processingState
              .getGroupState()
              .get(id)
              .ifPresent(
                  existing ->
                      appendFollowUpCommand(
                          existing.getGroupKey(),
                          GroupIntent.DELETE,
                          new GroupRecord().setGroupKey(existing.getGroupKey()).setGroupId(id)));
      case MAPPING_RULE ->
          processingState
              .getMappingRuleState()
              .get(id)
              .ifPresent(
                  existing ->
                      appendFollowUpCommand(
                          existing.getMappingRuleKey(),
                          MappingRuleIntent.DELETE,
                          new MappingRuleRecord()
                              .setMappingRuleKey(existing.getMappingRuleKey())
                              .setMappingRuleId(id)));
      default ->
          throw new IllegalStateException(
              "Expected a delete of a tenant, role, group or mapping rule, but got %s"
                  .formatted(delete.getEntityType()));
    }
  }

  private static PolicyUpdateRecord progress(
      final String envelopeId,
      final long version,
      final int chunkIndex,
      final int chunkCount,
      final boolean lastPage) {
    return new PolicyUpdateRecord()
        .setEnvelopeId(envelopeId)
        .setPolicyVersion(version)
        .setChunkIndex(chunkIndex)
        .setChunkCount(chunkCount)
        .setLastPage(lastPage);
  }
}
