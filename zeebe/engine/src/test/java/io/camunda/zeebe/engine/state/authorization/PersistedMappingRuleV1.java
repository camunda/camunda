/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.security.core.auth.MappingRuleMatcher;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class PersistedMappingRuleV1 extends UnpackedObject
    implements DbValue, MappingRuleMatcher.MappingRule {

  private final LongProperty mappingRuleKeyProp = new LongProperty("mappingRuleKey", -1L);
  private final StringProperty claimNameProp = new StringProperty("claimName", "");
  private final StringProperty claimValueProp = new StringProperty("claimValue", "");
  private final StringProperty nameProp = new StringProperty("name", "");
  private final StringProperty mappingRuleIdProp = new StringProperty("mappingRuleId", "");

  public PersistedMappingRuleV1() {
    super(5);
    declareProperty(mappingRuleKeyProp)
        .declareProperty(claimNameProp)
        .declareProperty(claimValueProp)
        .declareProperty(nameProp)
        .declareProperty(mappingRuleIdProp);
  }

  public PersistedMappingRuleV1 copy() {
    final var copy = new PersistedMappingRuleV1();
    copy.copyFrom(this);
    return copy;
  }

  public long getMappingRuleKey() {
    return mappingRuleKeyProp.getValue();
  }

  public PersistedMappingRuleV1 setMappingRuleKey(final long mappingRuleKey) {
    mappingRuleKeyProp.setValue(mappingRuleKey);
    return this;
  }

  public String getClaimName() {
    return BufferUtil.bufferAsString(claimNameProp.getValue());
  }

  public PersistedMappingRuleV1 setClaimName(final String claimName) {
    claimNameProp.setValue(claimName);
    return this;
  }

  public String getClaimValue() {
    return BufferUtil.bufferAsString(claimValueProp.getValue());
  }

  public PersistedMappingRuleV1 setClaimValue(final String claimValue) {
    claimValueProp.setValue(claimValue);
    return this;
  }

  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public PersistedMappingRuleV1 setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  public String getMappingRuleId() {
    return BufferUtil.bufferAsString(mappingRuleIdProp.getValue());
  }

  public PersistedMappingRuleV1 setMappingRuleId(final String mappingRuleId) {
    mappingRuleIdProp.setValue(mappingRuleId);
    return this;
  }

  @Override
  public String mappingRuleId() {
    return getMappingRuleId();
  }

  @Override
  public String claimName() {
    return getClaimName();
  }

  @Override
  public String claimValue() {
    return getClaimValue();
  }
}
