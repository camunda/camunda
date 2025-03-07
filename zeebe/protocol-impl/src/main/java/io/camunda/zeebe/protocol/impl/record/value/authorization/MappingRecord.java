/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.authorization;

import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.MappingRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class MappingRecord extends UnifiedRecordValue implements MappingRecordValue {

  private final StringProperty claimNameProp = new StringProperty("claimName", "");
  private final StringProperty claimValueProp = new StringProperty("claimValue", "");
  private final StringProperty nameProp = new StringProperty("name", "");
  private final StringProperty idProp = new StringProperty("id", "");

  public MappingRecord() {
    super(4);
    declareProperty(claimNameProp)
        .declareProperty(claimValueProp)
        .declareProperty(nameProp)
        .declareProperty(idProp);
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
  public String getId() {
    return BufferUtil.bufferAsString(idProp.getValue());
  }

  public MappingRecord setId(final String id) {
    idProp.setValue(id);
    return this;
  }
}
