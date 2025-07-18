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
import io.camunda.zeebe.protocol.record.value.MappingRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class MappingRecord extends UnifiedRecordValue implements MappingRecordValue {

  // Static StringValue keys for property names
  private static final StringValue MAPPING_KEY_KEY = new StringValue("mappingKey");
  private static final StringValue MAPPING_ID_KEY = new StringValue("mappingId");
  private static final StringValue CLAIM_NAME_KEY = new StringValue("claimName");
  private static final StringValue CLAIM_VALUE_KEY = new StringValue("claimValue");
  private static final StringValue NAME_KEY = new StringValue("name");

  private final LongProperty mappingKeyProp = new LongProperty(MAPPING_KEY_KEY, -1L);
  private final StringProperty mappingIdProp = new StringProperty(MAPPING_ID_KEY);
  private final StringProperty claimNameProp = new StringProperty(CLAIM_NAME_KEY, "");
  private final StringProperty claimValueProp = new StringProperty(CLAIM_VALUE_KEY, "");
  private final StringProperty nameProp = new StringProperty(NAME_KEY, "");

  public MappingRecord() {
    super(5);
    declareProperty(mappingKeyProp)
        .declareProperty(mappingIdProp)
        .declareProperty(claimNameProp)
        .declareProperty(claimValueProp)
        .declareProperty(nameProp);
  }

  @Override
  public long getMappingKey() {
    return mappingKeyProp.getValue();
  }

  public MappingRecord setMappingKey(final long mappingKey) {
    mappingKeyProp.setValue(mappingKey);
    return this;
  }

  @Override
  public String getClaimName() {
    return BufferUtil.bufferAsString(claimNameProp.getValue());
  }

  public MappingRecord setClaimName(final String claimName) {
    claimNameProp.setValue(claimName);
    return this;
  }

  @Override
  public String getClaimValue() {
    return BufferUtil.bufferAsString(claimValueProp.getValue());
  }

  public MappingRecord setClaimValue(final String claimValue) {
    claimValueProp.setValue(claimValue);
    return this;
  }

  @Override
  public String getName() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public MappingRecord setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  @Override
  public String getMappingId() {
    return BufferUtil.bufferAsString(mappingIdProp.getValue());
  }

  public MappingRecord setMappingId(final String mappingId) {
    mappingIdProp.setValue(mappingId);
    return this;
  }
}
