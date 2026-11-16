/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.policyupdate;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationRecordValue;
import io.camunda.zeebe.protocol.record.value.GroupRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.PolicyAuthorizationDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyMembershipEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * A policy update. On an {@code APPEND_CHUNK} command it carries one chunk: each section is an
 * array property that holds the entries of that section, and the sections are the upserts and
 * deletions of the Hub change set, in the apply order defined by the change set contract. The other
 * intents leave the sections empty and carry only the update's identifiers.
 */
@MsgpackPojo
@NullMarked
public final class PolicyUpdateRecord extends MsgpackBufferValue<PolicyUpdateRecord>
    implements MsgpackRecordValue, PolicyUpdateRecordValue {

  DirectBuffer policyId = Decoding.EMPTY;
  long policyVersion = 0L;
  long basePolicyVersion = 0L;
  long seqNo = 0L;
  DirectBuffer updateId = Decoding.EMPTY;
  int chunkIndex = -1;
  int chunkCount = -1;

  final List<TenantRecord> tenants = new ArrayList<>();
  final List<RoleRecord> roles = new ArrayList<>();
  final List<GroupRecord> groups = new ArrayList<>();
  final List<MappingRuleRecord> mappingRules = new ArrayList<>();
  final List<PolicyMembershipEntry> memberships = new ArrayList<>();
  final List<AuthorizationRecord> authorizations = new ArrayList<>();
  final List<PolicyDeleteEntry> deletes = new ArrayList<>();
  final List<PolicyMembershipEntry> membershipDeletes = new ArrayList<>();
  final List<PolicyAuthorizationDeleteEntry> authorizationDeletes = new ArrayList<>();

  DirectBuffer errorMessage = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getPolicyId() {
    return BufferUtil.bufferAsString(policyId);
  }

  public PolicyUpdateRecord setPolicyId(final String policyId) {
    this.policyId = BufferUtil.wrapString(policyId);
    return this;
  }

  @Override
  public long getPolicyVersion() {
    return policyVersion;
  }

  public PolicyUpdateRecord setPolicyVersion(final long policyVersion) {
    this.policyVersion = policyVersion;
    return this;
  }

  @Override
  public long getBasePolicyVersion() {
    return basePolicyVersion;
  }

  public PolicyUpdateRecord setBasePolicyVersion(final long basePolicyVersion) {
    this.basePolicyVersion = basePolicyVersion;
    return this;
  }

  @Override
  public long getSeqNo() {
    return seqNo;
  }

  public PolicyUpdateRecord setSeqNo(final long seqNo) {
    this.seqNo = seqNo;
    return this;
  }

  @Override
  public String getUpdateId() {
    return BufferUtil.bufferAsString(updateId);
  }

  public PolicyUpdateRecord setUpdateId(final String updateId) {
    this.updateId = BufferUtil.wrapString(updateId);
    return this;
  }

  @Override
  public int getChunkIndex() {
    return chunkIndex;
  }

  public PolicyUpdateRecord setChunkIndex(final int chunkIndex) {
    this.chunkIndex = chunkIndex;
    return this;
  }

  @Override
  public int getChunkCount() {
    return chunkCount;
  }

  public PolicyUpdateRecord setChunkCount(final int chunkCount) {
    this.chunkCount = chunkCount;
    return this;
  }

  @Override
  public List<TenantRecordValue> getTenants() {
    return tenants.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecord setTenants(final Collection<TenantRecordValue> tenants) {
    this.tenants.clear();
    tenants.forEach(tenant -> this.tenants.add(copyTenant(tenant)));
    return this;
  }

  @Override
  public List<RoleRecordValue> getRoles() {
    return roles.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecord setRoles(final Collection<RoleRecordValue> roles) {
    this.roles.clear();
    roles.forEach(role -> this.roles.add(copyRole(role)));
    return this;
  }

  @Override
  public List<GroupRecordValue> getGroups() {
    return groups.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecord setGroups(final Collection<GroupRecordValue> groups) {
    this.groups.clear();
    groups.forEach(group -> this.groups.add(copyGroup(group)));
    return this;
  }

  @Override
  public List<MappingRuleRecordValue> getMappingRules() {
    return mappingRules.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecord setMappingRules(final Collection<MappingRuleRecordValue> mappingRules) {
    this.mappingRules.clear();
    mappingRules.forEach(rule -> this.mappingRules.add(copyMappingRule(rule)));
    return this;
  }

  @Override
  public List<PolicyMembershipEntryValue> getMemberships() {
    return memberships.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecord setMemberships(final Collection<PolicyMembershipEntryValue> entries) {
    memberships.clear();
    entries.forEach(entry -> memberships.add(copyMembership(entry)));
    return this;
  }

  @Override
  public List<AuthorizationRecordValue> getAuthorizations() {
    return authorizations.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecord setAuthorizations(
      final Collection<AuthorizationRecordValue> authorizations) {
    this.authorizations.clear();
    authorizations.forEach(auth -> this.authorizations.add(copyAuthorization(auth)));
    return this;
  }

  @Override
  public List<PolicyDeleteEntryValue> getDeletes() {
    return deletes.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecord setDeletes(final Collection<PolicyDeleteEntryValue> deletes) {
    this.deletes.clear();
    deletes.forEach(
        delete ->
            this.deletes.add(
                new PolicyDeleteEntry()
                    .setEntityType(delete.getEntityType())
                    .setId(delete.getId())));
    return this;
  }

  @Override
  public List<PolicyMembershipEntryValue> getMembershipDeletes() {
    return membershipDeletes.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecord setMembershipDeletes(
      final Collection<PolicyMembershipEntryValue> entries) {
    membershipDeletes.clear();
    entries.forEach(entry -> membershipDeletes.add(copyMembership(entry)));
    return this;
  }

  @Override
  public List<PolicyAuthorizationDeleteEntryValue> getAuthorizationDeletes() {
    return authorizationDeletes.stream().collect(Collectors.toList());
  }

  public PolicyUpdateRecord setAuthorizationDeletes(
      final Collection<PolicyAuthorizationDeleteEntryValue> deletes) {
    authorizationDeletes.clear();
    deletes.forEach(delete -> authorizationDeletes.add(copyAuthorizationDelete(delete)));
    return this;
  }

  @Override
  public String getErrorMessage() {
    return BufferUtil.bufferAsString(errorMessage);
  }

  public PolicyUpdateRecord setErrorMessage(final String errorMessage) {
    this.errorMessage = BufferUtil.wrapString(errorMessage);
    return this;
  }

  private static TenantRecord copyTenant(final TenantRecordValue source) {
    return new TenantRecord()
        .setTenantKey(source.getTenantKey())
        .setTenantId(source.getTenantId())
        .setName(source.getName())
        .setDescription(source.getDescription())
        .setEntityId(source.getEntityId())
        .setEntityType(source.getEntityType());
  }

  private static RoleRecord copyRole(final RoleRecordValue source) {
    return new RoleRecord()
        .setRoleKey(source.getRoleKey())
        .setRoleId(source.getRoleId())
        .setName(source.getName())
        .setDescription(source.getDescription())
        .setEntityId(source.getEntityId())
        .setEntityType(source.getEntityType());
  }

  private static GroupRecord copyGroup(final GroupRecordValue source) {
    return new GroupRecord()
        .setGroupKey(source.getGroupKey())
        .setGroupId(source.getGroupId())
        .setName(source.getName())
        .setDescription(source.getDescription())
        .setEntityId(source.getEntityId())
        .setEntityType(source.getEntityType());
  }

  private static MappingRuleRecord copyMappingRule(final MappingRuleRecordValue source) {
    return new MappingRuleRecord()
        .setMappingRuleKey(source.getMappingRuleKey())
        .setMappingRuleId(source.getMappingRuleId())
        .setName(source.getName())
        .setClaimName(source.getClaimName())
        .setClaimValue(source.getClaimValue());
  }

  private static PolicyMembershipEntry copyMembership(final PolicyMembershipEntryValue source) {
    return new PolicyMembershipEntry()
        .setTargetType(source.getTargetType())
        .setTargetId(source.getTargetId())
        .setMemberType(source.getMemberType())
        .setMemberId(source.getMemberId());
  }

  private static PolicyAuthorizationDeleteEntry copyAuthorizationDelete(
      final PolicyAuthorizationDeleteEntryValue source) {
    return new PolicyAuthorizationDeleteEntry()
        .setOwnerType(source.getOwnerType())
        .setOwnerId(source.getOwnerId())
        .setResourceType(source.getResourceType())
        .setResourceMatcher(source.getResourceMatcher())
        .setResourceId(source.getResourceId())
        .setResourcePropertyName(source.getResourcePropertyName());
  }

  private static AuthorizationRecord copyAuthorization(final AuthorizationRecordValue source) {
    return new AuthorizationRecord()
        .setAuthorizationKey(source.getAuthorizationKey())
        .setOwnerId(source.getOwnerId())
        .setOwnerType(source.getOwnerType())
        .setResourceMatcher(source.getResourceMatcher())
        .setResourceId(source.getResourceId())
        .setResourcePropertyName(source.getResourcePropertyName())
        .setResourceType(source.getResourceType())
        .setPermissionTypes(source.getPermissionTypes());
  }

  @Override
  protected MsgpackCodec<PolicyUpdateRecord> codec() {
    return PolicyUpdateRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<PolicyUpdateRecord> ops() {
    return PolicyUpdateRecordOps.INSTANCE;
  }
}
