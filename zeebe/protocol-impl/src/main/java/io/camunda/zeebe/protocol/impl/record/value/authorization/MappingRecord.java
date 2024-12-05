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
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class MappingRecord extends UnifiedRecordValue implements MappingRecordValue {

  private final LongProperty mappingKeyProp = new LongProperty("mappingKey", -1L);
  private final StringProperty claimNameProp = new StringProperty("claimName", "");
  private final StringProperty claimValueProp = new StringProperty("claimValue", "");

  public MappingRecord() {
    super(3);
    declareProperty(mappingKeyProp).declareProperty(claimNameProp).declareProperty(claimValueProp);
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
}
