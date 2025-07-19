/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.IdentitySetupRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.value.IdentitySetupRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.Function;

public final class IdentitySetupClient {

  private final CommandWriter writer;

  public IdentitySetupClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public IdentitySetupInitializeClient initialize() {
    return new IdentitySetupInitializeClient(writer);
  }

  public static class IdentitySetupInitializeClient {
    private static final Function<Long, Record<IdentitySetupRecordValue>> SUCCESS_SUPPLIER =
        (position) ->
            RecordingExporter.identitySetupRecords()
                .withIntent(IdentitySetupIntent.INITIALIZED)
                .withSourceRecordPosition(position)
                .getFirst();
    private static final Function<Long, Record<IdentitySetupRecordValue>> REJECTION_SUPPLIER =
        (position) ->
            RecordingExporter.identitySetupRecords()
                .onlyCommandRejections()
                .withIntent(IdentitySetupIntent.INITIALIZE)
                .withSourceRecordPosition(position)
                .getFirst();

    private final CommandWriter writer;
    private final IdentitySetupRecord record;
    private final RoleRecord defaultRole =
        new RoleRecord().setRoleId(Strings.newRandomValidIdentityId());
    private final TenantRecord defaultTenant =
        new TenantRecord().setTenantId(Strings.newRandomValidIdentityId());
    private Function<Long, Record<IdentitySetupRecordValue>> expectation = SUCCESS_SUPPLIER;

    public IdentitySetupInitializeClient(final CommandWriter writer) {
      this.writer = writer;
      record = new IdentitySetupRecord();
      record.addRole(defaultRole);
      record.setDefaultTenant(defaultTenant);
    }

    public IdentitySetupInitializeClient withRole(final RoleRecord role) {
      record.addRole(role);
      return this;
    }

    public IdentitySetupInitializeClient withRoleMember(final RoleRecord role) {
      record.addRoleMember(role);
      return this;
    }

    public IdentitySetupInitializeClient withUser(final UserRecord user) {
      record.addUser(user);
      return this;
    }

    public IdentitySetupInitializeClient withTenant(final TenantRecord tenant) {
      record.setDefaultTenant(tenant);
      return this;
    }

    public IdentitySetupInitializeClient withTenantMember(final TenantRecord tenant) {
      record.addTenantMember(tenant);
      return this;
    }

    public IdentitySetupInitializeClient withMapping(final MappingRuleRecord mapping) {
      record.addMappingRule(mapping);
      return this;
    }

    public IdentitySetupInitializeClient withAuthorization(
        final AuthorizationRecord authorization) {
      record.addAuthorization(authorization);
      return this;
    }

    public IdentitySetupInitializeClient expectRejection() {
      expectation = REJECTION_SUPPLIER;
      return this;
    }

    public Record<IdentitySetupRecordValue> initialize() {
      final long position = writer.writeCommand(IdentitySetupIntent.INITIALIZE, record);
      return expectation.apply(position);
    }
  }
}
