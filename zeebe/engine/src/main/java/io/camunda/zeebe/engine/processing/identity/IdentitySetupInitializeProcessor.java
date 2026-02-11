/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.authorization.IdentitySetupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.Collection;
import java.util.List;

@ExcludeAuthorizationCheck
public final class IdentitySetupInitializeProcessor
    implements TypedRecordProcessor<IdentitySetupRecord> {
  private final KeyGenerator keyGenerator;
  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;

  public IdentitySetupInitializeProcessor(final Writers writers, final KeyGenerator keyGenerator) {
    this.keyGenerator = keyGenerator;
    commandWriter = writers.command();
    stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<IdentitySetupRecord> command) {
    final var initializationKey = keyGenerator.nextKey();
    final var setupRecord = command.getValue();

    createDefaultTenant(initializationKey, setupRecord.getDefaultTenant());
    createTenants(initializationKey, setupRecord.getTenants());
    createRoles(initializationKey, setupRecord.getRoles());
    createGroups(initializationKey, setupRecord.getGroups());
    createUsers(initializationKey, setupRecord.getUsers());
    createMappingRules(initializationKey, setupRecord.getMappingRules());
    createAuthorizations(initializationKey, setupRecord.getAuthorizations());
    createTenantMembers(initializationKey, setupRecord.getTenantMembers());
    createRoleMembers(initializationKey, setupRecord.getRoleMembers());
    createGroupMembers(initializationKey, setupRecord.getGroupMembers());

    stateWriter.appendFollowUpEvent(
        initializationKey, IdentitySetupIntent.INITIALIZED, setupRecord);
  }

  @Override
  public boolean shouldProcessResultsInSeparateBatches() {
    return true;
  }

  private void createDefaultTenant(final long key, final TenantRecord defaultTenant) {
    commandWriter.appendFollowUpCommand(key, TenantIntent.CREATE, defaultTenant);
  }

  private void createTenants(final long key, final List<TenantRecordValue> tenants) {
    tenants.forEach(
        tenant -> commandWriter.appendFollowUpCommand(key, TenantIntent.CREATE, tenant));
  }

  private void createUsers(final long key, final List<UserRecordValue> users) {
    users.forEach(user -> commandWriter.appendFollowUpCommand(key, UserIntent.CREATE, user));
  }

  private void createMappingRules(final long key, final List<MappingRuleRecordValue> mappingRules) {
    mappingRules.forEach(
        mappingRule ->
            commandWriter.appendFollowUpCommand(key, MappingRuleIntent.CREATE, mappingRule));
  }

  private void createRoles(final long key, final Collection<RoleRecordValue> roles) {
    roles.forEach(role -> commandWriter.appendFollowUpCommand(key, RoleIntent.CREATE, role));
  }

  private void createRoleMembers(final long key, final Collection<RoleRecordValue> roleMembers) {
    roleMembers.forEach(
        roleMember -> commandWriter.appendFollowUpCommand(key, RoleIntent.ADD_ENTITY, roleMember));
  }

  private void createTenantMembers(
      final long key, final Collection<TenantRecordValue> tenantMembers) {
    tenantMembers.forEach(
        tenantMember ->
            commandWriter.appendFollowUpCommand(key, TenantIntent.ADD_ENTITY, tenantMember));
  }

  private void createAuthorizations(
      final long key, final Collection<AuthorizationRecordValue> authorizations) {
    authorizations.forEach(
        authorization ->
            commandWriter.appendFollowUpCommand(key, AuthorizationIntent.CREATE, authorization));
  }

  private void createGroups(final long key, final Collection<GroupRecordValue> groups) {
    groups.forEach(group -> commandWriter.appendFollowUpCommand(key, GroupIntent.CREATE, group));
  }

  private void createGroupMembers(final long key, final Collection<GroupRecordValue> groupMembers) {
    groupMembers.forEach(
        groupMember ->
            commandWriter.appendFollowUpCommand(key, GroupIntent.ADD_ENTITY, groupMember));
  }
}
