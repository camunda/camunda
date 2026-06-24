/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.authorization;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
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
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class IdentitySetupRecord extends UnifiedRecordValue implements IdentitySetupRecordValue {

  private final ArrayProperty<RoleRecord> rolesProp = new ArrayProperty<>("roles", RoleRecord::new);
  private final ArrayProperty<RoleRecord> roleMembersProp =
      new ArrayProperty<>("roleMembers", RoleRecord::new);
  private final ArrayProperty<UserRecord> usersProp = new ArrayProperty<>("users", UserRecord::new);
  private final ObjectProperty<TenantRecord> defaultTenantProp =
      new ObjectProperty<>("defaultTenant", new TenantRecord());
  private final ArrayProperty<TenantRecord> tenantsProp =
      new ArrayProperty<>("tenants", TenantRecord::new);
  private final ArrayProperty<TenantRecord> tenantMembersProp =
      new ArrayProperty<>("tenantMembers", TenantRecord::new);
  private final ArrayProperty<MappingRuleRecord> mappingRulesProp =
      new ArrayProperty<>("mappingRules", MappingRuleRecord::new);
  private final ArrayProperty<AuthorizationRecord> authorizationsProp =
      new ArrayProperty<>("authorizations", AuthorizationRecord::new);
  private final ArrayProperty<GroupRecord> groupsProp =
      new ArrayProperty<>("groups", GroupRecord::new);
  private final ArrayProperty<GroupRecord> groupMembersProp =
      new ArrayProperty<>("groupMembers", GroupRecord::new);

  public IdentitySetupRecord() {
    super(10);
    declareProperty(rolesProp)
        .declareProperty(roleMembersProp)
        .declareProperty(usersProp)
        .declareProperty(defaultTenantProp)
        .declareProperty(tenantsProp)
        .declareProperty(tenantMembersProp)
        .declareProperty(mappingRulesProp)
        .declareProperty(authorizationsProp)
        .declareProperty(groupsProp)
        .declareProperty(groupMembersProp);
  }

  @Override
  public Collection<RoleRecordValue> getRoles() {
    return rolesProp.stream().map(RoleRecordValue.class::cast).collect(Collectors.toList());
  }

  @Override
  public Collection<RoleRecordValue> getRoleMembers() {
    return roleMembersProp.stream().map(RoleRecordValue.class::cast).collect(Collectors.toList());
  }

  @Override
  public List<UserRecordValue> getUsers() {
    return usersProp.stream().map(UserRecordValue.class::cast).toList();
  }

  @Override
  public TenantRecord getDefaultTenant() {
    return defaultTenantProp.getValue();
  }

  @Override
  public List<TenantRecordValue> getTenants() {
    return tenantsProp.stream().map(TenantRecordValue.class::cast).toList();
  }

  @Override
  public Collection<TenantRecordValue> getTenantMembers() {
    return tenantMembersProp.stream()
        .map(TenantRecordValue.class::cast)
        .collect(Collectors.toList());
  }

  @Override
  public List<MappingRuleRecordValue> getMappingRules() {
    return mappingRulesProp.stream().map(MappingRuleRecordValue.class::cast).toList();
  }

  @Override
  public Collection<AuthorizationRecordValue> getAuthorizations() {
    return authorizationsProp.stream()
        .map(AuthorizationRecordValue.class::cast)
        .collect(Collectors.toList());
  }

  @Override
  public Collection<GroupRecordValue> getGroups() {
    return groupsProp.stream().map(GroupRecordValue.class::cast).collect(Collectors.toList());
  }

  @Override
  public Collection<GroupRecordValue> getGroupMembers() {
    return groupMembersProp.stream().map(GroupRecordValue.class::cast).collect(Collectors.toList());
  }

  public IdentitySetupRecord setDefaultTenant(final TenantRecord tenant) {
    defaultTenantProp.getValue().copyFrom(tenant);
    return this;
  }

  public IdentitySetupRecord addTenant(final TenantRecord tenant) {
    tenantsProp.add().copyFrom(tenant);
    return this;
  }

  public IdentitySetupRecord addTenantMember(final TenantRecord tenant) {
    tenantMembersProp.add().copyFrom(tenant);
    return this;
  }

  public IdentitySetupRecord addRole(final RoleRecord role) {
    rolesProp.add().copyFrom(role);
    return this;
  }

  public IdentitySetupRecord addRoleMember(final RoleRecord role) {
    roleMembersProp.add().copyFrom(role);
    return this;
  }

  public IdentitySetupRecord addUser(final UserRecord user) {
    usersProp.add().copyFrom(user);
    return this;
  }

  public IdentitySetupRecord addMappingRule(final MappingRuleRecord mappingRule) {
    mappingRulesProp.add().copyFrom(mappingRule);
    return this;
  }

  public IdentitySetupRecord addAuthorization(final AuthorizationRecord authorization) {
    authorizationsProp.add().copyFrom(authorization);
    return this;
  }

  public IdentitySetupRecord addGroup(final GroupRecord group) {
    groupsProp.add().copyFrom(group);
    return this;
  }

  public IdentitySetupRecord addGroupMember(final GroupRecord groupMember) {
    groupMembersProp.add().copyFrom(groupMember);
    return this;
  }
}
