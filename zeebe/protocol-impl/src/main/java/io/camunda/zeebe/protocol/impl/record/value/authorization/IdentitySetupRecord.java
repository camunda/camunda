/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.authorization;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.IdentitySetupRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class IdentitySetupRecord extends MsgpackBufferValue<IdentitySetupRecord>
    implements MsgpackRecordValue, IdentitySetupRecordValue {

  final List<RoleRecord> roles = new ArrayList<>();
  final List<RoleRecord> roleMembers = new ArrayList<>();
  final List<UserRecord> users = new ArrayList<>();
  final TenantRecord defaultTenant = new TenantRecord();
  final List<TenantRecord> tenants = new ArrayList<>();
  final List<TenantRecord> tenantMembers = new ArrayList<>();
  final List<MappingRuleRecord> mappingRules = new ArrayList<>();
  final List<AuthorizationRecord> authorizations = new ArrayList<>();
  final List<GroupRecord> groups = new ArrayList<>();
  final List<GroupRecord> groupMembers = new ArrayList<>();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public Collection<RoleRecordValue> getRoles() {
    return roles.stream().map(RoleRecordValue.class::cast).collect(Collectors.toList());
  }

  @Override
  public Collection<RoleRecordValue> getRoleMembers() {
    return roleMembers.stream().map(RoleRecordValue.class::cast).collect(Collectors.toList());
  }

  @Override
  public List<UserRecordValue> getUsers() {
    return users.stream().map(UserRecordValue.class::cast).toList();
  }

  @Override
  public TenantRecord getDefaultTenant() {
    return defaultTenant;
  }

  @Override
  public List<TenantRecordValue> getTenants() {
    return tenants.stream().map(TenantRecordValue.class::cast).toList();
  }

  @Override
  public Collection<TenantRecordValue> getTenantMembers() {
    return tenantMembers.stream().map(TenantRecordValue.class::cast).collect(Collectors.toList());
  }

  @Override
  public List<MappingRuleRecordValue> getMappingRules() {
    return mappingRules.stream().map(MappingRuleRecordValue.class::cast).toList();
  }

  @Override
  public Collection<AuthorizationRecordValue> getAuthorizations() {
    return authorizations.stream()
        .map(AuthorizationRecordValue.class::cast)
        .collect(Collectors.toList());
  }

  @Override
  public Collection<GroupRecordValue> getGroups() {
    return groups.stream().map(GroupRecordValue.class::cast).collect(Collectors.toList());
  }

  @Override
  public Collection<GroupRecordValue> getGroupMembers() {
    return groupMembers.stream().map(GroupRecordValue.class::cast).collect(Collectors.toList());
  }

  public IdentitySetupRecord setDefaultTenant(final TenantRecord tenant) {
    defaultTenant.copyFrom(tenant);
    return this;
  }

  public IdentitySetupRecord addTenant(final TenantRecord tenant) {
    final var copy = new TenantRecord();
    copy.copyFrom(tenant);
    tenants.add(copy);
    return this;
  }

  public IdentitySetupRecord addTenantMember(final TenantRecord tenant) {
    final var copy = new TenantRecord();
    copy.copyFrom(tenant);
    tenantMembers.add(copy);
    return this;
  }

  public IdentitySetupRecord addRole(final RoleRecord role) {
    final var copy = new RoleRecord();
    copy.copyFrom(role);
    roles.add(copy);
    return this;
  }

  public IdentitySetupRecord addRoleMember(final RoleRecord role) {
    final var copy = new RoleRecord();
    copy.copyFrom(role);
    roleMembers.add(copy);
    return this;
  }

  public IdentitySetupRecord addUser(final UserRecord user) {
    final var copy = new UserRecord();
    copy.copyFrom(user);
    users.add(copy);
    return this;
  }

  public IdentitySetupRecord addMappingRule(final MappingRuleRecord mappingRule) {
    final var copy = new MappingRuleRecord();
    copy.copyFrom(mappingRule);
    mappingRules.add(copy);
    return this;
  }

  public IdentitySetupRecord addAuthorization(final AuthorizationRecord authorization) {
    final var copy = new AuthorizationRecord();
    copy.copyFrom(authorization);
    authorizations.add(copy);
    return this;
  }

  public IdentitySetupRecord addGroup(final GroupRecord group) {
    final var copy = new GroupRecord();
    copy.copyFrom(group);
    groups.add(copy);
    return this;
  }

  public IdentitySetupRecord addGroupMember(final GroupRecord groupMember) {
    final var copy = new GroupRecord();
    copy.copyFrom(groupMember);
    groupMembers.add(copy);
    return this;
  }

  @Override
  protected MsgpackCodec<IdentitySetupRecord> codec() {
    return IdentitySetupRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<IdentitySetupRecord> ops() {
    return IdentitySetupRecordOps.INSTANCE;
  }
}
