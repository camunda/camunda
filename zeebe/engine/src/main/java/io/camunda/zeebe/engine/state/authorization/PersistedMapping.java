/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.authorization;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class PersistedMapping extends UnpackedObject implements DbValue {

  private final LongProperty mappingKeyProp = new LongProperty("mappingKey", -1L);
  private final StringProperty claimNameProp = new StringProperty("claimName", "");
  private final StringProperty claimValueProp = new StringProperty("claimValue", "");
  private final StringProperty nameProp = new StringProperty("name", "");
  private final StringProperty mappingRuleIdProp = new StringProperty("mappingRuleId", "");

  public PersistedMapping() {
    super(5);
    declareProperty(mappingKeyProp)
        .declareProperty(claimNameProp)
        .declareProperty(claimValueProp)
        .declareProperty(nameProp)
        .declareProperty(mappingRuleIdProp);
  }

  public PersistedMapping copy() {
    final var copy = new PersistedMapping();
    copy.copyFrom(this);
    return copy;
  }

  public long getMappingKey() {
    return mappingKeyProp.getValue();
  }

  public PersistedMapping setMappingKey(final long mappingKey) {
    mappingKeyProp.setValue(mappingKey);
    return this;
  }

  public String getClaimName() {
    return BufferUtil.bufferAsString(claimNameProp.getValue());
  }

  public PersistedMapping setClaimName(final String claimName) {
    claimNameProp.setValue(claimName);
    return this;
  }

  public String getClaimValue() {
    return BufferUtil.bufferAsString(claimValueProp.getValue());
  }

  public PersistedMapping setClaimValue(final String claimValue) {
    claimValueProp.setValue(claimValue);
    return this;
  }

  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public PersistedMapping setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  public String getMappingRuleId() {
    return BufferUtil.bufferAsString(mappingRuleIdProp.getValue());
  }

  public PersistedMapping setMappingRuleId(final String mappingRuleId) {
    mappingRuleIdProp.setValue(mappingRuleId);
    return this;
  }
}
