/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.secretreference;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ResolutionState;
import io.camunda.zeebe.protocol.record.value.SecretReferenceRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class SecretReferenceRecord extends UnifiedRecordValue
    implements SecretReferenceRecordValue {

  private final StringProperty storeIdProp = new StringProperty("storeId", "");
  private final StringProperty secretReferenceProp = new StringProperty("secretReference", "");
  private final EnumProperty<ResolutionState> resolutionStateProp =
      new EnumProperty<>("resolutionState", ResolutionState.class, ResolutionState.UNSPECIFIED);
  private final ArrayProperty<LongValue> jobKeysProp =
      new ArrayProperty<>("jobKeys", LongValue::new);

  public SecretReferenceRecord() {
    super(4);
    declareProperty(storeIdProp)
        .declareProperty(secretReferenceProp)
        .declareProperty(resolutionStateProp)
        .declareProperty(jobKeysProp);
  }

  @Override
  public String getStoreId() {
    return BufferUtil.bufferAsString(storeIdProp.getValue());
  }

  public SecretReferenceRecord setStoreId(final String storeId) {
    storeIdProp.setValue(storeId);
    return this;
  }

  @Override
  public String getSecretReference() {
    return BufferUtil.bufferAsString(secretReferenceProp.getValue());
  }

  public SecretReferenceRecord setSecretReference(final String secretReference) {
    secretReferenceProp.setValue(secretReference);
    return this;
  }

  @Override
  public ResolutionState getResolutionState() {
    return resolutionStateProp.getValue();
  }

  public SecretReferenceRecord setResolutionState(final ResolutionState resolutionState) {
    resolutionStateProp.setValue(resolutionState);
    return this;
  }

  @Override
  public List<Long> getJobKeys() {
    return StreamSupport.stream(jobKeysProp.spliterator(), false)
        .map(LongValue::getValue)
        .collect(Collectors.toList());
  }

  public SecretReferenceRecord addJobKey(final long jobKey) {
    jobKeysProp.add().setValue(jobKey);
    return this;
  }
}
