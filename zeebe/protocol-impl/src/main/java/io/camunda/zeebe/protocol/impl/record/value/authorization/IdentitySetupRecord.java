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
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.impl.record.value.user.UserRecord;
import io.camunda.zeebe.protocol.record.value.IdentitySetupRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import java.util.List;

public class IdentitySetupRecord extends UnifiedRecordValue implements IdentitySetupRecordValue {

  private final ObjectProperty<RoleRecord> defaultRoleProp =
      new ObjectProperty<>("defaultRole", new RoleRecord());
  private final ArrayProperty<UserRecord> usersProp = new ArrayProperty<>("users", UserRecord::new);
  private final ObjectProperty<TenantRecord> defaultTenantProp =
      new ObjectProperty<>("defaultTenant", new TenantRecord());
  private final ArrayProperty<MappingRecord> mappingsProp =
      new ArrayProperty<>("mappings", MappingRecord::new);

  public IdentitySetupRecord() {
    super(4);
    declareProperty(defaultRoleProp)
        .declareProperty(usersProp)
        .declareProperty(defaultTenantProp)
        .declareProperty(mappingsProp);
  }

  @Override
  public RoleRecord getDefaultRole() {
    return defaultRoleProp.getValue();
  }

  public IdentitySetupRecord setDefaultRole(final RoleRecord role) {
    defaultRoleProp.getValue().copyFrom(role);
    return this;
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
  public List<MappingRecordValue> getMappings() {
    return mappingsProp.stream().map(MappingRecordValue.class::cast).toList();
  }

  public IdentitySetupRecord setDefaultTenant(final TenantRecord tenant) {
    defaultTenantProp.getValue().copyFrom(tenant);
    return this;
  }

  public IdentitySetupRecord addUser(final UserRecord user) {
    usersProp.add().copyFrom(user);
    return this;
  }

  public IdentitySetupRecord addMapping(final MappingRecord mapping) {
    mappingsProp.add().copyFrom(mapping);
    return this;
  }
}
