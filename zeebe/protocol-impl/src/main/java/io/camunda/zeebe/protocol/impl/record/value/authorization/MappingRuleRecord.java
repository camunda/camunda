/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.authorization;

import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRuleType;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class MappingRuleRecord extends UnifiedRecordValue implements MappingRuleRecordValue {

  // Static StringValue keys for property names
  private static final StringValue MAPPING_RULE_KEY_KEY = new StringValue("mappingRuleKey");
  private static final StringValue MAPPING_RULE_ID_KEY = new StringValue("mappingRuleId");
  private static final StringValue CLAIM_NAME_KEY = new StringValue("claimName");
  private static final StringValue CLAIM_VALUE_KEY = new StringValue("claimValue");
  private static final StringValue NAME_KEY = new StringValue("name");
  private static final StringValue TYPE_KEY = new StringValue("type");
  private static final StringValue ORIGIN_KEY = new StringValue("origin");

  private final LongProperty mappingRuleKeyProp = new LongProperty(MAPPING_RULE_KEY_KEY, -1L);
  private final StringProperty mappingRuleIdProp = new StringProperty(MAPPING_RULE_ID_KEY);
  private final StringProperty claimNameProp = new StringProperty(CLAIM_NAME_KEY, "");
  private final StringProperty claimValueProp = new StringProperty(CLAIM_VALUE_KEY, "");
  private final StringProperty nameProp = new StringProperty(NAME_KEY, "");
  private final StringProperty typeProp =
      new StringProperty(TYPE_KEY, MappingRuleType.TOKEN_CLAIM.name());
  private final StringProperty originProp = new StringProperty(ORIGIN_KEY, "");

  public MappingRuleRecord() {
    super(7);
    declareProperty(mappingRuleKeyProp)
        .declareProperty(mappingRuleIdProp)
        .declareProperty(claimNameProp)
        .declareProperty(claimValueProp)
        .declareProperty(nameProp)
        .declareProperty(typeProp)
        .declareProperty(originProp);
  }

  @Override
  public long getMappingRuleKey() {
    return mappingRuleKeyProp.getValue();
  }

  public MappingRuleRecord setMappingRuleKey(final long mappingRuleKey) {
    mappingRuleKeyProp.setValue(mappingRuleKey);
    return this;
  }

  @Override
  public String getClaimName() {
    return BufferUtil.bufferAsString(claimNameProp.getValue());
  }

  public MappingRuleRecord setClaimName(final String claimName) {
    claimNameProp.setValue(claimName);
    return this;
  }

  @Override
  public String getClaimValue() {
    return BufferUtil.bufferAsString(claimValueProp.getValue());
  }

  public MappingRuleRecord setClaimValue(final String claimValue) {
    claimValueProp.setValue(claimValue);
    return this;
  }

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public MappingRuleRecord setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  @Override
  public String getMappingRuleId() {
    return BufferUtil.bufferAsString(mappingRuleIdProp.getValue());
  }

  public MappingRuleRecord setMappingRuleId(final String mappingRuleId) {
    mappingRuleIdProp.setValue(mappingRuleId);
    return this;
  }

  /** A missing or empty type means {@link MappingRuleType#TOKEN_CLAIM}. */
  @Override
  public MappingRuleType getType() {
    final String type = BufferUtil.bufferAsString(typeProp.getValue());
    return type.isEmpty() ? MappingRuleType.TOKEN_CLAIM : MappingRuleType.valueOf(type);
  }

  public MappingRuleRecord setType(final MappingRuleType type) {
    typeProp.setValue(type == null ? MappingRuleType.TOKEN_CLAIM.name() : type.name());
    return this;
  }

  @Override
  public String getOrigin() {
    return BufferUtil.bufferAsString(originProp.getValue());
  }

  public MappingRuleRecord setOrigin(final String origin) {
    originProp.setValue(origin == null ? "" : origin);
    return this;
  }
}
