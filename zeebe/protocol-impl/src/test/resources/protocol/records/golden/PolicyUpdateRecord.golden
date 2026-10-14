/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.policyupdate;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.PolicyDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateKind;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One chunk of a policy update, or the progress of applying it. Each section is an array property
 * that holds the entries of that section; the sections are the upserts and deletions of the Hub
 * change set, in the apply order defined by the change set contract.
 */
public final class PolicyUpdateRecord extends UnifiedRecordValue
    implements PolicyUpdateRecordValue {

  private final StringProperty envelopeIdProp = new StringProperty("envelopeId", "");
  private final EnumProperty<PolicyUpdateKind> kindProp =
      new EnumProperty<>("kind", PolicyUpdateKind.class, PolicyUpdateKind.UNSPECIFIED);
  private final LongProperty policyVersionProp = new LongProperty("policyVersion", 0);
  private final IntegerProperty chunkIndexProp = new IntegerProperty("chunkIndex", 0);
  private final IntegerProperty chunkCountProp = new IntegerProperty("chunkCount", 0);

  private final ArrayProperty<TenantRecord> tenantsProp =
      new ArrayProperty<>("tenants", TenantRecord::new);
  private final ArrayProperty<RoleRecord> rolesProp = new ArrayProperty<>("roles", RoleRecord::new);
  private final ArrayProperty<GroupRecord> groupsProp =
      new ArrayProperty<>("groups", GroupRecord::new);
  private final ArrayProperty<MappingRuleRecord> mappingRulesProp =
      new ArrayProperty<>("mappingRules", MappingRuleRecord::new);
  private final ArrayProperty<PolicyMembershipEntry> membershipsProp =
      new ArrayProperty<>("memberships", PolicyMembershipEntry::new);
  private final ArrayProperty<AuthorizationRecord> authorizationsProp =
      new ArrayProperty<>("authorizations", AuthorizationRecord::new);
  private final ArrayProperty<PolicyDeleteEntry> deletesProp =
      new ArrayProperty<>("deletes", PolicyDeleteEntry::new);
  private final ArrayProperty<PolicyMembershipEntry> membershipDeletesProp =
      new ArrayProperty<>("membershipDeletes", PolicyMembershipEntry::new);
  private final ArrayProperty<AuthorizationRecord> authorizationDeletesProp =
      new ArrayProperty<>("authorizationDeletes", AuthorizationRecord::new);

  private final BooleanProperty lastPageProp = new BooleanProperty("lastPage", false);

  public PolicyUpdateRecord() {
    super(15);
    declareProperty(envelopeIdProp)
        .declareProperty(kindProp)
        .declareProperty(policyVersionProp)
        .declareProperty(chunkIndexProp)
        .declareProperty(chunkCountProp)
        .declareProperty(tenantsProp)
        .declareProperty(rolesProp)
        .declareProperty(groupsProp)
        .declareProperty(mappingRulesProp)
        .declareProperty(membershipsProp)
        .declareProperty(authorizationsProp)
        .declareProperty(deletesProp)
        .declareProperty(membershipDeletesProp)
        .declareProperty(authorizationDeletesProp)
        .declareProperty(lastPageProp);
  }

  @Override
  public String getEnvelopeId() {
    return BufferUtil.bufferAsString(envelopeIdProp.getValue());
  }

  public PolicyUpdateRecord setEnvelopeId(final String envelopeId) {
    envelopeIdProp.setValue(envelopeId);
    return this;
  }

  @Override
  public PolicyUpdateKind getKind() {
    return kindProp.getValue();
  }

  public PolicyUpdateRecord setKind(final PolicyUpdateKind kind) {
    kindProp.setValue(kind);
    return this;
  }

  @Override
  public long getPolicyVersion() {
    return policyVersionProp.getValue();
  }

  public PolicyUpdateRecord setPolicyVersion(final long policyVersion) {
    policyVersionProp.setValue(policyVersion);
    return this;
  }

  @Override
  public int getChunkIndex() {
    return chunkIndexProp.getValue();
  }

  public PolicyUpdateRecord setChunkIndex(final int chunkIndex) {
    chunkIndexProp.setValue(chunkIndex);
    return this;
  }

  @Override
  public int getChunkCount() {
    return chunkCountProp.getValue();
  }

  public PolicyUpdateRecord setChunkCount(final int chunkCount) {
    chunkCountProp.setValue(chunkCount);
    return this;
  }

  @Override
  public List<TenantRecordValue> getTenants() {
    return tenantsProp.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecord setTenants(final Collection<TenantRecordValue> tenants) {
    tenantsProp.reset();
    tenants.forEach(tenant -> copyTenant(tenantsProp.add(), tenant));
    return this;
  }

  @Override
  public List<RoleRecordValue> getRoles() {
    return rolesProp.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecord setRoles(final Collection<RoleRecordValue> roles) {
    rolesProp.reset();
    roles.forEach(role -> copyRole(rolesProp.add(), role));
    return this;
  }

  @Override
  public List<GroupRecordValue> getGroups() {
    return groupsProp.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecord setGroups(final Collection<GroupRecordValue> groups) {
    groupsProp.reset();
    groups.forEach(group -> copyGroup(groupsProp.add(), group));
    return this;
  }

  @Override
  public List<MappingRuleRecordValue> getMappingRules() {
    return mappingRulesProp.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecord setMappingRules(final Collection<MappingRuleRecordValue> mappingRules) {
    mappingRulesProp.reset();
    mappingRules.forEach(rule -> copyMappingRule(mappingRulesProp.add(), rule));
    return this;
  }

  @Override
  public List<PolicyMembershipEntryValue> getMemberships() {
    return membershipsProp.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecord setMemberships(final Collection<PolicyMembershipEntryValue> entries) {
    membershipsProp.reset();
    entries.forEach(entry -> copyMembership(membershipsProp.add(), entry));
    return this;
  }

  @Override
  public List<AuthorizationRecordValue> getAuthorizations() {
    return authorizationsProp.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecord setAuthorizations(
      final Collection<AuthorizationRecordValue> authorizations) {
    authorizationsProp.reset();
    authorizations.forEach(auth -> copyAuthorization(authorizationsProp.add(), auth));
    return this;
  }

  @Override
  public List<PolicyDeleteEntryValue> getDeletes() {
    return deletesProp.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecord setDeletes(final Collection<PolicyDeleteEntryValue> deletes) {
    deletesProp.reset();
    deletes.forEach(
        delete -> deletesProp.add().setEntityType(delete.getEntityType()).setId(delete.getId()));
    return this;
  }

  @Override
  public List<PolicyMembershipEntryValue> getMembershipDeletes() {
    return membershipDeletesProp.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecord setMembershipDeletes(
      final Collection<PolicyMembershipEntryValue> entries) {
    membershipDeletesProp.reset();
    entries.forEach(entry -> copyMembership(membershipDeletesProp.add(), entry));
    return this;
  }

  @Override
  public List<AuthorizationRecordValue> getAuthorizationDeletes() {
    return authorizationDeletesProp.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecord setAuthorizationDeletes(
      final Collection<AuthorizationRecordValue> authorizations) {
    authorizationDeletesProp.reset();
    authorizations.forEach(auth -> copyAuthorization(authorizationDeletesProp.add(), auth));
    return this;
  }

  @Override
  public boolean isLastPage() {
    return lastPageProp.getValue();
  }

  public PolicyUpdateRecord setLastPage(final boolean lastPage) {
    lastPageProp.setValue(lastPage);
    return this;
  }

  private static void copyTenant(final TenantRecord target, final TenantRecordValue source) {
    target
        .setTenantKey(source.getTenantKey())
        .setTenantId(source.getTenantId())
        .setName(source.getName())
        .setDescription(source.getDescription())
        .setEntityId(source.getEntityId())
        .setEntityType(source.getEntityType())
        .setPolicyVersion(source.getPolicyVersion());
  }

  private static void copyRole(final RoleRecord target, final RoleRecordValue source) {
    target
        .setRoleKey(source.getRoleKey())
        .setRoleId(source.getRoleId())
        .setName(source.getName())
        .setDescription(source.getDescription())
        .setEntityId(source.getEntityId())
        .setEntityType(source.getEntityType())
        .setPolicyVersion(source.getPolicyVersion());
  }

  private static void copyGroup(final GroupRecord target, final GroupRecordValue source) {
    target
        .setGroupKey(source.getGroupKey())
        .setGroupId(source.getGroupId())
        .setName(source.getName())
        .setDescription(source.getDescription())
        .setEntityId(source.getEntityId())
        .setEntityType(source.getEntityType())
        .setPolicyVersion(source.getPolicyVersion());
  }

  private static void copyMappingRule(
      final MappingRuleRecord target, final MappingRuleRecordValue source) {
    target
        .setMappingRuleKey(source.getMappingRuleKey())
        .setMappingRuleId(source.getMappingRuleId())
        .setName(source.getName())
        .setClaimName(source.getClaimName())
        .setClaimValue(source.getClaimValue())
        .setPolicyVersion(source.getPolicyVersion());
  }

  private static void copyMembership(
      final PolicyMembershipEntry target, final PolicyMembershipEntryValue source) {
    target
        .setTargetType(source.getTargetType())
        .setTargetId(source.getTargetId())
        .setMemberType(source.getMemberType())
        .setMemberId(source.getMemberId());
  }

  private static void copyAuthorization(
      final AuthorizationRecord target, final AuthorizationRecordValue source) {
    target
        .setAuthorizationKey(source.getAuthorizationKey())
        .setOwnerId(source.getOwnerId())
        .setOwnerType(source.getOwnerType())
        .setResourceMatcher(source.getResourceMatcher())
        .setResourceId(source.getResourceId())
        .setResourcePropertyName(source.getResourcePropertyName())
        .setResourceType(source.getResourceType())
        .setPermissionTypes(source.getPermissionTypes())
        .setPolicyVersion(source.getPolicyVersion());
  }
}
